namespace HomeCursor.Protocol.Messages;

/// <summary>Relative pointer movement in pixels. Sent over UDP (or TCP).</summary>
public sealed record MouseMoveMessage(short Dx, short Dy) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.MouseMove;

    internal static MouseMoveMessage Read(BinaryReader reader) =>
        new(reader.ReadInt16(), reader.ReadInt16());

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(Dx);
        writer.Write(Dy);
    }
}
