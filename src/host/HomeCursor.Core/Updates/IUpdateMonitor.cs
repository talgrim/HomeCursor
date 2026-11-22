namespace HomeCursor.Core.Updates;

/// <summary>Finds out in the background, and on request, whether a newer host release exists.</summary>
public interface IUpdateMonitor
{
    /// <summary>The newer release found by the latest check, or <c>null</c>.</summary>
    UpdateInfo? Available { get; }

    /// <summary>Raised from a background thread when a check finds a release that was not known before.</summary>
    event EventHandler<UpdateInfo>? UpdateFound;

    /// <summary>
    /// Starts the scheduled checks: one now, unless the last one is recent, then one per interval.
    /// Does nothing when the user switched update checks off.
    /// </summary>
    void Start();

    /// <summary>
    /// Brings the scheduled checks in line with the current <c>CheckForUpdates</c> setting: starts them when it was
    /// switched on, stops them when it was switched off. Does nothing when they already match.
    /// </summary>
    void Refresh();

    /// <summary>Checks right now, whatever the schedule or the setting say.</summary>
    /// <exception cref="UpdateException">The check failed.</exception>
    Task<UpdateInfo?> CheckNowAsync(CancellationToken cancellationToken = default);
}
