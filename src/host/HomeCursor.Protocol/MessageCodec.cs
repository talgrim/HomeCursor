using System.Text;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol;

/// <summary>
/// Binary encoding of <see cref="ProtocolMessage"/>: one type byte followed by little-endian fields.
/// Strings are a UInt16 byte count followed by UTF-8 bytes.
/// </summary>
public static class MessageCodec
{
    public static byte[] Encode(ProtocolMessage message)
    {
        ArgumentNullException.ThrowIfNull(message);

        using var stream = new MemoryStream();
        using (var writer = new BinaryWriter(stream, ProtocolMessage.Encoding, leaveOpen: true))
        {
            message.Write(writer);
        }

        return stream.ToArray();
    }

    public static ProtocolMessage Decode(byte[] buffer, int offset, int count)
    {
        ArgumentNullException.ThrowIfNull(buffer);

        if (count < 1)
        {
            throw new ProtocolException("Message is empty.");
        }

        if (count > ProtocolDefaults.MaxMessageLength)
        {
            throw new ProtocolException($"Message is longer than {ProtocolDefaults.MaxMessageLength} bytes.");
        }

        using var stream = new MemoryStream(buffer, offset, count, writable: false);
        using var reader = new BinaryReader(stream, ProtocolMessage.Encoding, leaveOpen: true);

        try
        {
            var type = (MessageType)reader.ReadByte();
            return type switch
            {
                MessageType.Hello => new HelloMessage(reader.ReadByte(), ReadString(reader)),
                MessageType.Welcome => new WelcomeMessage(
                    reader.ReadByte(), reader.ReadUInt32(), reader.ReadUInt16(), reader.ReadByte()),
                MessageType.Reject => new RejectMessage(ReadEnum<RejectReason>(reader)),
                MessageType.Ping => new PingMessage(),
                MessageType.Pong => new PongMessage(),
                MessageType.MouseMove => new MouseMoveMessage(reader.ReadInt16(), reader.ReadInt16()),
                MessageType.MouseButton => new MouseButtonMessage(ReadEnum<MouseButton>(reader), reader.ReadBoolean()),
                MessageType.Scroll => new ScrollMessage(reader.ReadInt16(), reader.ReadInt16()),
                MessageType.Key => new KeyMessage(ReadEnum<SpecialKey>(reader), reader.ReadBoolean()),
                MessageType.Text => new TextMessage(ReadString(reader)),
                MessageType.DiscoveryProbe => new DiscoveryProbeMessage(reader.ReadByte()),
                MessageType.DiscoveryReply => new DiscoveryReplyMessage(
                    reader.ReadByte(), ReadString(reader), reader.ReadUInt16(), reader.ReadUInt16(), reader.ReadBoolean()),
                _ => throw new ProtocolException($"Unknown message type {(byte)type}.")
            };
        }
        catch (EndOfStreamException ex)
        {
            throw new ProtocolException("Message is truncated.", ex);
        }
    }

    private static string ReadString(BinaryReader reader)
    {
        var length = reader.ReadUInt16();
        if (length > ProtocolDefaults.MaxStringBytes)
        {
            throw new ProtocolException($"String is longer than {ProtocolDefaults.MaxStringBytes} UTF-8 bytes.");
        }

        var bytes = reader.ReadBytes(length);
        if (bytes.Length != length)
        {
            throw new EndOfStreamException();
        }

        try
        {
            return ProtocolMessage.Encoding.GetString(bytes);
        }
        catch (DecoderFallbackException ex)
        {
            throw new ProtocolException("String is not valid UTF-8.", ex);
        }
    }

    private static T ReadEnum<T>(BinaryReader reader)
        where T : struct, Enum
    {
        var value = reader.ReadByte();
        if (!Enum.IsDefined(typeof(T), value))
        {
            throw new ProtocolException($"Invalid {typeof(T).Name} value {value}.");
        }

        return (T)Enum.ToObject(typeof(T), value);
    }
}
