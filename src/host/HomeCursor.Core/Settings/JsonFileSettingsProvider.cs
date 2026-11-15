using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Settings;

/// <summary>
/// Reads <see cref="HostSettings"/> from a JSON file. A missing file is created with the default values
/// so users can discover and edit it.
/// </summary>
public sealed class JsonFileSettingsProvider : ISettingsProvider
{
    private static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true,
        ReadCommentHandling = JsonCommentHandling.Skip,
        AllowTrailingCommas = true
    };

    private readonly string _path;
    private readonly ILogger<JsonFileSettingsProvider> _logger;

    public JsonFileSettingsProvider(string path, ILogger<JsonFileSettingsProvider> logger)
    {
        _path = path;
        _logger = logger;
    }

    public HostSettings Load()
    {
        if (!File.Exists(_path))
        {
            return CreateDefaultFile();
        }

        HostSettings settings;
        try
        {
            var json = File.ReadAllText(_path);
            settings = JsonSerializer.Deserialize<HostSettings>(json, Options) ?? new HostSettings();
        }
        catch (JsonException ex)
        {
            throw new SettingsException($"Settings file '{_path}' is not valid: {ex.Message}", ex);
        }
        catch (IOException ex)
        {
            throw new SettingsException($"Settings file '{_path}' cannot be read: {ex.Message}", ex);
        }
        catch (UnauthorizedAccessException ex)
        {
            throw new SettingsException($"Settings file '{_path}' cannot be read: {ex.Message}", ex);
        }

        try
        {
            settings.Validate();
        }
        catch (SettingsException ex)
        {
            throw new SettingsException($"Settings file '{_path}' is not valid: {ex.Message}", ex);
        }

        _logger.LogInformation("Loaded settings from {Path}", _path);
        return settings;
    }

    private HostSettings CreateDefaultFile()
    {
        var defaults = new HostSettings();
        try
        {
            var directory = Path.GetDirectoryName(_path);
            if (!string.IsNullOrEmpty(directory))
            {
                Directory.CreateDirectory(directory);
            }

            File.WriteAllText(_path, JsonSerializer.Serialize(defaults, Options));
            _logger.LogInformation("Created default settings file {Path}", _path);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _logger.LogWarning(ex, "Could not create default settings file {Path}", _path);
        }

        return defaults;
    }
}
