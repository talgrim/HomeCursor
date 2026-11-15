namespace HomeCursor.Protocol.Messages;

/// <summary>Text typed on the client; the host types it character by character.</summary>
public sealed record TextMessage(string Text) : ProtocolMessage;
