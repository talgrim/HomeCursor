namespace HomeCursor.Core.Settings;

public interface ISettingsProvider
{
    /// <exception cref="SettingsException">Settings cannot be read or are invalid.</exception>
    HostSettings Load();

    /// <summary>
    /// Writes the settings back. Only values that changed are touched, so comments and keys the host does not know about stay.
    /// </summary>
    /// <exception cref="SettingsException">The settings are invalid or cannot be written.</exception>
    void Save(HostSettings settings);
}
