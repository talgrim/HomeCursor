using System.Text;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Protocol;

/// <summary>
/// Field-level rules of the binary format shared by every message: strict UTF-8, length-prefixed strings
/// and validated one-byte enums. Integers are written by <see cref="BinaryWriter"/> / <see cref="BinaryReader"/>
/// (little-endian).
/// </summary>
internal static class WireFormat
{
    public static readonly UTF8Encoding Utf8 = new(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true);

    public static void WriteString(BinaryWriter writer, string value)
    {
        ArgumentNullException.ThrowIfNull(value);

        var bytes = Utf8.GetBytes(value);
        if (bytes.Length > ProtocolDefaults.MaxStringBytes)
        {
            throw new ArgumentException(
                $"String is longer than {ProtocolDefaults.MaxStringBytes} UTF-8 bytes.", nameof(value));
        }

        writer.Write((ushort)bytes.Length);
        writer.Write(bytes);
    }

    /// <exception cref="EndOfStreamException">The string is cut off.</exception>
    /// <exception cref="ProtocolException">The string is too long or not valid UTF-8.</exception>
    public static string ReadString(BinaryReader reader)
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
            return Utf8.GetString(bytes);
        }
        catch (DecoderFallbackException ex)
        {
            throw new ProtocolException("String is not valid UTF-8.", ex);
        }
    }

    public static T ReadEnum<T>(BinaryReader reader)
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
