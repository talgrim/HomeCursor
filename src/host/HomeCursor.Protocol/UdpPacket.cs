using System.Buffers.Binary;
using System.Diagnostics.CodeAnalysis;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol;

/// <summary>
/// UDP datagram carrying one pointer message: UInt32 session id, UInt32 sequence number (both little-endian)
/// followed by an encoded message.
/// </summary>
public sealed record UdpPacket(uint SessionId, uint Sequence, ProtocolMessage Message)
{
    public const int HeaderLength = 8;

    public byte[] Encode()
    {
        var body = MessageCodec.Encode(Message);
        var buffer = new byte[HeaderLength + body.Length];
        BinaryPrimitives.WriteUInt32LittleEndian(buffer, SessionId);
        BinaryPrimitives.WriteUInt32LittleEndian(buffer.AsSpan(4), Sequence);
        body.CopyTo(buffer, HeaderLength);
        return buffer;
    }

    public static bool TryDecode(byte[] buffer, [NotNullWhen(true)] out UdpPacket? packet)
    {
        ArgumentNullException.ThrowIfNull(buffer);

        packet = null;
        if (buffer.Length <= HeaderLength)
        {
            return false;
        }

        try
        {
            var sessionId = BinaryPrimitives.ReadUInt32LittleEndian(buffer);
            var sequence = BinaryPrimitives.ReadUInt32LittleEndian(buffer.AsSpan(4));
            var message = MessageCodec.Decode(buffer, HeaderLength, buffer.Length - HeaderLength);
            packet = new UdpPacket(sessionId, sequence, message);
            return true;
        }
        catch (ProtocolException)
        {
            return false;
        }
    }
}
