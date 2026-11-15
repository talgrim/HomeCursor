namespace HomeCursor.Protocol.Messages;

/// <summary>Client to host: UDP broadcast looking for hosts.</summary>
public sealed record DiscoveryProbeMessage(byte ProtocolVersion) : ProtocolMessage
{
    protected override MessageType MessageType => MessageType.DiscoveryProbe;

    protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(ProtocolVersion);
    }
}
