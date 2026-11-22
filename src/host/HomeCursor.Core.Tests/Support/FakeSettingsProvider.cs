using HomeCursor.Core.Settings;

namespace HomeCursor.Core.Tests.Support;

internal sealed class FakeSettingsProvider : ISettingsProvider
{
    public List<HostSettings> Saved { get; } = [];

    public SettingsException? SaveFailure { get; set; }

    public HostSettings Load() => Saved.Count > 0 ? Saved[^1] : new HostSettings();

    public void Save(HostSettings settings)
    {
        if (SaveFailure is not null)
        {
            throw SaveFailure;
        }

        Saved.Add(settings);
    }
}
