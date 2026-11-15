namespace HomeCursor.Core.Settings;

public interface ISettingsProvider
{
    /// <exception cref="SettingsException">Settings cannot be read or are invalid.</exception>
    HostSettings Load();
}
