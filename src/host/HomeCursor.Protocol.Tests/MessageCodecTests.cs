using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol.Tests;

public class MessageCodecTests
{
    public static TheoryData<ProtocolMessage> AllMessages => new()
    {
        new HelloMessage(1, "Pixel 8"),
        new WelcomeMessage(1, 0xDEADBEEF, 47801, 5),
        new RejectMessage(RejectReason.Busy),
        new PingMessage(),
        new PongMessage(),
        new MouseMoveMessage(-12, 345),
        new MouseButtonMessage(MouseButton.Right, true),
        new MouseButtonMessage(MouseButton.Middle, false),
        new ScrollMessage(-120, 240),
        new KeyMessage(SpecialKey.F5, false),
        new TextMessage("Zażółć gęślą jaźń 🙂"),
        new DiscoveryProbeMessage(1),
        new DiscoveryReplyMessage(1, "DESKTOP-PC", 47800, 47801, true)
    };

    [Theory]
    [MemberData(nameof(AllMessages))]
    public void EncodeThenDecode_ReturnsEqualMessage(ProtocolMessage message)
    {
        var bytes = MessageCodec.Encode(message);

        var decoded = MessageCodec.Decode(bytes, 0, bytes.Length);

        Assert.Equal(message, decoded);
    }

    [Fact]
    public void TestData_CoversEveryMessageTypeExactlyOnce()
    {
        var concreteMessages = typeof(ProtocolMessage).Assembly.GetTypes()
            .Where(type => type.IsSubclassOf(typeof(ProtocolMessage)) && !type.IsAbstract)
            .ToHashSet();
        var coveredMessages = ((IEnumerable<object[]>)AllMessages).Select(row => row[0].GetType()).ToList();

        Assert.Equal(concreteMessages.Count, Enum.GetValues<MessageType>().Length);
        Assert.Equal(concreteMessages.Count, coveredMessages.Distinct().Count());
        Assert.True(concreteMessages.SetEquals(coveredMessages));
    }

    [Fact]
    public void Encode_MouseMove_UsesLittleEndianLayout()
    {
        var bytes = MessageCodec.Encode(new MouseMoveMessage(1, -2));

        Assert.Equal(new byte[] { 16, 0x01, 0x00, 0xFE, 0xFF }, bytes);
    }

    [Fact]
    public void Decode_HonorsOffsetAndCount()
    {
        var encoded = MessageCodec.Encode(new MouseMoveMessage(7, 8));
        var padded = new byte[] { 0xAA, 0xBB }.Concat(encoded).Concat(new byte[] { 0xCC }).ToArray();

        var decoded = MessageCodec.Decode(padded, 2, encoded.Length);

        Assert.Equal(new MouseMoveMessage(7, 8), decoded);
    }

    [Fact]
    public void Decode_EmptyBuffer_Throws()
    {
        Assert.Throws<ProtocolException>(() => MessageCodec.Decode([], 0, 0));
    }

    [Fact]
    public void Decode_UnknownType_Throws()
    {
        Assert.Throws<ProtocolException>(() => MessageCodec.Decode([0xFF], 0, 1));
    }

    [Fact]
    public void Decode_TruncatedMessage_Throws()
    {
        var bytes = MessageCodec.Encode(new WelcomeMessage(1, 5, 6, 7));

        Assert.Throws<ProtocolException>(() => MessageCodec.Decode(bytes, 0, bytes.Length - 1));
    }

    [Fact]
    public void Decode_InvalidEnumValue_Throws()
    {
        var bytes = new byte[] { (byte)MessageType.MouseButton, 99, 1 };

        Assert.Throws<ProtocolException>(() => MessageCodec.Decode(bytes, 0, bytes.Length));
    }

    [Fact]
    public void Decode_StringLongerThanLimit_Throws()
    {
        var length = (ushort)(ProtocolDefaults.MaxStringBytes + 1);
        var bytes = new byte[] { (byte)MessageType.Text, (byte)(length & 0xFF), (byte)(length >> 8) };

        Assert.Throws<ProtocolException>(() => MessageCodec.Decode(bytes, 0, bytes.Length));
    }

    [Fact]
    public void Decode_InvalidUtf8_Throws()
    {
        var bytes = new byte[] { (byte)MessageType.Text, 1, 0, 0xFF };

        Assert.Throws<ProtocolException>(() => MessageCodec.Decode(bytes, 0, bytes.Length));
    }

    [Fact]
    public void Encode_StringLongerThanLimit_Throws()
    {
        var message = new TextMessage(new string('a', ProtocolDefaults.MaxStringBytes + 1));

        Assert.Throws<ArgumentException>(() => MessageCodec.Encode(message));
    }
}
