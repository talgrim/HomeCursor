namespace HomeCursor.Protocol.Messages;

/// <summary>
/// Host to client: connection accepted. <paramref name="SessionId"/> must be put in every UDP datagram,
/// and the client should send a <see cref="PingMessage"/> every <paramref name="PingIntervalSeconds"/>.
/// </summary>
public sealed record WelcomeMessage(byte ProtocolVersion, uint SessionId, ushort UdpPort, byte PingIntervalSeconds)
    : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.Welcome;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(ProtocolVersion);
        writer.Write(SessionId);
        writer.Write(UdpPort);
        writer.Write(PingIntervalSeconds);
    }
}
