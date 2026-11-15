namespace HomeCursor.Protocol.Messages;

/// <summary>Text typed on the client; the host types it character by character.</summary>
public sealed record TextMessage(string Text) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.Text;

    protected override void WriteCore(BinaryWriter writer)
    {
        WriteString(writer, Text);
    }
}
