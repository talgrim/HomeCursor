namespace HomeCursor.Core.Settings;

/// <summary>What happened when new settings were applied.</summary>
/// <param name="AppRestartRequired">Some changed settings only take effect after the application restarts.</param>
/// <param name="SaveError">
/// Why the settings could not be written to the file, or <c>null</c> when they were. The settings are in effect either way,
/// but a failed save means they are lost at the next start.
/// </param>
public sealed record SettingsApplyResult(bool AppRestartRequired, string? SaveError = null)
{
    public bool Saved => SaveError is null;
}
