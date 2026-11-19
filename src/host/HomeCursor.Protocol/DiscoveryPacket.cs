using System.Diagnostics.CodeAnalysis;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol;

/// <summary>
/// UDP discovery datagram: the ASCII magic "HCUR" followed by an encoded discovery message.
/// </summary>
public static class DiscoveryPacket
{
    private static readonly byte[] Magic = "HCUR"u8.ToArray();

    public static byte[] Encode(ProtocolMessage message)
    {
        if (message is not (DiscoveryProbeMessage or DiscoveryReplyMessage))
        {
            throw new ArgumentException("Only discovery messages can be sent in a discovery packet.", nameof(message));
        }

        var body = MessageCodec.Encode(message);
        var buffer = new byte[Magic.Length + body.Length];
        Magic.CopyTo(buffer, 0);
        body.CopyTo(buffer, Magic.Length);
        return buffer;
    }

    public static bool TryDecode(byte[] buffer, [NotNullWhen(true)] out ProtocolMessage? message)
    {
        ArgumentNullException.ThrowIfNull(buffer);

        message = null;
        if (buffer.Length <= Magic.Length || !buffer.AsSpan(0, Magic.Length).SequenceEqual(Magic))
        {
            return false;
        }

        try
        {
            var decoded = MessageCodec.Decode(buffer, Magic.Length, buffer.Length - Magic.Length);
            if (decoded is not (DiscoveryProbeMessage or DiscoveryReplyMessage))
            {
                return false;
            }

            message = decoded;
            return true;
        }
        catch (ProtocolException)
        {
            return false;
        }
    }
}
