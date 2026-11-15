namespace HomeCursor.Protocol.Messages;

/// <summary>
/// Scroll amount in 1/120 of a wheel notch. Positive vertical scrolls up, positive horizontal scrolls right.
/// </summary>
public sealed record ScrollMessage(short Horizontal, short Vertical) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.Scroll;

    internal static ScrollMessage Read(BinaryReader reader) =>
        new(reader.ReadInt16(), reader.ReadInt16());

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(Horizontal);
        writer.Write(Vertical);
    }
}
