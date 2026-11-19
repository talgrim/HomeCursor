namespace HomeCursor.Protocol.Messages;

/// <summary>
/// Base type of every message exchanged between client and host. The hierarchy is closed to this assembly:
/// each message declares its <see cref="MessageType"/> and writes its own fields, and
/// <see cref="MessageCodec"/> holds the matching reader.
/// </summary>
public abstract record ProtocolMessage
{
    private protected abstract MessageType MessageType { get; }

    internal void Write(BinaryWriter writer)
    {
        writer.Write((byte)MessageType);
        WriteCore(writer);
    }

    private protected virtual void WriteCore(BinaryWriter writer)
    {
    }
}
