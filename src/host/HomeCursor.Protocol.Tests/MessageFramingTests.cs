using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol.Tests;

public class MessageFramingTests
{
    [Fact]
    public async Task WriteThenRead_ReturnsSameMessagesInOrder()
    {
        var stream = new MemoryStream();
        await MessageFraming.WriteAsync(stream, new PingMessage(), CancellationToken.None);
        await MessageFraming.WriteAsync(stream, new TextMessage("hello"), CancellationToken.None);
        stream.Position = 0;

        var first = await MessageFraming.ReadAsync(stream, CancellationToken.None);
        var second = await MessageFraming.ReadAsync(stream, CancellationToken.None);

        Assert.Equal(new PingMessage(), first);
        Assert.Equal(new TextMessage("hello"), second);
    }

    [Fact]
    public async Task Read_AtCleanEndOfStream_ReturnsNull()
    {
        var stream = new MemoryStream();

        var message = await MessageFraming.ReadAsync(stream, CancellationToken.None);

        Assert.Null(message);
    }

    [Fact]
    public async Task Read_StreamEndsInsideHeader_Throws()
    {
        var stream = new MemoryStream([0x05]);

        await Assert.ThrowsAsync<ProtocolException>(() => MessageFraming.ReadAsync(stream, CancellationToken.None));
    }

    [Fact]
    public async Task Read_StreamEndsInsidePayload_Throws()
    {
        var stream = new MemoryStream([0x05, 0x00, 0x10]);

        await Assert.ThrowsAsync<ProtocolException>(() => MessageFraming.ReadAsync(stream, CancellationToken.None));
    }

    [Fact]
    public async Task Read_ZeroLengthFrame_Throws()
    {
        var stream = new MemoryStream([0x00, 0x00]);

        await Assert.ThrowsAsync<ProtocolException>(() => MessageFraming.ReadAsync(stream, CancellationToken.None));
    }

    [Fact]
    public async Task Read_FrameLongerThanLimit_Throws()
    {
        var length = (ushort)(ProtocolDefaults.MaxMessageLength + 1);
        var stream = new MemoryStream([(byte)(length & 0xFF), (byte)(length >> 8)]);

        await Assert.ThrowsAsync<ProtocolException>(() => MessageFraming.ReadAsync(stream, CancellationToken.None));
    }
}
