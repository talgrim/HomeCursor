using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Updates;

/// <summary>Keeps the last check time in a small JSON file. A missing or unreadable file means "never checked".</summary>
public sealed class JsonFileUpdateStateStore : IUpdateStateStore
{
    private readonly string _path;
    private readonly ILogger<JsonFileUpdateStateStore> _logger;

    public JsonFileUpdateStateStore(UpdateEnvironment environment, ILogger<JsonFileUpdateStateStore> logger)
        : this(environment.StateFilePath, logger)
    {
    }

    public JsonFileUpdateStateStore(string path, ILogger<JsonFileUpdateStateStore> logger)
    {
        _path = path;
        _logger = logger;
    }

    public DateTimeOffset? LastCheck
    {
        get
        {
            try
            {
                return File.Exists(_path)
                    ? JsonSerializer.Deserialize<State>(File.ReadAllText(_path))?.LastCheck
                    : null;
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException)
            {
                _logger.LogDebug(ex, "Could not read the update state file {Path}", _path);
                return null;
            }
        }
    }

    public void SaveLastCheck(DateTimeOffset time)
    {
        try
        {
            var directory = Path.GetDirectoryName(_path);
            if (!string.IsNullOrEmpty(directory))
            {
                Directory.CreateDirectory(directory);
            }

            File.WriteAllText(_path, JsonSerializer.Serialize(new State(time)));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _logger.LogDebug(ex, "Could not write the update state file {Path}", _path);
        }
    }

    private sealed record State(DateTimeOffset? LastCheck);
}
