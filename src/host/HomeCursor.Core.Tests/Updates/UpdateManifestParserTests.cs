using System.Text.Json;
using HomeCursor.Core.Updates;

namespace HomeCursor.Core.Tests.Updates;

public sealed class UpdateManifestParserTests
{
    private static readonly Uri ManifestUri = new("https://example.test/files/manifest.json");
    private static readonly string Hash = new('a', 64);

    private static string Entry(
        string product = "homecursor-host", string version = "1.3.0", string suffix = "-win-x64.zip",
        string? file = null, string? sha256 = null, long size = 100) =>
        $$"""{"product":"{{product}}","version":"{{version}}","suffix":"{{suffix}}","file":"{{file ?? $"{product}-{version}{suffix}"}}","size":{{size}},"sha256":"{{sha256 ?? Hash}}","latest":false}""";

    private static string Manifest(params string[] entries) => $$"""{"generated":"x","files":[{{string.Join(',', entries)}}]}""";

    private static UpdateInfo? Find(string json) =>
        UpdateManifestParser.FindLatest(json, ManifestUri, "homecursor-host", "-win-x64.zip");

    [Fact]
    public void FindLatest_ReturnsHighestVersionOfProduct()
    {
        var result = Find(Manifest(Entry(version: "1.2.0"), Entry(version: "1.10.0"), Entry(version: "1.3.0")));

        Assert.Equal(new Version(1, 10, 0), result!.Version);
    }

    [Fact]
    public void FindLatest_ResolvesDownloadNextToManifest()
    {
        var result = Find(Manifest(Entry()));

        Assert.Equal(new Uri("https://example.test/files/homecursor-host-1.3.0-win-x64.zip"), result!.DownloadUri);
        Assert.Equal(Hash, result.Sha256);
        Assert.Equal(100, result.Size);
    }

    [Fact]
    public void FindLatest_IgnoresOtherProductsAndSuffixes()
    {
        var result = Find(Manifest(
            Entry(product: "homecursor", suffix: ".apk", version: "9.0.0"),
            Entry(product: "other", version: "9.0.0"),
            Entry(version: "1.3.0")));

        Assert.Equal(new Version(1, 3, 0), result!.Version);
    }

    [Fact]
    public void FindLatest_NoMatchingEntry_ReturnsNull()
    {
        Assert.Null(Find(Manifest(Entry(product: "other"))));
        Assert.Null(Find(Manifest()));
    }

    [Theory]
    [InlineData("""{"files":[{"product":"homecursor-host","suffix":"-win-x64.zip"}]}""")]
    [InlineData("""{"files":"nope"}""")]
    [InlineData("""{}""")]
    [InlineData("""[]""")]
    public void FindLatest_UnexpectedShape_ReturnsNull(string json)
    {
        Assert.Null(Find(json));
    }

    [Fact]
    public void FindLatest_SkipsMalformedEntries()
    {
        var result = Find(Manifest(
            Entry(version: "not-a-version"),
            Entry(version: "2.0.0", sha256: "short"),
            Entry(version: "3.0.0", file: "../evil.zip"),
            Entry(version: "4.0.0", file: "https:evil.zip"),
            Entry(version: "1.3.0")));

        Assert.Equal(new Version(1, 3, 0), result!.Version);
    }

    [Fact]
    public void FindLatest_InvalidJson_Throws()
    {
        Assert.ThrowsAny<JsonException>(() => Find("<html>"));
    }
}
