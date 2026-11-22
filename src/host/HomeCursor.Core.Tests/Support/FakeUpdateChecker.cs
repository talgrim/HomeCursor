using HomeCursor.Core.Updates;

namespace HomeCursor.Core.Tests.Support;

internal sealed class FakeUpdateChecker : IUpdateChecker
{
    public UpdateInfo? Result { get; set; }

    public Exception? Failure { get; set; }

    public int Calls { get; private set; }

    public Task<UpdateInfo?> CheckAsync(CancellationToken cancellationToken = default)
    {
        Calls++;
        return Failure is null ? Task.FromResult(Result) : Task.FromException<UpdateInfo?>(Failure);
    }
}
