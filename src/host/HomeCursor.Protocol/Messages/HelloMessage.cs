namespace HomeCursor.Protocol.Messages;

/// <summary>Client to host: first message on a TCP connection.</summary>
public sealed record HelloMessage(byte ProtocolVersion, string ClientName) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.Hello;

    internal static HelloMessage Read(BinaryReader reader) =>
        new(reader.ReadByte(), WireFormat.ReadString(reader));

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(ProtocolVersion);
        WireFormat.WriteString(writer, ClientName);
    }
}
