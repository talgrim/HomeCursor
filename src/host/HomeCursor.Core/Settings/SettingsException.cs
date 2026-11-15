namespace HomeCursor.Core.Settings;

/// <summary>
/// Raised when settings cannot be read or contain invalid values.
/// </summary>
public sealed class SettingsException : Exception
{
    public SettingsException(string message)
        : base(message)
    {
    }

    public SettingsException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
