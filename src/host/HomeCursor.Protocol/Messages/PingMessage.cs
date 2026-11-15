namespace HomeCursor.Protocol.Messages;

/// <summary>Client to host: keep-alive. The host answers with <see cref="PongMessage"/>.</summary>
public sealed record PingMessage : ProtocolMessage;
