namespace HomeCursor.Protocol.Messages;

public sealed record MouseButtonMessage(MouseButton Button, bool IsDown) : ProtocolMessage;
