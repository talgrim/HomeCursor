namespace HomeCursor.Protocol.Messages;

/// <summary>Text typed on the client; the host types it character by character.</summary>
public sealed record TextMessage(string Text) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.Text;

    internal static TextMessage Read(BinaryReader reader) =>
        new(WireFormat.ReadString(reader));

    private protected override void WriteCore(BinaryWriter writer)
    {
        WireFormat.WriteString(writer, Text);
    }
}
