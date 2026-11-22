using System.Text.Encodings.Web;
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
        // A host name like "Büro" should stay readable in the file; this is not HTML.
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
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

    public void Save(HostSettings settings)
    {
        try
        {
            settings.Validate();
        }
        catch (SettingsException ex)
        {
            throw new SettingsException($"Settings cannot be saved: {ex.Message}", ex);
        }

        try
        {
            var json = File.Exists(_path)
                ? JsonObjectPatcher.Patch(File.ReadAllText(_path), ToJsonValues(settings), ToJsonValues(new HostSettings()))
                : JsonSerializer.Serialize(settings, Options);

            WriteAtomically(json);
        }
        catch (JsonException ex)
        {
            throw new SettingsException($"Settings file '{_path}' is not valid, not overwriting it: {ex.Message}", ex);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            throw new SettingsException($"Settings file '{_path}' cannot be written: {ex.Message}", ex);
        }

        _logger.LogInformation("Saved settings to {Path}", _path);
    }

    private static Dictionary<string, string> ToJsonValues(HostSettings settings)
    {
        var values = new Dictionary<string, string>();
        foreach (var property in JsonSerializer.SerializeToElement(settings, Options).EnumerateObject())
        {
            values[property.Name] = property.Value.GetRawText();
        }

        return values;
    }

    /// <summary>Writes a temporary file next to the settings and moves it over them, so a failure cannot leave half a file.</summary>
    private void WriteAtomically(string json)
    {
        var directory = Path.GetDirectoryName(_path);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }

        var temporary = _path + ".tmp";
        try
        {
            File.WriteAllText(temporary, json);
            File.Move(temporary, _path, overwrite: true);
        }
        finally
        {
            File.Delete(temporary);
        }
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
