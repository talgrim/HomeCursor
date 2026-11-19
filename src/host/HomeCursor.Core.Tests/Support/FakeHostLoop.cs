namespace HomeCursor.Core.Tests.Support;

internal sealed class FakeHostLoop : IHostLoop
{
    private readonly List<string> _journal;

    public FakeHostLoop(string name, List<string> journal)
    {
        Name = name;
        _journal = journal;
    }

    public string Name { get; }

    public Exception? StartException { get; set; }

    public Exception? StopException { get; set; }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        _journal.Add($"start {Name}");
        return StartException is null ? Task.CompletedTask : Task.FromException(StartException);
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        _journal.Add($"stop {Name}");
        return StopException is null ? Task.CompletedTask : Task.FromException(StopException);
    }
}
