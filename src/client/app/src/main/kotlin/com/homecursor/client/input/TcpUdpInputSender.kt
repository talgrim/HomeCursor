package com.homecursor.client.input

import com.homecursor.client.connection.HostSession
import com.homecursor.client.connection.SessionState
import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.protocol.ProtocolMessage
import com.homecursor.client.protocol.UdpPacket
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sends pointer movement and scrolling as UDP datagrams (high rate, loss is harmless) and button changes over the
 * session's TCP connection (must not be lost), as `docs/protocol.md` recommends.
 *
 * Calls only enqueue; one consumer coroutine on [dispatcher] delivers the messages in order, so a button change
 * never overtakes the movement queued before it and the caller (the UI thread) never blocks on a socket. The
 * queue is unbounded on purpose: dropping a button release would leave the button stuck, and the producer is a
 * fixed-rate ticker, so the queue only grows while the TCP write stalls, which ends the session within seconds.
 *
 * The sender closes itself, and its datagram socket, when the [session] ends. Calls after that are ignored.
 * Safe to call from any thread.
 */
class TcpUdpInputSender(
    private val session: HostSession,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socketFactory: () -> DatagramSocket = { DatagramSocket() },
) : InputSender {
    private val queue = Channel<ProtocolMessage>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launch { deliverQueued() }
        scope.launch {
            session.state.first { it is SessionState.Closed }
            close()
        }
    }

    override fun sendMove(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        queue.trySend(ProtocolMessage.MouseMove(dx.coerceIn(INT16_RANGE), dy.coerceIn(INT16_RANGE)))
    }

    override fun sendScroll(horizontal: Int, vertical: Int) {
        if (horizontal == 0 && vertical == 0) return
        queue.trySend(ProtocolMessage.Scroll(horizontal.coerceIn(INT16_RANGE), vertical.coerceIn(INT16_RANGE)))
    }

    override fun sendButton(button: MouseButton, isDown: Boolean) {
        queue.trySend(ProtocolMessage.MouseButtonChange(button, isDown))
    }

    /** Stops delivering. Messages still queued are dropped; the host releases held buttons when the session ends. */
    fun close() {
        queue.close()
        scope.cancel()
    }

    private suspend fun deliverQueued() {
        try {
            socketFactory().use { socket -> deliverVia(socket) }
        } catch (_: IOException) {
            // No datagram socket: pointer input cannot be sent. The session state still reports a dead connection.
            queue.close()
        }
    }

    private suspend fun deliverVia(socket: DatagramSocket) {
        // Resolved once, here on the IO dispatcher, because resolving a host name can block.
        val target = InetSocketAddress(session.endpoint.address, session.udpPort).takeUnless { it.isUnresolved }
        var sequence = 0L
        // Cancelling the scope ends this loop and closes the socket through `use`.
        for (message in queue) {
            when (message) {
                is ProtocolMessage.MouseMove, is ProtocolMessage.Scroll -> {
                    if (target != null && sendDatagram(socket, target, sequence, message)) {
                        sequence = (sequence + 1) and UINT32_MASK
                    }
                }
                else -> session.send(message)
            }
        }
    }

    /** Returns whether a datagram was handed to the network. A failure is dropped: pointer input is best effort. */
    private fun sendDatagram(
        socket: DatagramSocket,
        target: InetSocketAddress,
        sequence: Long,
        message: ProtocolMessage,
    ): Boolean = try {
        val bytes = UdpPacket.encode(session.sessionId, sequence, message)
        socket.send(DatagramPacket(bytes, bytes.size, target))
        true
    } catch (_: IOException) {
        false
    }

    private companion object {
        val INT16_RANGE = Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()
        const val UINT32_MASK = 0xFFFF_FFFFL
    }
}
