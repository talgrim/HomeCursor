namespace HomeCursor.Protocol;

public enum RejectReason : byte
{
    /// <summary>Another client is already connected.</summary>
    Busy = 1,

    /// <summary>The client speaks a protocol version the host does not support.</summary>
    UnsupportedVersion = 2
}
