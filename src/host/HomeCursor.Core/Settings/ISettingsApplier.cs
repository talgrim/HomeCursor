namespace HomeCursor.Core.Settings;

/// <summary>Puts new settings into effect while the host runs, and saves them.</summary>
public interface ISettingsApplier
{
    /// <summary>
    /// Validates the settings, makes them current (restarting the host when its addresses changed) and saves them.
    /// </summary>
    /// <exception cref="SettingsException">
    /// The settings are invalid, or the host could not run with them. In both cases nothing changed: the previous
    /// settings are in effect again and the file was not touched.
    /// </exception>
    Task<SettingsApplyResult> ApplyAsync(HostSettings settings, CancellationToken cancellationToken = default);
}
