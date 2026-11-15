namespace HomeCursor.Protocol.Messages;

/// <summary>Host to client: connection refused. The host closes the connection afterwards.</summary>
public sealed record RejectMessage(RejectReason Reason) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.Reject;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write((byte)Reason);
    }
}
