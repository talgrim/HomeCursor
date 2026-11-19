package com.homecursor.client.connection

/** Why connecting to a host failed or an established connection ended. */
enum class ConnectionFailure {
    /** Another client is already connected to the host. */
    Busy,

    /** The host speaks a different protocol version. */
    UnsupportedVersion,

    /** The host could not be reached (refused, no route, unknown address). */
    Unreachable,

    /** The host did not answer in time. */
    Timeout,

    /** The host sent data that is not valid protocol data. */
    ProtocolError,

    /** The host closed the connection. */
    HostClosed,
}

class ConnectionException(val failure: ConnectionFailure, cause: Throwable? = null) :
    Exception(failure.name, cause)
