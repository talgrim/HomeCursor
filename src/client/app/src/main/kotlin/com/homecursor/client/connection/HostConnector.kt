package com.homecursor.client.connection

import com.homecursor.client.protocol.ProtocolMessage
import kotlinx.coroutines.flow.StateFlow

sealed interface SessionState {
    data object Active : SessionState

    /** The session ended. [failure] is null when the client closed it itself. */
    data class Closed(val failure: ConnectionFailure?) : SessionState
}

/** An accepted connection to a host. Pings are sent and dead hosts are detected until it is closed. */
interface HostSession {
    val endpoint: HostEndpoint
    val sessionId: Long

    /** The UDP port the host receives pointer datagrams on, from its Welcome (authoritative for this session). */
    val udpPort: Int
    val state: StateFlow<SessionState>

    /**
     * Sends [message] over the TCP connection. This is the only write path, so frames never interleave with the
     * ping loop's. It blocks while the socket is full, so call it off the main thread. A write failure ends the
     * session (see [state]) instead of throwing. Does nothing once the session is closed.
     */
    fun send(message: ProtocolMessage)

    /** Ends the session cleanly so the host frees its client slot at once. Safe to call more than once. */
    fun close()
}

interface HostConnector {
    /** Connects and completes the handshake. @throws ConnectionException the host could not be used. */
    suspend fun connect(endpoint: HostEndpoint): HostSession
}
