namespace HomeCursor.Protocol.Messages;

/// <summary>Relative pointer movement in pixels. Sent over UDP (or TCP).</summary>
public sealed record MouseMoveMessage(short Dx, short Dy) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.MouseMove;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(Dx);
        writer.Write(Dy);
    }
}
