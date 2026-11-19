package com.homecursor.client.session

import com.homecursor.client.connection.ConnectionException
import com.homecursor.client.connection.ConnectionFailure
import com.homecursor.client.connection.HostConnector
import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.connection.HostSession
import com.homecursor.client.connection.SessionState
import com.homecursor.client.discovery.HostDiscovery
import com.homecursor.client.input.InputSender
import com.homecursor.client.input.NoOpInputSender
import com.homecursor.client.input.TcpUdpInputSender
import com.homecursor.client.storage.SavedHostStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Keeps the app connected to one host: connects to the remembered host (finding it again by name if its
 * address changed), retries while the host is busy, and reconnects when the connection is lost.
 *
 * The host serves one client at a time and frees the slot of a vanished client only after its heartbeat
 * timeout, so right after the app was killed our own stale session can make the host answer Busy.
 * Automatic connects therefore retry on Busy for about `busyRetryAttempts * busyRetryIntervalMillis`.
 *
 * Call everything from one thread (the main thread); sockets are only touched on [ioDispatcher].
 */
class ConnectionCoordinator(
    private val connector: HostConnector,
    private val discovery: HostDiscovery,
    private val store: SavedHostStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val config: Config = Config(),
    private val inputSenderFactory: (HostSession) -> InputSender = { TcpUdpInputSender(it, ioDispatcher) },
) {
    data class Config(
        val busyRetryIntervalMillis: Long = 1_000,
        val busyRetryAttempts: Int = 20,
        val reconnectDelayMillis: Long = 1_000,
    )

    private val mutableStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = mutableStatus.asStateFlow()

    private val mutableInputSender = MutableStateFlow<InputSender>(NoOpInputSender)

    /**
     * Where to send pointer input: a sender for the current connection while [status] is
     * [ConnectionStatus.Connected], and [NoOpInputSender] at every other time. It is set before the status turns
     * Connected, so whoever reacts to Connected already finds the sender.
     */
    val inputSender: StateFlow<InputSender> = mutableInputSender.asStateFlow()

    private var job: Job? = null
    private var session: HostSession? = null

    /**
     * The app came to the foreground: connect to the remembered host unless already busy doing so.
     * Returns false when there is no remembered host, so the user has to pick one.
     */
    fun start(): Boolean {
        if (job?.isActive == true) return true
        val saved = store.load()
        if (saved == null) {
            mutableStatus.value = ConnectionStatus.Idle
            return false
        }
        // Set at once, not when the job starts, so observers never see Idle between start() and the first attempt.
        mutableStatus.value = ConnectionStatus.Connecting(saved)
        job = scope.launch { run(saved, singleAttempt = false) }
        return true
    }

    /** The user picked a host: one attempt, no waiting for a busy host. */
    fun connectTo(host: HostEndpoint) {
        stopCurrent()
        mutableStatus.value = ConnectionStatus.Connecting(host)
        job = scope.launch { run(host, singleAttempt = true) }
    }

    /** The app left the foreground (or the user disconnected): close the connection so the host frees its slot. */
    fun stop() {
        stopCurrent()
        mutableStatus.value = ConnectionStatus.Idle
    }

    /** Disconnects and removes the remembered host. */
    fun forget() {
        stop()
        store.clear()
    }

    private fun stopCurrent() {
        job?.cancel()
        job = null
        mutableInputSender.value = NoOpInputSender
        val open = session
        session = null
        // Closing a socket is network I/O, which must not happen on the main thread.
        if (open != null) scope.launch(ioDispatcher) { open.close() }
    }

    private sealed interface Outcome {
        class Connected(val session: HostSession) : Outcome
        class Failed(val host: HostEndpoint, val failure: ConnectionFailure) : Outcome
    }

    private suspend fun run(first: HostEndpoint, singleAttempt: Boolean) {
        var target = first
        var single = singleAttempt
        var isReconnect = false
        while (true) {
            mutableStatus.value = ConnectionStatus.Connecting(target, isReconnect)
            val outcome = if (single) attempt(target, retryWhenBusy = false) else connectOrSearch(target)
            single = false

            val connected = when (outcome) {
                is Outcome.Failed -> {
                    mutableStatus.value = ConnectionStatus.Failed(outcome.host, outcome.failure)
                    return
                }
                is Outcome.Connected -> outcome.session
            }

            target = connected.endpoint
            store.save(target)
            mutableInputSender.value = inputSenderFactory(connected)
            mutableStatus.value = ConnectionStatus.Connected(target)

            val closed = connected.state.first { it is SessionState.Closed } as SessionState.Closed
            mutableInputSender.value = NoOpInputSender
            session = null
            if (closed.failure == null) return

            isReconnect = true
            mutableStatus.value = ConnectionStatus.Connecting(target, isReconnect = true)
            delay(config.reconnectDelayMillis)
        }
    }

    /** Tries the saved address; if the host is not there, looks for a host of the same name on the network. */
    private suspend fun connectOrSearch(saved: HostEndpoint): Outcome {
        val direct = attempt(saved, retryWhenBusy = true)
        if (direct !is Outcome.Failed || direct.failure !in SEARCHABLE_FAILURES) return direct

        mutableStatus.value = ConnectionStatus.Searching(saved)
        val found = discovery.discover().firstOrNull { it.name == saved.name } ?: return direct

        val moved = HostEndpoint(found.address, found.name, found.tcpPort, found.udpPort)
        mutableStatus.value = ConnectionStatus.Connecting(moved)
        return attempt(moved, retryWhenBusy = true)
    }

    private suspend fun attempt(target: HostEndpoint, retryWhenBusy: Boolean): Outcome {
        var tries = 0
        while (true) {
            try {
                val opened = connector.connect(target)
                // No suspension between receiving the session and remembering it, so stop() can always close it.
                session = opened
                return Outcome.Connected(opened)
            } catch (e: ConnectionException) {
                tries++
                if (e.failure == ConnectionFailure.Busy && retryWhenBusy && tries < config.busyRetryAttempts) {
                    mutableStatus.value = ConnectionStatus.Busy(target)
                    delay(config.busyRetryIntervalMillis)
                    continue
                }
                return Outcome.Failed(target, e.failure)
            }
        }
    }

    private companion object {
        /** Failures that mean "nothing useful at this address", so the host may have moved. */
        val SEARCHABLE_FAILURES = setOf(
            ConnectionFailure.Unreachable,
            ConnectionFailure.Timeout,
            ConnectionFailure.HostClosed,
            ConnectionFailure.ProtocolError,
        )
    }
}
