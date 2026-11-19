namespace HomeCursor.Protocol.Messages;

/// <summary>Host to client: unicast answer to a <see cref="DiscoveryProbeMessage"/>.</summary>
public sealed record DiscoveryReplyMessage(
    byte ProtocolVersion,
    string HostName,
    ushort TcpPort,
    ushort UdpPort,
    bool IsBusy) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.DiscoveryReply;

    internal static DiscoveryReplyMessage Read(BinaryReader reader) =>
        new(reader.ReadByte(), WireFormat.ReadString(reader), reader.ReadUInt16(), reader.ReadUInt16(), reader.ReadBoolean());

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(ProtocolVersion);
        WireFormat.WriteString(writer, HostName);
        writer.Write(TcpPort);
        writer.Write(UdpPort);
        writer.Write(IsBusy);
    }
}
