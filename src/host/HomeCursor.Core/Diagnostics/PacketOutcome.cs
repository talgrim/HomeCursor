namespace HomeCursor.Core.Diagnostics;

/// <summary>What the pointer receiver did with a received datagram.</summary>
public enum PacketOutcome
{
    /// <summary>Handed to the dispatcher.</summary>
    Applied,

    /// <summary>Not a valid datagram.</summary>
    Undecodable,

    /// <summary>A valid datagram that does not carry pointer motion.</summary>
    NotPointer,

    /// <summary>No active session with that id and sender address.</summary>
    Rejected,

    /// <summary>Duplicate or older than a datagram already applied.</summary>
    Stale
}
