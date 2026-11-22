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

    [Fact]
    public void Save_ChangedValue_KeepsCommentsAndUnknownKeys()
    {
        WriteFile("""
            {
              // where the phone connects
              "TcpPort": 47800, /* default */
              "Extra": { "list": [1, 2, "x"] },
              "HostName": "Old"
            }
            """);

        CreateProvider().Save(new HostSettings { TcpPort = 5000, HostName = "New" });

        Assert.Equal("""
            {
              // where the phone connects
              "TcpPort": 5000, /* default */
              "Extra": { "list": [1, 2, "x"] },
              "HostName": "New"
            }
            """, File.ReadAllText(_path));
    }

    [Fact]
    public void Save_NothingChanged_LeavesFileUntouched()
    {
        const string Original = "{ // note\n  \"TcpPort\": 47800,\n}";
        WriteFile(Original);

        CreateProvider().Save(new HostSettings());

        Assert.Equal(Original, File.ReadAllText(_path));
    }

    [Fact]
    public void Save_KeyDiffersInCase_UpdatesIt()
    {
        WriteFile("""{ "tcpport": 1000 }""");

        CreateProvider().Save(new HostSettings { TcpPort = 2000 });

        Assert.Equal("""{ "tcpport": 2000 }""", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_MissingKeyWithNonDefaultValue_AddsIt()
    {
        WriteFile("{\n  \"TcpPort\": 47800\n}");

        CreateProvider().Save(new HostSettings { NotifyOnClientConnection = false });

        Assert.Equal("{\n  \"TcpPort\": 47800,\n  \"NotifyOnClientConnection\": false\n}", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_MissingKeyWithDefaultValue_DoesNotAddIt()
    {
        WriteFile("""{ "TcpPort": 47800 }""");

        CreateProvider().Save(new HostSettings { HostName = Environment.MachineName });

        Assert.Equal("""{ "TcpPort": 47800 }""", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_EmptyObject_AddsKeysAndKeepsTheBraceOnItsOwnLine()
    {
        WriteFile("{\n}");

        CreateProvider().Save(new HostSettings { TcpPort = 5000, DiagnosticsEnabled = true });

        Assert.Equal("{\n  \"TcpPort\": 5000,\n  \"DiagnosticsEnabled\": true\n}", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_EmptyObjectOnOneLine_PutsTheBraceOnItsOwnLine()
    {
        WriteFile("{}");

        CreateProvider().Save(new HostSettings { TcpPort = 5000 });

        Assert.Equal("{\n  \"TcpPort\": 5000\n}", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_FileWithTrailingCommaAndCrLf_StaysValidAndKeepsLineEndings()
    {
        WriteFile("{\r\n  \"TcpPort\": 47800,\r\n}");

        CreateProvider().Save(new HostSettings { UdpPort = 6000 });

        var text = File.ReadAllText(_path);
        Assert.Equal("{\r\n  \"TcpPort\": 47800,\r\n  \"UdpPort\": 6000,\r\n}", text);
        Assert.Equal(6000, CreateProvider().Load().UdpPort);
    }

    [Fact]
    public void Save_NonAsciiHostName_StaysReadableInTheFile()
    {
        WriteFile("""{ "HostName": "Old" }""");

        CreateProvider().Save(new HostSettings { HostName = "Büro" });

        Assert.Equal("""{ "HostName": "Büro" }""", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_ThenLoad_ReturnsTheSavedSettings()
    {
        WriteFile("""
            { "BindAddress": "127.0.0.1", "PingIntervalSeconds": 2, "HeartbeatTimeoutSeconds": 9 // kept
            }
            """);
        var settings = CreateProvider().Load() with
        {
            HostName = "Study",
            TcpPort = 5000,
            NotifyOnClientDisconnection = true,
            CheckForUpdates = false,
            DiagnosticsEnabled = true,
            DiagnosticsLogDirectory = @"C:\Logs"
        };

        CreateProvider().Save(settings);

        Assert.Equal(settings, CreateProvider().Load());
    }

    [Fact]
    public void Save_MissingFile_WritesAllSettings()
    {
        var settings = new HostSettings { HostName = "Study", TcpPort = 5000 };

        CreateProvider().Save(settings);

        Assert.Equal(settings, CreateProvider().Load());
    }

    [Fact]
    public void Save_InvalidSettings_ThrowsAndLeavesFileUntouched()
    {
        WriteFile("""{ "TcpPort": 47800 }""");

        var ex = Assert.Throws<SettingsException>(() => CreateProvider().Save(new HostSettings { TcpPort = 0 }));

        Assert.Contains(nameof(HostSettings.TcpPort), ex.Message);
        Assert.Equal("""{ "TcpPort": 47800 }""", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_MalformedFile_ThrowsAndDoesNotOverwriteIt()
    {
        WriteFile("{ \"TcpPort\": ");

        var ex = Assert.Throws<SettingsException>(() => CreateProvider().Save(new HostSettings { TcpPort = 5000 }));

        Assert.Contains(_path, ex.Message);
        Assert.Equal("{ \"TcpPort\": ", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_FileIsNotAnObject_ThrowsAndDoesNotOverwriteIt()
    {
        WriteFile("[1, 2]");

        Assert.Throws<SettingsException>(() => CreateProvider().Save(new HostSettings { TcpPort = 5000 }));

        Assert.Equal("[1, 2]", File.ReadAllText(_path));
    }

    [Fact]
    public void Save_LeavesNoTemporaryFileBehind()
    {
        WriteFile("{}");

        CreateProvider().Save(new HostSettings { TcpPort = 5000 });

        Assert.Equal([_path], Directory.GetFiles(_directory));
    }
}
