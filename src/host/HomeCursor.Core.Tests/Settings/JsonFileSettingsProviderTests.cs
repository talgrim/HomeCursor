using HomeCursor.Core.Settings;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Settings;

public sealed class JsonFileSettingsProviderTests : IDisposable
{
    private readonly string _directory = Path.Combine(Path.GetTempPath(), "HomeCursorTests-" + Guid.NewGuid());
    private readonly string _path;

    public JsonFileSettingsProviderTests()
    {
        _path = Path.Combine(_directory, "settings.json");
    }

    public void Dispose()
    {
        if (Directory.Exists(_directory))
        {
            Directory.Delete(_directory, recursive: true);
        }
    }

    private JsonFileSettingsProvider CreateProvider() => new(_path, NullLogger<JsonFileSettingsProvider>.Instance);

    private void WriteFile(string json)
    {
        Directory.CreateDirectory(_directory);
        File.WriteAllText(_path, json);
    }

    [Fact]
    public void Load_MissingFile_ReturnsDefaultsAndCreatesFile()
    {
        var settings = CreateProvider().Load();

        Assert.Equal(new HostSettings(), settings);
        Assert.True(File.Exists(_path));
    }

    [Fact]
    public void Load_CreatedDefaultFile_LoadsBackAsDefaults()
    {
        CreateProvider().Load();

        var settings = CreateProvider().Load();

        Assert.Equal(new HostSettings(), settings);
    }

    [Fact]
    public void Load_PartialFile_OverridesOnlyGivenValues()
    {
        WriteFile("""{ "tcpPort": 5555, "HostName": "Living room" }""");

        var settings = CreateProvider().Load();

        Assert.Equal(5555, settings.TcpPort);
        Assert.Equal("Living room", settings.HostName);
        Assert.Equal(new HostSettings().UdpPort, settings.UdpPort);
    }

    [Fact]
    public void Load_FileWithCommentsAndTrailingCommas_IsAccepted()
    {
        WriteFile("""
            {
                // custom port
                "TcpPort": 5555,
            }
            """);

        Assert.Equal(5555, CreateProvider().Load().TcpPort);
    }

    [Fact]
    public void Load_MalformedJson_ThrowsSettingsExceptionNamingTheFile()
    {
        WriteFile("{ not json");

        var exception = Assert.Throws<SettingsException>(() => CreateProvider().Load());

        Assert.Contains(_path, exception.Message);
    }

    [Fact]
    public void Load_InvalidValues_ThrowsSettingsExceptionNamingTheFile()
    {
        WriteFile("""{ "TcpPort": 0 }""");

        var exception = Assert.Throws<SettingsException>(() => CreateProvider().Load());

        Assert.Contains(_path, exception.Message);
        Assert.Contains("TcpPort", exception.Message);
    }

    [Fact]
    public void Load_WrongValueType_ThrowsSettingsException()
    {
        WriteFile("""{ "TcpPort": "abc" }""");

        Assert.Throws<SettingsException>(() => CreateProvider().Load());
    }
}
