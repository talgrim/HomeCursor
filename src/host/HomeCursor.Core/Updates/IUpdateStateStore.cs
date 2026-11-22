namespace HomeCursor.Core.Updates;

/// <summary>Remembers when the host last asked the release server, so a restart does not check again at once.</summary>
public interface IUpdateStateStore
{
    DateTimeOffset? LastCheck { get; }

    void SaveLastCheck(DateTimeOffset time);
}
