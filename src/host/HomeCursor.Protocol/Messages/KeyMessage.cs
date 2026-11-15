namespace HomeCursor.Protocol.Messages;

public sealed record KeyMessage(SpecialKey Key, bool IsDown) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.Key;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write((byte)Key);
        writer.Write(IsDown);
    }
}
