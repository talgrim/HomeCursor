namespace HomeCursor.Core;

/// <summary>
/// Placeholder implementation. Networking and input injection will be added here
/// behind their own abstractions.
/// </summary>
public sealed class HostService : IHostService
{
    public HostStatus Status { get; private set; } = HostStatus.Stopped;

    public event EventHandler<HostStatus>? StatusChanged;

    public Task StartAsync(CancellationToken cancellationToken = default)
    {
        SetStatus(HostStatus.Running);
        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken = default)
    {
        SetStatus(HostStatus.Stopped);
        return Task.CompletedTask;
    }

    private void SetStatus(HostStatus status)
    {
        if (Status == status)
        {
            return;
        }

        Status = status;
        StatusChanged?.Invoke(this, status);
    }
}
