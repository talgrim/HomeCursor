using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol.Tests;

public class UdpPacketTests
{
    [Fact]
    public void EncodeThenTryDecode_ReturnsEqualPacket()
    {
        var packet = new UdpPacket(42, 7, new MouseMoveMessage(3, -4));

        var decoded = UdpPacket.TryDecode(packet.Encode(), out var result);

        Assert.True(decoded);
        Assert.Equal(packet, result);
    }

    [Fact]
    public void Encode_PutsSessionIdAndSequenceInHeader()
    {
        var bytes = new UdpPacket(1, 2, new PingMessage()).Encode();

        Assert.Equal(new byte[] { 1, 0, 0, 0, 2, 0, 0, 0, (byte)MessageType.Ping }, bytes);
    }

    [Fact]
    public void TryDecode_BufferShorterThanHeader_ReturnsFalse()
    {
        Assert.False(UdpPacket.TryDecode(new byte[UdpPacket.HeaderLength], out _));
    }

    [Fact]
    public void TryDecode_GarbageBody_ReturnsFalse()
    {
        var buffer = new byte[UdpPacket.HeaderLength + 1];
        buffer[^1] = 0xFF;

        Assert.False(UdpPacket.TryDecode(buffer, out _));
    }
}
