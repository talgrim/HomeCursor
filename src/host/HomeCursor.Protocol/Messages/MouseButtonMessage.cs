namespace HomeCursor.Protocol.Messages;

public sealed record MouseButtonMessage(MouseButton Button, bool IsDown) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.MouseButton;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write((byte)Button);
        writer.Write(IsDown);
    }
}
