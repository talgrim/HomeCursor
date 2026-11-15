namespace HomeCursor.Protocol.Messages;

/// <summary>Client to host: first message on a TCP connection.</summary>
public sealed record HelloMessage(byte ProtocolVersion, string ClientName) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.Hello;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(ProtocolVersion);
        WriteString(writer, ClientName);
    }
}
