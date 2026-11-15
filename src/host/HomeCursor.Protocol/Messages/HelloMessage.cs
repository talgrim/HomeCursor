namespace HomeCursor.Protocol.Messages;

/// <summary>Client to host: first message on a TCP connection.</summary>
public sealed record HelloMessage(byte ProtocolVersion, string ClientName) : ProtocolMessage;
