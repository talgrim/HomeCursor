namespace HomeCursor.Protocol.Messages;

/// <summary>Client to host: UDP broadcast looking for hosts.</summary>
public sealed record DiscoveryProbeMessage(byte ProtocolVersion) : ProtocolMessage
{
    private protected override MessageType MessageType => MessageType.DiscoveryProbe;

    internal static DiscoveryProbeMessage Read(BinaryReader reader) =>
        new(reader.ReadByte());

    private protected override void WriteCore(BinaryWriter writer)
    {
        writer.Write(ProtocolVersion);
    }
}
