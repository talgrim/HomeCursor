namespace HomeCursor.Core;

/// <summary>
/// Lifecycle of the host that accepts client connections and injects input.
/// UI shells (tray, console, tests) depend on this abstraction only.
/// </summary>
public interface IHostService
{
    HostStatus Status { get; }

    event EventHandler<HostStatus>? StatusChanged;

    Task StartAsync(CancellationToken cancellationToken = default);

    Task StopAsync(CancellationToken cancellationToken = default);
}
