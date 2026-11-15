namespace HomeCursor.Protocol.Messages;

/// <summary>Client to host: keep-alive. The host answers with <see cref="PongMessage"/>.</summary>
public sealed record PingMessage : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.Ping;

    internal static PingMessage Read(BinaryReader reader) => new();
}
