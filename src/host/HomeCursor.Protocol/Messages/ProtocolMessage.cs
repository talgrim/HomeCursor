using System.Text;

namespace HomeCursor.Protocol.Messages;

/// <summary>
/// Base type of every message exchanged between client and host.
/// </summary>
public abstract record ProtocolMessage
{
    internal static readonly UTF8Encoding Encoding = new(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true);

    protected abstract MessageType MessageType { get; }

    internal void Write(BinaryWriter writer)
    {
        writer.Write((byte)MessageType);
        WriteCore(writer);
    }

    protected virtual void WriteCore(BinaryWriter writer)
    {
    }

    protected static void WriteString(BinaryWriter writer, string value)
    {
        ArgumentNullException.ThrowIfNull(value);

        var bytes = Encoding.GetBytes(value);
        if (bytes.Length > ProtocolDefaults.MaxStringBytes)
        {
            throw new ArgumentException(
                $"String is longer than {ProtocolDefaults.MaxStringBytes} UTF-8 bytes.", nameof(value));
        }

        writer.Write((ushort)bytes.Length);
        writer.Write(bytes);
    }
}
