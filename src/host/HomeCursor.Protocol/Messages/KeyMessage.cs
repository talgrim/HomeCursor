namespace HomeCursor.Protocol.Messages;

public sealed record KeyMessage(SpecialKey Key, bool IsDown) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.Key;

    internal static KeyMessage Read(BinaryReader reader) =>
        new(WireFormat.ReadEnum<SpecialKey>(reader), reader.ReadBoolean());

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write((byte)Key);
        writer.Write(IsDown);
    }
}
