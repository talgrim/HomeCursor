using System.Text;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol;

/// <summary>
/// Binary encoding of <see cref="ProtocolMessage"/>: one type byte followed by little-endian fields.
/// Strings are a UInt16 byte count followed by UTF-8 bytes.
/// </summary>
public static class MessageCodec
{
    private static readonly UTF8Encoding StrictUtf8 = new(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true);

    public static byte[] Encode(ProtocolMessage message)
    {
        ArgumentNullException.ThrowIfNull(message);

        using var stream = new MemoryStream();
        using (var writer = new BinaryWriter(stream, StrictUtf8, leaveOpen: true))
        {
            switch (message)
            {
                case HelloMessage m:
                    writer.Write((byte)MessageType.Hello);
                    writer.Write(m.ProtocolVersion);
                    WriteString(writer, m.ClientName);
                    break;
                case WelcomeMessage m:
                    writer.Write((byte)MessageType.Welcome);
                    writer.Write(m.ProtocolVersion);
                    writer.Write(m.SessionId);
                    writer.Write(m.UdpPort);
                    writer.Write(m.PingIntervalSeconds);
                    break;
                case RejectMessage m:
                    writer.Write((byte)MessageType.Reject);
                    writer.Write((byte)m.Reason);
                    break;
                case PingMessage:
                    writer.Write((byte)MessageType.Ping);
                    break;
                case PongMessage:
                    writer.Write((byte)MessageType.Pong);
                    break;
                case MouseMoveMessage m:
                    writer.Write((byte)MessageType.MouseMove);
                    writer.Write(m.Dx);
                    writer.Write(m.Dy);
                    break;
                case MouseButtonMessage m:
                    writer.Write((byte)MessageType.MouseButton);
                    writer.Write((byte)m.Button);
                    writer.Write(m.IsDown);
                    break;
                case ScrollMessage m:
                    writer.Write((byte)MessageType.Scroll);
                    writer.Write(m.Horizontal);
                    writer.Write(m.Vertical);
                    break;
                case KeyMessage m:
                    writer.Write((byte)MessageType.Key);
                    writer.Write((byte)m.Key);
                    writer.Write(m.IsDown);
                    break;
                case TextMessage m:
                    writer.Write((byte)MessageType.Text);
                    WriteString(writer, m.Text);
                    break;
                case DiscoveryProbeMessage m:
                    writer.Write((byte)MessageType.DiscoveryProbe);
                    writer.Write(m.ProtocolVersion);
                    break;
                case DiscoveryReplyMessage m:
                    writer.Write((byte)MessageType.DiscoveryReply);
                    writer.Write(m.ProtocolVersion);
                    WriteString(writer, m.HostName);
                    writer.Write(m.TcpPort);
                    writer.Write(m.UdpPort);
                    writer.Write(m.IsBusy);
                    break;
                default:
                    throw new ArgumentException($"Unsupported message type {message.GetType().Name}.", nameof(message));
            }
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
        using var reader = new BinaryReader(stream, StrictUtf8, leaveOpen: true);

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

    private static void WriteString(BinaryWriter writer, string value)
    {
        ArgumentNullException.ThrowIfNull(value);

        var bytes = StrictUtf8.GetBytes(value);
        if (bytes.Length > ProtocolDefaults.MaxStringBytes)
        {
            throw new ArgumentException(
                $"String is longer than {ProtocolDefaults.MaxStringBytes} UTF-8 bytes.", nameof(value));
        }

        writer.Write((ushort)bytes.Length);
        writer.Write(bytes);
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
            return StrictUtf8.GetString(bytes);
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
