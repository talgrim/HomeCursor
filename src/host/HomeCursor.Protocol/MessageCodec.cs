using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol;

/// <summary>
/// Binary encoding of <see cref="ProtocolMessage"/>: one type byte followed by little-endian fields.
/// Each message writes its own fields; the reader of each message type is registered here.
/// </summary>
public static class MessageCodec
{
    private static readonly Dictionary<MessageType, Func<BinaryReader, ProtocolMessage>> Readers = new()
    {
        [MessageType.Hello] = HelloMessage.Read,
        [MessageType.Welcome] = WelcomeMessage.Read,
        [MessageType.Reject] = RejectMessage.Read,
        [MessageType.Ping] = PingMessage.Read,
        [MessageType.Pong] = PongMessage.Read,
        [MessageType.MouseMove] = MouseMoveMessage.Read,
        [MessageType.MouseButton] = MouseButtonMessage.Read,
        [MessageType.Scroll] = ScrollMessage.Read,
        [MessageType.Key] = KeyMessage.Read,
        [MessageType.Text] = TextMessage.Read,
        [MessageType.DiscoveryProbe] = DiscoveryProbeMessage.Read,
        [MessageType.DiscoveryReply] = DiscoveryReplyMessage.Read
    };

    public static byte[] Encode(ProtocolMessage message)
    {
        ArgumentNullException.ThrowIfNull(message);

        using var stream = new MemoryStream();
        using (var writer = new BinaryWriter(stream, WireFormat.Utf8, leaveOpen: true))
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
        using var reader = new BinaryReader(stream, WireFormat.Utf8, leaveOpen: true);

        try
        {
            var type = (MessageType)reader.ReadByte();
            if (!Readers.TryGetValue(type, out var read))
            {
                throw new ProtocolException($"Unknown message type {(byte)type}.");
            }

            return read(reader);
        }
        catch (EndOfStreamException ex)
        {
            throw new ProtocolException("Message is truncated.", ex);
        }
    }
}
