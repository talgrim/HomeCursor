using HomeCursor.Core.Updates;

namespace HomeCursor.Core.Tests.Support;

internal sealed class InMemoryUpdateStateStore : IUpdateStateStore
{
    public DateTimeOffset? LastCheck { get; set; }

    public void SaveLastCheck(DateTimeOffset time) => LastCheck = time;
}
