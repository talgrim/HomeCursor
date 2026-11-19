namespace HomeCursor.Protocol;

/// <summary>
/// Constants shared by every implementation of the HomeCursor wire protocol.
/// </summary>
public static class ProtocolDefaults
{
    public const byte Version = 1;

    public const int TcpPort = 47800;

    public const int UdpPointerPort = 47801;

    public const int DiscoveryPort = 47802;

    /// <summary>Maximum size of one encoded message (type byte included).</summary>
    public const int MaxMessageLength = 8192;

    /// <summary>Maximum UTF-8 size of any string field.</summary>
    public const int MaxStringBytes = 2048;
}
