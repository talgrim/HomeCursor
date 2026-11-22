namespace HomeCursor.Core.Settings;

public sealed class SettingsHolder : ISettingsHolder
{
    private volatile HostSettings _current;

    public SettingsHolder(HostSettings initial)
    {
        _current = initial;
    }

    public HostSettings Value => _current;

    public event EventHandler<HostSettings>? Changed;

    public void Update(HostSettings settings)
    {
        ArgumentNullException.ThrowIfNull(settings);

        _current = settings;
        Changed?.Invoke(this, settings);
    }
}
