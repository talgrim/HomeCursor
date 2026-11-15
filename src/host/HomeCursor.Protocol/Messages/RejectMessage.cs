namespace HomeCursor.Protocol.Messages;

/// <summary>Host to client: connection refused. The host closes the connection afterwards.</summary>
public sealed record RejectMessage(RejectReason Reason) : ProtocolMessage;
