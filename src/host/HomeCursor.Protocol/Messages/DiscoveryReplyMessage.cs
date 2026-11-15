namespace HomeCursor.Protocol.Messages;

/// <summary>Host to client: unicast answer to a <see cref="DiscoveryProbeMessage"/>.</summary>
public sealed record DiscoveryReplyMessage(
    byte ProtocolVersion,
    string HostName,
    ushort TcpPort,
    ushort UdpPort,
    bool IsBusy) : ProtocolMessage;
