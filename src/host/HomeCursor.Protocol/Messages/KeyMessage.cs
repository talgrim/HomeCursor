namespace HomeCursor.Protocol.Messages;

public sealed record KeyMessage(SpecialKey Key, bool IsDown) : ProtocolMessage;
