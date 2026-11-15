using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol.Tests;

public class DiscoveryPacketTests
{
    [Fact]
    public void EncodeThenTryDecode_Probe_ReturnsEqualMessage()
    {
        var bytes = DiscoveryPacket.Encode(new DiscoveryProbeMessage(1));

        var decoded = DiscoveryPacket.TryDecode(bytes, out var message);

        Assert.True(decoded);
        Assert.Equal(new DiscoveryProbeMessage(1), message);
    }

    [Fact]
    public void Encode_StartsWithMagic()
    {
        var bytes = DiscoveryPacket.Encode(new DiscoveryReplyMessage(1, "pc", 1, 2, false));

        Assert.Equal("HCUR"u8.ToArray(), bytes[..4]);
    }

    [Fact]
    public void Encode_NonDiscoveryMessage_Throws()
    {
        Assert.Throws<ArgumentException>(() => DiscoveryPacket.Encode(new PingMessage()));
    }

    [Fact]
    public void TryDecode_WrongMagic_ReturnsFalse()
    {
        var bytes = DiscoveryPacket.Encode(new DiscoveryProbeMessage(1));
        bytes[0] = (byte)'X';

        Assert.False(DiscoveryPacket.TryDecode(bytes, out _));
    }

    [Fact]
    public void TryDecode_NonDiscoveryMessageBody_ReturnsFalse()
    {
        var bytes = "HCUR"u8.ToArray().Concat(MessageCodec.Encode(new PingMessage())).ToArray();

        Assert.False(DiscoveryPacket.TryDecode(bytes, out _));
    }
}
