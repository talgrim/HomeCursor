package com.homecursor.client.input

import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.protocol.ProtocolMessage
import com.homecursor.client.protocol.UdpPacket
import com.homecursor.client.session.FakeSession
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TcpUdpInputSenderTest {
    /** Stands in for the host's UDP port. */
    private lateinit var host: DatagramSocket
    private lateinit var session: FakeSession
    private lateinit var sender: TcpUdpInputSender

    @Before
    fun setUp() {
        host = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        session = FakeSession(HostEndpoint("127.0.0.1", "Fake", 1, host.localPort))
        sender = TcpUdpInputSender(session)
    }

    @After
    fun tearDown() {
        sender.close()
        host.close()
    }

    /** The next datagram, or null if none arrives within [timeoutMillis]. */
    private fun receive(timeoutMillis: Int = 2_000): ByteArray? {
        host.soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(64), 64)
        return try {
            host.receive(packet)
            packet.data.copyOf(packet.length)
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    private suspend fun awaitSent(count: Int) = withTimeout(2_000) {
        while (session.sent.size < count) delay(10)
    }

    @Test
    fun sendMove_sendsDatagramWithSessionIdAndIncreasingSequence() {
        sender.sendMove(5, -5)
        sender.sendMove(1, 2)

        assertArrayEquals(UdpPacket.encode(1, 0, ProtocolMessage.MouseMove(5, -5)), receive())
        assertArrayEquals(UdpPacket.encode(1, 1, ProtocolMessage.MouseMove(1, 2)), receive())
    }

    @Test
    fun sendScroll_sendsDatagramSharingTheSequence() {
        sender.sendMove(1, 1)
        sender.sendScroll(0, -120)

        assertArrayEquals(UdpPacket.encode(1, 0, ProtocolMessage.MouseMove(1, 1)), receive())
        assertArrayEquals(UdpPacket.encode(1, 1, ProtocolMessage.Scroll(0, -120)), receive())
    }

    @Test
    fun sendMove_outsideInt16_isClamped() {
        sender.sendMove(100_000, -100_000)

        assertArrayEquals(UdpPacket.encode(1, 0, ProtocolMessage.MouseMove(32767, -32768)), receive())
    }

    @Test
    fun sendMoveAndScroll_zero_sendNothing() = runBlocking {
        sender.sendMove(0, 0)
        sender.sendScroll(0, 0)
        sender.sendButton(MouseButton.Left, true)
        awaitSent(1)

        assertNull(receive(200))
    }

    @Test
    fun sendButton_goesOverTcpOnly() = runBlocking {
        sender.sendButton(MouseButton.Right, true)
        sender.sendButton(MouseButton.Right, false)
        awaitSent(2)

        assertEquals(
            listOf(
                ProtocolMessage.MouseButtonChange(MouseButton.Right, true),
                ProtocolMessage.MouseButtonChange(MouseButton.Right, false),
            ),
            session.sent,
        )
        assertNull(receive(200))
    }

    @Test
    fun sendButtonAfterMove_movementIsSentFirst() = runBlocking {
        sender.sendMove(3, 4)
        sender.sendButton(MouseButton.Left, true)
        awaitSent(1)

        // The consumer sends datagrams before it hands the next message to TCP, so the move is already on the wire.
        assertArrayEquals(UdpPacket.encode(1, 0, ProtocolMessage.MouseMove(3, 4)), receive(200))
    }

    @Test
    fun sessionEnds_laterInputIsIgnored() = runBlocking {
        session.lose()
        delay(300)

        sender.sendMove(1, 1)
        sender.sendButton(MouseButton.Left, true)
        delay(300)

        assertTrue(session.sent.isEmpty())
        assertNull(receive(200))
    }

    @Test
    fun close_laterInputIsIgnored() = runBlocking {
        sender.close()

        sender.sendMove(1, 1)
        sender.sendButton(MouseButton.Left, true)
        delay(300)

        assertTrue(session.sent.isEmpty())
        assertNull(receive(200))
    }

    @Test
    fun noOpSender_ignoresEverything() {
        NoOpInputSender.sendMove(1, 1)
        NoOpInputSender.sendScroll(1, 1)
        NoOpInputSender.sendButton(MouseButton.Left, true)
    }
}
