using HomeCursor.Core.Diagnostics;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Tests.Diagnostics;

public sealed class RollingFileLoggerProviderTests : IDisposable
{
    private readonly string _directory = Path.Combine(Path.GetTempPath(), "homecursor-tests-" + Guid.NewGuid().ToString("N"));

    public void Dispose()
    {
        if (Directory.Exists(_directory))
        {
            Directory.Delete(_directory, recursive: true);
        }
    }

    private string LogFile(string name = "homecursor.log") => Path.Combine(_directory, name);

    [Fact]
    public void Log_WritesTimestampLevelCategoryAndMessage()
    {
        using (var provider = new RollingFileLoggerProvider(_directory))
        {
            provider.CreateLogger("Some.Category").LogWarning("hello {Name}", "world");
        }

        var line = Assert.Single(File.ReadAllLines(LogFile()));
        Assert.Matches(@"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \[WRN\] Some\.Category: hello world$", line);
    }

    [Fact]
    public void Log_WithException_AppendsTheException()
    {
        using (var provider = new RollingFileLoggerProvider(_directory))
        {
            provider.CreateLogger("c").LogError(new InvalidOperationException("boom"), "failed");
        }

        var text = File.ReadAllText(LogFile());
        Assert.Contains("failed", text);
        Assert.Contains("InvalidOperationException: boom", text);
    }

    [Fact]
    public void Log_AppendsToAnExistingFile()
    {
        Directory.CreateDirectory(_directory);
        File.WriteAllText(LogFile(), "earlier" + Environment.NewLine);

        using (var provider = new RollingFileLoggerProvider(_directory))
        {
            provider.CreateLogger("c").LogInformation("later");
        }

        var lines = File.ReadAllLines(LogFile());
        Assert.Equal(2, lines.Length);
        Assert.Equal("earlier", lines[0]);
    }

    [Fact]
    public void Log_WhenTheFileIsFull_RollsOverAndKeepsOnlyTheNewestFiles()
    {
        using (var provider = new RollingFileLoggerProvider(_directory, maxFileBytes: 200, maxFiles: 3))
        {
            var logger = provider.CreateLogger("c");
            for (var i = 0; i < 20; i++)
            {
                logger.LogInformation("line {Index} {Padding}", i, new string('x', 60));
            }
        }

        Assert.True(File.Exists(LogFile()));
        Assert.True(File.Exists(LogFile("homecursor.1.log")));
        Assert.True(File.Exists(LogFile("homecursor.2.log")));
        Assert.False(File.Exists(LogFile("homecursor.3.log")));
        Assert.All(
            Directory.GetFiles(_directory),
            file => Assert.True(new FileInfo(file).Length <= 400, "a file grew far beyond the limit"));
        Assert.Contains("line 19", File.ReadAllText(LogFile()));
        Assert.DoesNotContain("line 0 ", File.ReadAllText(LogFile("homecursor.2.log")));
    }

    [Fact]
    public void Log_WhenTheDirectoryCannotBeCreated_DoesNotThrow()
    {
        var blocker = Path.Combine(Path.GetTempPath(), "homecursor-tests-blocker-" + Guid.NewGuid().ToString("N"));
        File.WriteAllText(blocker, "a file where a directory is needed");
        try
        {
            using var provider = new RollingFileLoggerProvider(Path.Combine(blocker, "logs"));

            var exception = Record.Exception(() => provider.CreateLogger("c").LogInformation("dropped"));

            Assert.Null(exception);
        }
        finally
        {
            File.Delete(blocker);
        }
    }
}
