using HomeCursor.Core.Input;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Core.Tests.Support;

internal sealed class RecordingDispatcher : IMessageDispatcher
{
    private readonly object _sync = new();
    private readonly List<ProtocolMessage> _messages = [];
    private int _releaseAllCount;

    public IReadOnlyList<ProtocolMessage> Messages
    {
        get
        {
            lock (_sync)
            {
                return [.. _messages];
            }
        }
    }

    public int ReleaseAllCount => Volatile.Read(ref _releaseAllCount);

    public void Dispatch(ProtocolMessage message)
    {
        lock (_sync)
        {
            _messages.Add(message);
        }
    }

    public void ReleaseAll() => Interlocked.Increment(ref _releaseAllCount);
}
