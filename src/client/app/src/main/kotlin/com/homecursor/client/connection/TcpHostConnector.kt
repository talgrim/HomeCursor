package com.homecursor.client.connection

import com.homecursor.client.protocol.MessageFraming
import com.homecursor.client.protocol.ProtocolConstants
import com.homecursor.client.protocol.ProtocolException
import com.homecursor.client.protocol.ProtocolMessage
import com.homecursor.client.protocol.RejectReason
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

/**
 * Connects over TCP: sends Hello and expects Welcome (or Reject). The returned session pings the host
 * and treats it as dead after [silenceTimeoutIntervals] ping intervals without any message from it
 * (3 intervals = 15 s with the default ping interval, which matches the host's own heartbeat timeout).
 */
class TcpHostConnector(
    private val clientName: String,
    private val connectTimeoutMillis: Int = 3_000,
    private val handshakeTimeoutMillis: Int = 3_000,
    private val silenceTimeoutIntervals: Int = 3,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HostConnector {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun connect(endpoint: HostEndpoint): HostSession = suspendCancellableCoroutine { continuation ->
        // The attempt runs in its own job rather than in withContext: if the caller is cancelled just after
        // the handshake, withContext would drop the session and leave the host's single slot taken.
        val attempt = CoroutineScope(dispatcher).launch {
            try {
                val session = open(endpoint)
                continuation.resume(session) { _, lateSession, _ -> lateSession.close() }
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
            }
        }
        continuation.invokeOnCancellation { attempt.cancel() }
    }

    private suspend fun open(endpoint: HostEndpoint): HostSession {
        val socket = Socket()
        try {
            val welcome = interruptibly(socket) { handshake(socket, endpoint) }
            return TcpHostSession(socket, endpoint, welcome, silenceTimeoutIntervals, dispatcher).also { it.start() }
        } catch (e: Throwable) {
            socket.close()
            // If we were cancelled, the failure is just the closed socket; report the cancellation instead.
            currentCoroutineContext().ensureActive()
            throw e.toConnectionException()
        }
    }

    private fun handshake(socket: Socket, endpoint: HostEndpoint): ProtocolMessage.Welcome {
        socket.connect(InetSocketAddress(endpoint.address, endpoint.tcpPort), connectTimeoutMillis)
        socket.tcpNoDelay = true
        socket.soTimeout = handshakeTimeoutMillis

        MessageFraming.write(socket.getOutputStream(), ProtocolMessage.Hello(ProtocolConstants.VERSION, clientName))

        return when (val reply = MessageFraming.read(socket.getInputStream())) {
            is ProtocolMessage.Welcome ->
                if (reply.protocolVersion == ProtocolConstants.VERSION) reply
                else throw ConnectionException(ConnectionFailure.UnsupportedVersion)
            is ProtocolMessage.Reject -> throw ConnectionException(
                if (reply.reason == RejectReason.Busy) ConnectionFailure.Busy else ConnectionFailure.UnsupportedVersion,
            )
            null -> throw ConnectionException(ConnectionFailure.HostClosed)
            else -> throw ConnectionException(ConnectionFailure.ProtocolError)
        }
    }

    private fun Throwable.toConnectionException(): Throwable = when (this) {
        is ConnectionException, is CancellationException -> this
        is SocketTimeoutException -> ConnectionException(ConnectionFailure.Timeout, this)
        is ProtocolException -> ConnectionException(ConnectionFailure.ProtocolError, this)
        is IOException, is IllegalArgumentException -> ConnectionException(ConnectionFailure.Unreachable, this)
        else -> this
    }
}

/**
 * Runs blocking socket [block]. Blocking socket calls ignore coroutine cancellation, so the socket is
 * closed when the caller is cancelled, which makes the blocked call fail at once.
 */
private suspend fun <T> interruptibly(socket: Socket, block: () -> T): T = coroutineScope {
    val finished = AtomicBoolean(false)
    // Unconfined: the cleanup must run on the cancelling thread, even if no other thread is free.
    val watcher = launch(Dispatchers.Unconfined) {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) socket.close()
        }
    }
    try {
        block()
    } finally {
        finished.set(true)
        watcher.cancel()
    }
}

internal class TcpHostSession(
    private val socket: Socket,
    override val endpoint: HostEndpoint,
    welcome: ProtocolMessage.Welcome,
    silenceTimeoutIntervals: Int,
    dispatcher: CoroutineDispatcher,
) : HostSession {
    override val sessionId: Long = welcome.sessionId

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Active)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val pingIntervalMillis = maxOf(1, welcome.pingIntervalSeconds) * 1_000L
    private val silenceTimeoutMillis = pingIntervalMillis * silenceTimeoutIntervals
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val writeLock = Any()

    fun start() {
        socket.soTimeout = silenceTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        scope.launch { readLoop() }
        scope.launch { pingLoop() }
    }

    override fun close() = finish(null)

    private fun readLoop() {
        val failure = try {
            val input = socket.getInputStream()
            while (true) {
                val message = MessageFraming.read(input) ?: break
                // Pong needs no handling: any message from the host proves it is alive.
                if (message is ProtocolMessage.Reject) break
            }
            ConnectionFailure.HostClosed
        } catch (_: SocketTimeoutException) {
            ConnectionFailure.Timeout
        } catch (_: ProtocolException) {
            ConnectionFailure.ProtocolError
        } catch (_: IOException) {
            ConnectionFailure.HostClosed
        }
        finish(failure)
    }

    private suspend fun pingLoop() {
        val output = socket.getOutputStream()
        while (scope.isActive) {
            delay(pingIntervalMillis)
            try {
                synchronized(writeLock) { MessageFraming.write(output, ProtocolMessage.Ping) }
            } catch (_: IOException) {
                finish(ConnectionFailure.HostClosed)
                return
            }
        }
    }

    private fun finish(failure: ConnectionFailure?) {
        mutableState.update { if (it is SessionState.Closed) it else SessionState.Closed(failure) }
        scope.cancel()
        try {
            socket.close()
        } catch (_: IOException) {
            // nothing left to release
        }
    }
}
