using System.Buffers.Binary;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol;

/// <summary>
/// TCP framing: a UInt16 little-endian length followed by that many bytes of an encoded message.
/// </summary>
public static class MessageFraming
{
    private const int HeaderLength = 2;

    public static async Task WriteAsync(Stream stream, ProtocolMessage message, CancellationToken cancellationToken)
    {
        var payload = MessageCodec.Encode(message);
        if (payload.Length > ProtocolDefaults.MaxMessageLength)
        {
            throw new ArgumentException(
                $"Message is longer than {ProtocolDefaults.MaxMessageLength} bytes.", nameof(message));
        }

        var frame = new byte[HeaderLength + payload.Length];
        BinaryPrimitives.WriteUInt16LittleEndian(frame, (ushort)payload.Length);
        payload.CopyTo(frame, HeaderLength);

        await stream.WriteAsync(frame, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Reads one message. Returns <c>null</c> when the stream ended cleanly between two frames.
    /// </summary>
    /// <exception cref="ProtocolException">The frame is malformed or the stream ended inside a frame.</exception>
    public static async Task<ProtocolMessage?> ReadAsync(Stream stream, CancellationToken cancellationToken)
    {
        var header = new byte[HeaderLength];
        if (!await TryReadExactlyAsync(stream, header, cancellationToken).ConfigureAwait(false))
        {
            return null;
        }

        var length = BinaryPrimitives.ReadUInt16LittleEndian(header);
        if (length is 0 or > ProtocolDefaults.MaxMessageLength)
        {
            throw new ProtocolException($"Invalid frame length {length}.");
        }

        var payload = new byte[length];
        if (!await TryReadExactlyAsync(stream, payload, cancellationToken).ConfigureAwait(false))
        {
            throw new ProtocolException("Stream ended inside a frame.");
        }

        return MessageCodec.Decode(payload, 0, length);
    }

    private static async Task<bool> TryReadExactlyAsync(Stream stream, byte[] buffer, CancellationToken cancellationToken)
    {
        var total = 0;
        while (total < buffer.Length)
        {
            var read = await stream.ReadAsync(buffer.AsMemory(total), cancellationToken).ConfigureAwait(false);
            if (read == 0)
            {
                if (total == 0)
                {
                    return false;
                }

                throw new ProtocolException("Stream ended inside a frame.");
            }

            total += read;
        }

        return true;
    }
}
