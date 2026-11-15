namespace HomeCursor.Core.Sessions;

/// <summary>Why a client session ended.</summary>
public enum SessionEndReason
{
    /// <summary>The client closed the connection cleanly.</summary>
    ClientDisconnected,

    /// <summary>The client sent nothing for longer than the heartbeat timeout.</summary>
    TimedOut,

    /// <summary>The connection broke (network error, client app killed, Wi-Fi lost).</summary>
    ConnectionLost,

    /// <summary>The client sent data that violates the protocol.</summary>
    ProtocolError,

    /// <summary>The host is shutting down.</summary>
    HostStopping,

    /// <summary>An unexpected error occurred while serving the client.</summary>
    Error
}
