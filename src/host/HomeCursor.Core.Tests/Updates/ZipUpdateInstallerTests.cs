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

    public void Dispose() => _dir.Dispose();

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
            NullLogger<ZipUpdateInstaller>.Instance);

    [Fact]
    public async Task InstallAsync_ReplacesExecutable_AndLeavesOtherFilesAlone()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        File.WriteAllText(_dir.File("settings.json"), "my settings");
        var zip = CreateZip(("HomeCursor.exe", "new exe"), ("settings.json", "default settings"), ("LICENSE", "text"));

        await Create(FakeHttpMessageHandler.Serving(zip)).InstallAsync(InfoFor(zip));

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

        await Create(FakeHttpMessageHandler.Serving(zip)).InstallAsync(InfoFor(zip, Convert.ToHexString(SHA256.HashData(zip))));

        Assert.Equal("new exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_ChecksumMismatch_LeavesExecutableUntouched()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("HomeCursor.exe", "new exe"));

        await Assert.ThrowsAsync<UpdateException>(() =>
            Create(FakeHttpMessageHandler.Serving(zip)).InstallAsync(InfoFor(zip, new string('0', 64))));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
        Assert.False(File.Exists(_dir.File("HomeCursor.exe.old")));
        Assert.False(File.Exists(_dir.File("HomeCursor.exe.new")));
    }

    [Fact]
    public async Task InstallAsync_ZipWithoutExecutable_Throws()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var zip = CreateZip(("readme.txt", "hello"));

        await Assert.ThrowsAsync<UpdateException>(() => Create(FakeHttpMessageHandler.Serving(zip)).InstallAsync(InfoFor(zip)));

        Assert.Equal("old exe", File.ReadAllText(_dir.File("HomeCursor.exe")));
    }

    [Fact]
    public async Task InstallAsync_NotAZip_Throws()
    {
        File.WriteAllText(_dir.File("HomeCursor.exe"), "old exe");
        var junk = "not a zip"u8.ToArray();

        await Assert.ThrowsAsync<UpdateException>(() => Create(FakeHttpMessageHandler.Serving(junk)).InstallAsync(InfoFor(junk)));

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
        var handler = FakeHttpMessageHandler.Serving(zip);

        await Assert.ThrowsAsync<UpdateException>(() =>
            Create(handler, _dir.File("dotnet.exe")).InstallAsync(InfoFor(zip)));

        Assert.Empty(handler.Requests);
    }
}
