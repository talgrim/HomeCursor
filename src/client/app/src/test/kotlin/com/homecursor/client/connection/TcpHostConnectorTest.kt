package com.homecursor.client.connection

import com.homecursor.client.protocol.MessageFraming
import com.homecursor.client.protocol.ProtocolMessage
import com.homecursor.client.protocol.RejectReason
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TcpHostConnectorTest {
    private val hosts = mutableListOf<FakeHost>()
    private val sessions = mutableListOf<HostSession>()

    @After
    fun tearDown() {
        sessions.forEach { it.close() }
        hosts.forEach { it.close() }
    }

    private fun host(handler: (java.net.Socket) -> Unit) = FakeHost(handler).also { hosts += it }

    private fun connector(
        handshakeTimeoutMillis: Int = 2_000,
        silenceTimeoutIntervals: Int = 3,
    ) = TcpHostConnector("Test phone", 2_000, handshakeTimeoutMillis, silenceTimeoutIntervals)

    private suspend fun TcpHostConnector.connectOk(host: FakeHost) = connect(host.endpoint).also { sessions += it }

    private suspend fun failureOf(connector: TcpHostConnector, endpoint: HostEndpoint): ConnectionFailure =
        try {
            connector.connect(endpoint).also { sessions += it }
            fail("connect should fail")
            error("unreachable")
        } catch (e: ConnectionException) {
            e.failure
        }

    @Test
    fun connect_hostWelcomes_sendsHelloAndReturnsActiveSession() = runBlocking {
        val hellos = LinkedBlockingQueue<ProtocolMessage>()
        val host = host { hellos.add(FakeHost.accept(it, FakeHost.welcome())!!); Thread.sleep(2_000) }

        val session = connector().connectOk(host)

        assertEquals(ProtocolMessage.Hello(1, "Test phone"), hellos.pollWithin(1_000))
        assertEquals(42L, session.sessionId)
        assertEquals(SessionState.Active, session.state.value)
    }

    @Test
    fun connect_hostBusy_failsWithBusy() = runBlocking {
        val host = host { FakeHost.accept(it, ProtocolMessage.Reject(RejectReason.Busy)) }
        assertEquals(ConnectionFailure.Busy, failureOf(connector(), host.endpoint))
    }

    @Test
    fun connect_hostRejectsVersion_failsWithUnsupportedVersion() = runBlocking {
        val host = host { FakeHost.accept(it, ProtocolMessage.Reject(RejectReason.UnsupportedVersion)) }
        assertEquals(ConnectionFailure.UnsupportedVersion, failureOf(connector(), host.endpoint))
    }

    @Test
    fun connect_welcomeWithOtherVersion_failsWithUnsupportedVersion() = runBlocking {
        val host = host { FakeHost.accept(it, FakeHost.welcome(version = 2)) }
        assertEquals(ConnectionFailure.UnsupportedVersion, failureOf(connector(), host.endpoint))
    }

    @Test
    fun connect_hostClosesAfterHello_failsWithHostClosed() = runBlocking {
        val host = host { FakeHost.accept(it, null); it.close() }
        assertEquals(ConnectionFailure.HostClosed, failureOf(connector(), host.endpoint))
    }

    @Test
    fun connect_hostSendsGarbage_failsWithProtocolError() = runBlocking {
        val host = host {
            FakeHost.accept(it, null)
            it.getOutputStream().write(byteArrayOf(1, 0, 99))
            Thread.sleep(1_000)
        }
        assertEquals(ConnectionFailure.ProtocolError, failureOf(connector(), host.endpoint))
    }

    @Test
    fun connect_hostNeverAnswers_failsWithTimeout() = runBlocking {
        val host = host { FakeHost.accept(it, null); Thread.sleep(2_000) }
        assertEquals(ConnectionFailure.Timeout, failureOf(connector(handshakeTimeoutMillis = 200), host.endpoint))
    }

    @Test
    fun connect_nobodyListening_failsWithUnreachable() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val endpoint = HostEndpoint("127.0.0.1", "Gone", port)
        assertEquals(ConnectionFailure.Unreachable, failureOf(connector(), endpoint))
    }

    @Test
    fun connect_cancelledWhileWaitingForWelcome_returnsPromptly() = runBlocking {
        val host = host { FakeHost.accept(it, null); Thread.sleep(5_000) }
        val attempt = async { connector(handshakeTimeoutMillis = 10_000).connect(host.endpoint) }

        delay(300)
        val start = System.nanoTime()
        attempt.cancel()
        try {
            withTimeout(2_000) { attempt.await() }
            fail("should be cancelled")
        } catch (_: CancellationException) {
            // expected
        }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1_500)
    }

    @Test
    fun session_sendsPingAtWelcomeInterval() = runBlocking {
        val pings = LinkedBlockingQueue<ProtocolMessage>()
        val host = host { socket ->
            FakeHost.accept(socket, FakeHost.welcome(pingIntervalSeconds = 1))
            while (true) {
                val message = MessageFraming.read(socket.getInputStream()) ?: break
                pings.add(message)
                MessageFraming.write(socket.getOutputStream(), ProtocolMessage.Pong)
            }
        }

        connector().connectOk(host)

        assertEquals(ProtocolMessage.Ping, pings.pollWithin(2_000))
        assertEquals(ProtocolMessage.Ping, pings.pollWithin(2_000))
    }

    @Test
    fun session_hostClosesConnection_endsWithHostClosed() = runBlocking {
        val host = host { FakeHost.accept(it, FakeHost.welcome()); Thread.sleep(200); it.close() }

        val session = connector().connectOk(host)

        val closed = withTimeout(3_000) { session.state.first { it is SessionState.Closed } }
        assertEquals(SessionState.Closed(ConnectionFailure.HostClosed), closed)
    }

    @Test
    fun session_hostGoesSilent_endsWithTimeout() = runBlocking {
        val host = host { FakeHost.accept(it, FakeHost.welcome(pingIntervalSeconds = 1)); Thread.sleep(5_000) }

        val session = connector(silenceTimeoutIntervals = 1).connectOk(host)

        val closed = withTimeout(4_000) { session.state.first { it is SessionState.Closed } }
        assertEquals(SessionState.Closed(ConnectionFailure.Timeout), closed)
    }

    @Test
    fun session_close_endsWithoutFailureAndHostSeesEndOfStream() = runBlocking {
        val hostSawEnd = LinkedBlockingQueue<Boolean>()
        val host = host { socket ->
            FakeHost.accept(socket, FakeHost.welcome())
            hostSawEnd.add(MessageFraming.read(socket.getInputStream()) == null)
        }

        val session = connector().connectOk(host)
        session.close()
        session.close()

        assertEquals(SessionState.Closed(null), session.state.value)
        assertEquals(true, hostSawEnd.pollWithin(2_000))
    }

    @Test
    fun session_closeAfterHostClosed_keepsOriginalFailure() = runBlocking {
        val host = host { FakeHost.accept(it, FakeHost.welcome()); it.close() }

        val session = connector().connectOk(host)
        withTimeout(3_000) { session.state.first { it is SessionState.Closed } }
        session.close()

        assertEquals(SessionState.Closed(ConnectionFailure.HostClosed), session.state.value)
    }
}
