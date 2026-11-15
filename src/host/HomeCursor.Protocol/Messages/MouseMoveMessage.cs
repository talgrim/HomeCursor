namespace HomeCursor.Protocol.Messages;

/// <summary>Relative pointer movement in pixels. Sent over UDP (or TCP).</summary>
public sealed record MouseMoveMessage(short Dx, short Dy) : ProtocolMessage;
