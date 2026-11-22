namespace HomeCursor.Core.Tests.Support;

internal sealed class FakeHostService : IHostService
{
    public List<string> Journal { get; } = [];

    /// <summary>Failures for the next starts, one per call; a start with none left succeeds.</summary>
    public Queue<Exception> StartFailures { get; } = new();

    public HostStatus Status { get; set; } = HostStatus.Running;

    public event EventHandler<HostStatus>? StatusChanged
    {
        add { }
        remove { }
    }

    public Task StartAsync(CancellationToken cancellationToken = default)
    {
        Journal.Add("start");
        if (StartFailures.TryDequeue(out var failure))
        {
            Status = HostStatus.Faulted;
            return Task.FromException(failure);
        }

        Status = HostStatus.Running;
        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken = default)
    {
        Journal.Add("stop");
        Status = HostStatus.Stopped;
        return Task.CompletedTask;
    }
}
