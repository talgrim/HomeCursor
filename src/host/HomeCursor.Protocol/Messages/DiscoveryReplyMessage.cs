namespace HomeCursor.Protocol.Messages;

/// <summary>Host to client: unicast answer to a <see cref="DiscoveryProbeMessage"/>.</summary>
public sealed record DiscoveryReplyMessage(
    byte ProtocolVersion,
    string HostName,
    ushort TcpPort,
    ushort UdpPort,
    bool IsBusy) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.DiscoveryReply;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(ProtocolVersion);
        WriteString(writer, HostName);
        writer.Write(TcpPort);
        writer.Write(UdpPort);
        writer.Write(IsBusy);
    }
}
