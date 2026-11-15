namespace HomeCursor.Protocol.Messages;

/// <summary>Host to client: answer to <see cref="PingMessage"/>.</summary>
public sealed record PongMessage : ProtocolMessage;
