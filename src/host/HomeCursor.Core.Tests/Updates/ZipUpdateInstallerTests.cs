using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Core.Updates;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Updates;

public sealed class ZipUpdateInstallerTests : IDisposable
{
    private readonly TempDirectory _dir = new();
    private readonly ECDsa _releaseKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);

    public void Dispose()
    {
        _releaseKey.Dispose();
        _dir.Dispose();
    }

    private IUpdateSignatureVerifier ReleaseVerifier =>
        new EcdsaUpdateSignatureVerifier(Convert.ToBase64String(_releaseKey.ExportSubjectPublicKeyInfo()));

    private static string Sign(ECDsa key, Version version, byte[] zip) =>
        Convert.ToBase64String(key.SignData(
            UpdateSignature.GetPayload(version, Convert.ToHexString(SHA256.HashData(zip))),
            HashAlgorithmName.SHA256,
            DSASignatureFormat.Rfc3279DerSequence));

    /// <summary>Serves the zip and, next to it, a signature (the release's own unless another is given).</summary>
    private FakeHttpMessageHandler Serve(byte[] zip, string? signature = null, HttpStatusCode signatureStatus = HttpStatusCode.OK) =>
        new(request =>
        {
            if (request.RequestUri!.AbsolutePath.EndsWith(".sig", StringComparison.Ordinal))
            {
                return new HttpResponseMessage(signatureStatus)
                {
                    Content = new StringContent(signature ?? Sign(_releaseKey, new Version(1, 4, 0), zip))
                };
            }

            return new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(zip) };
        });

    private static byte[] CreateZip(params (string Name, string Content)[] entries)
    {
        using var stream = new MemoryStream();
        using (var archive = new ZipArchive(stream, ZipArchiveMode.Create, leaveOpen: true))
        {
            foreach (var (name, content) in entries)
            {
                using var writer = new StreamWriter(archive.CreateEntry(name).Open());
                writer.Write(content);
            }
        }

        return stream.ToArray();
    }

    private static UpdateInfo InfoFor(byte[] zip, string? sha256 = null) =>
        new(new Version(1, 4, 0), new Uri("https://example.test/files/update.zip"), sha256 ?? Convert.ToHexString(SHA256.HashData(zip)).ToLowerInvariant(), zip.Length);

    private ZipUpdateInstaller Create(FakeHttpMessageHandler handler, string? executablePath = null) =>
        new(
            new HttpClient(handler),
            new UpdateEnvironment(new Version(1, 3, 0), executablePath ?? _dir.File("HomeCursor.exe"), _dir.File("state.json")),
            ReleaseVerifier,
            NullLogger<ZipUpdateInstaller>.Instance);

    [Fact]
    public async Task InstallAsync_ReplacesExecutable_AndLeavesOtherFilesAlone()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        File.WriteAllText(_dir.File("settings.json"), "my settings");
        var zip = CreateZip(("HomeCursor.exe", "new exe"), ("settings.json", "default settings"), ("LICENSE", "text"));

        await Create(Serve(zip)).InstallAsync(InfoFor(zip));

        Assert.Equal("new exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe.old")));
        Assert.Equal("my settings", File.ReadAllText(_dir.File("settings.json")));
        Assert.False(File.Exists(_dir.File("HomeCursor.exe.new")));
        Assert.False(File.Exists(_dir.File("LICENSE")));
    }

    [Fact]
    public async Task InstallAsync_AcceptsUppercaseChecksum()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));

        await Create(Serve(zip)).InstallAsync(InfoFor(zip, Convert.ToHexString(SHA256.HashData(zip))));

        Assert.Equal("new exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_ChecksumMismatch_LeavesExecutableUntouched()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));

        await Assert.ThrowsAsync<UpdateException>(() =>
            Create(Serve(zip)).InstallAsync(InfoFor(zip, new string('0', 64))));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
        Assert.False(File.Exists(_dir.File("HomeCursor.exe.old")));
        Assert.False(File.Exists(_dir.File("HomeCursor.exe.new")));
    }

    [Fact]
    public async Task InstallAsync_ZipWithoutExecutable_Throws()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("readme.txt", "hello"));

        await Assert.ThrowsAsync<UpdateException>(() => Create(Serve(zip)).InstallAsync(InfoFor(zip)));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_NotAZip_Throws()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var junk = "not a zip"u8.ToArray();

        await Assert.ThrowsAsync<UpdateException>(() => Create(Serve(junk)).InstallAsync(InfoFor(junk)));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_DownloadFails_Throws()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));

        await Assert.ThrowsAsync<UpdateException>(() =>
            Create(FakeHttpMessageHandler.Failing(HttpStatusCode.NotFound)).InstallAsync(InfoFor(zip)));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_RunThroughDotnetHost_Throws()
    {
        var zip = CreateZip(("HomeCursor.exe", "new exe"));
        var handler = Serve(zip);

        await Assert.ThrowsAsync<UpdateException>(() =>
            Create(handler, _dir.File("dotnet.exe")).InstallAsync(InfoFor(zip)));

        Assert.Empty(handler.Requests);
    }

    [Fact]
    public async Task InstallAsync_SignatureMissing_LeavesExecutableUntouched()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));

        var ex = await Assert.ThrowsAsync<UpdateException>(() =>
            Create(Serve(zip, signatureStatus: HttpStatusCode.NotFound)).InstallAsync(InfoFor(zip)));

        Assert.Contains("not signed", ex.Message);
        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
        Assert.False(File.Exists(_dir.File("HomeCursor.exe.old")));
    }

    [Fact]
    public async Task InstallAsync_SignedWithAnotherKey_IsRejected()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));
        using var attacker = ECDsa.Create(ECCurve.NamedCurves.nistP256);

        await Assert.ThrowsAsync<UpdateException>(() =>
            Create(Serve(zip, Sign(attacker, new Version(1, 4, 0), zip))).InstallAsync(InfoFor(zip)));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_SignatureOfAnOlderVersion_IsRejected()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));

        // A genuine signature for this very zip, but for version 1.2.0: replaying an old release as a new one.
        await Assert.ThrowsAsync<UpdateException>(() =>
            Create(Serve(zip, Sign(_releaseKey, new Version(1, 2, 0), zip))).InstallAsync(InfoFor(zip)));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Theory]
    [InlineData("not base64!")]
    [InlineData("")]
    [InlineData("AAAA")]
    public async Task InstallAsync_MalformedSignature_IsRejected(string signature)
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));

        await Assert.ThrowsAsync<UpdateException>(() => Create(Serve(zip, signature)).InstallAsync(InfoFor(zip)));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_AsksForTheSignatureNextToTheZip()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));
        var handler = Serve(zip);

        await Create(handler).InstallAsync(InfoFor(zip));

        Assert.Equal(
            [new Uri("https://example.test/files/update.zip"), new Uri("https://example.test/files/update.zip.sig")],
            handler.Requests);
    }
}
