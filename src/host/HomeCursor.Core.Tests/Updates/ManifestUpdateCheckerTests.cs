using System.Net;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Core.Updates;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Tests.Updates;

public sealed class ManifestUpdateCheckerTests
{
    private static string Manifest(string version) =>
        $$"""{"files":[{"product":"homecursor-host","version":"{{version}}","suffix":"-win-x64.zip","file":"homecursor-host-{{version}}-win-x64.zip","size":1,"sha256":"{{new string('b', 64)}}"}]}""";

    private static ManifestUpdateChecker Create(FakeHttpMessageHandler handler, Version current) =>
        new(
            new HttpClient(handler),
            Options.Create(new HostSettings { UpdateManifestUrl = "https://example.test/files/manifest.json" }),
            new UpdateEnvironment(current, "HomeCursor.exe", "state.json"),
            NullLogger<ManifestUpdateChecker>.Instance);

    [Fact]
    public async Task CheckAsync_NewerRelease_ReturnsIt()
    {
        var checker = Create(FakeHttpMessageHandler.Serving(Manifest("1.4.0")), new Version(1, 3, 0, 0));

        var update = await checker.CheckAsync();

        Assert.Equal(new Version(1, 4, 0), update!.Version);
    }

    [Fact]
    public async Task CheckAsync_SameVersion_ReturnsNull()
    {
        // The assembly version has four parts, the release version three.
        var checker = Create(FakeHttpMessageHandler.Serving(Manifest("1.3.0")), new Version(1, 3, 0, 0));

        Assert.Null(await checker.CheckAsync());
    }

    [Fact]
    public async Task CheckAsync_OlderRelease_ReturnsNull()
    {
        var checker = Create(FakeHttpMessageHandler.Serving(Manifest("1.2.0")), new Version(1, 3, 0, 0));

        Assert.Null(await checker.CheckAsync());
    }

    [Fact]
    public async Task CheckAsync_RequestsConfiguredManifest()
    {
        var handler = FakeHttpMessageHandler.Serving(Manifest("1.3.0"));

        await Create(handler, new Version(1, 3, 0)).CheckAsync();

        Assert.Equal([new Uri("https://example.test/files/manifest.json")], handler.Requests);
    }

    [Fact]
    public async Task CheckAsync_ServerError_ThrowsUpdateException()
    {
        var checker = Create(FakeHttpMessageHandler.Failing(HttpStatusCode.InternalServerError), new Version(1, 3, 0));

        await Assert.ThrowsAsync<UpdateException>(() => checker.CheckAsync());
    }

    [Fact]
    public async Task CheckAsync_GarbageBody_ThrowsUpdateException()
    {
        var checker = Create(FakeHttpMessageHandler.Serving("<html>"), new Version(1, 3, 0));

        await Assert.ThrowsAsync<UpdateException>(() => checker.CheckAsync());
    }
}
