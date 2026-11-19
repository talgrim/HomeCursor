package com.homecursor.client.session

import com.homecursor.client.connection.ConnectionFailure
import com.homecursor.client.connection.HostEndpoint

/** What the app is doing about its connection to a host. */
sealed interface ConnectionStatus {
    /** Not connected and nothing in progress. */
    data object Idle : ConnectionStatus

    data class Connecting(val host: HostEndpoint, val isReconnect: Boolean = false) : ConnectionStatus

    /** The saved host did not answer at its address, so the network is searched for it by name. */
    data class Searching(val host: HostEndpoint) : ConnectionStatus

    /** The host is occupied by another client (or by our own stale session); trying again shortly. */
    data class Busy(val host: HostEndpoint) : ConnectionStatus

    data class Connected(val host: HostEndpoint) : ConnectionStatus

    /** Gave up. [host] is the one last tried. */
    data class Failed(val host: HostEndpoint, val failure: ConnectionFailure) : ConnectionStatus
}
