namespace HomeCursor.Protocol.Messages;

/// <summary>
/// Scroll amount in 1/120 of a wheel notch. Positive vertical scrolls up, positive horizontal scrolls right.
/// </summary>
public sealed record ScrollMessage(short Horizontal, short Vertical) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.Scroll;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(Horizontal);
        writer.Write(Vertical);
    }
}
