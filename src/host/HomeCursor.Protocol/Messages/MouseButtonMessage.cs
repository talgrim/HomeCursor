namespace HomeCursor.Protocol.Messages;

public sealed record MouseButtonMessage(MouseButton Button, bool IsDown) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.MouseButton;

    internal static MouseButtonMessage Read(BinaryReader reader) =>
        new(WireFormat.ReadEnum<MouseButton>(reader), reader.ReadBoolean());

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write((byte)Button);
        writer.Write(IsDown);
    }
}
