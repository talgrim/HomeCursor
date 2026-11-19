namespace HomeCursor.Protocol.Messages;

/// <summary>Host to client: connection refused. The host closes the connection afterwards.</summary>
public sealed record RejectMessage(RejectReason Reason) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.Reject;

    internal static RejectMessage Read(BinaryReader reader) =>
        new(WireFormat.ReadEnum<RejectReason>(reader));

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write((byte)Reason);
    }
}
