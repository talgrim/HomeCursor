package com.homecursor.client.connection

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
    val state: StateFlow<SessionState>

    /** Ends the session cleanly so the host frees its client slot at once. Safe to call more than once. */
    fun close()
}

interface HostConnector {
    /** Connects and completes the handshake. @throws ConnectionException the host could not be used. */
    suspend fun connect(endpoint: HostEndpoint): HostSession
}
