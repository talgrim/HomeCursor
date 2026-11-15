using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol.Tests;

public class SpecialKeyTests
{
    [Theory]
    [InlineData(SpecialKey.Space, 19)]
    [InlineData(SpecialKey.A, 64)]
    [InlineData(SpecialKey.Z, 89)]
    [InlineData(SpecialKey.D0, 96)]
    [InlineData(SpecialKey.D9, 105)]
    [InlineData(SpecialKey.Minus, 112)]
    [InlineData(SpecialKey.Grave, 122)]
    public void Key_HasTheCodeFromTheSpec(SpecialKey key, byte code)
    {
        Assert.Equal(code, (byte)key);
    }

    [Fact]
    public void Codes_AreUnique()
    {
        var codes = Enum.GetValues<SpecialKey>().Select(key => (byte)key).ToList();

        Assert.Equal(codes.Count, codes.Distinct().Count());
    }

    [Fact]
    public void EveryKey_RoundTripsThroughTheCodec()
    {
        foreach (var key in Enum.GetValues<SpecialKey>())
        {
            var bytes = MessageCodec.Encode(new KeyMessage(key, true));

            Assert.Equal(new KeyMessage(key, true), MessageCodec.Decode(bytes, 0, bytes.Length));
        }
    }

    [Fact]
    public void Encode_KeyAltDown_UsesSpecBytes()
    {
        Assert.Equal(new byte[] { 0x13, 0x11, 0x01 }, MessageCodec.Encode(new KeyMessage(SpecialKey.Alt, true)));
        Assert.Equal(new byte[] { 0x13, 0x43, 0x00 }, MessageCodec.Encode(new KeyMessage(SpecialKey.D, false)));
    }
}
