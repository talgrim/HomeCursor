using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Settings;

/// <summary>
/// The settings the host is running with right now. Components read <see cref="IOptions{HostSettings}.Value"/>
/// each time they need a value instead of keeping a copy, so a change applies without rebuilding them.
/// </summary>
public interface ISettingsHolder : IOptions<HostSettings>
{
    /// <summary>Raised, on the thread that called <see cref="Update"/>, after the current settings were replaced.</summary>
    event EventHandler<HostSettings>? Changed;

    /// <summary>Replaces the current settings. The caller validates them first.</summary>
    void Update(HostSettings settings);
}
