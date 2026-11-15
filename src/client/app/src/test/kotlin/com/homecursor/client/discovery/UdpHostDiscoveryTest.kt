package com.homecursor.client.discovery

import com.homecursor.client.protocol.DiscoveryPacket
import com.homecursor.client.protocol.ProtocolMessage
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpHostDiscoveryTest {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val hostSocket = DatagramSocket(0, loopback)
    private val probesReceived = java.util.concurrent.atomic.AtomicInteger()

    @After
    fun tearDown() = hostSocket.close()

    private fun discovery(window: Long = 400) = UdpHostDiscovery(
        targets = object : BroadcastTargets {
            override fun targets() = listOf(loopback)
        },
        discoveryPort = hostSocket.localPort,
        windowMillis = window,
        probeOffsetsMillis = listOf(0, 100),
    )

    /** Fake host: answers every datagram it receives with each of [replies]. */
    private fun startHost(vararg replies: ByteArray) = thread(isDaemon = true) {
        val buffer = ByteArray(1024)
        try {
            while (true) {
                val probe = DatagramPacket(buffer, buffer.size)
                hostSocket.receive(probe)
                if (DiscoveryPacket.tryDecode(probe.data, 0, probe.length) is ProtocolMessage.DiscoveryProbe) {
                    probesReceived.incrementAndGet()
                }
                for (reply in replies) {
                    hostSocket.send(DatagramPacket(reply, reply.size, probe.address, probe.port))
                }
            }
        } catch (_: java.net.SocketException) {
            // socket closed by the test
        }
    }

    private fun reply(name: String = "Desktop", version: Int = 1, tcp: Int = 47800, busy: Boolean = false) =
        DiscoveryPacket.encode(ProtocolMessage.DiscoveryReply(version, name, tcp, 47801, busy))

    @Test
    fun discover_hostAnswers_emitsHostWithReplyDataAndSourceAddress() = runBlocking {
        startHost(reply(name = "Desktop", tcp = 50000, busy = true))

        val hosts = discovery().discover().toList()

        assertEquals(listOf(DiscoveredHost("127.0.0.1", "Desktop", 50000, 47801, true)), hosts)
    }

    @Test
    fun discover_probeSentSeveralTimes_hostEmittedOnce() = runBlocking {
        startHost(reply())

        val hosts = discovery().discover().toList()

        assertEquals(1, hosts.size)
        assertTrue("probe should be repeated", probesReceived.get() >= 2)
    }

    @Test
    fun discover_garbageWrongVersionAndProbeReplies_areIgnored() = runBlocking {
        startHost(
            byteArrayOf(1, 2, 3),
            reply(version = 2),
            reply(tcp = 0),
            DiscoveryPacket.encode(ProtocolMessage.DiscoveryProbe(1)),
            reply(name = "Valid"),
        )

        val hosts = discovery().discover().toList()

        assertEquals(listOf("Valid"), hosts.map { it.name })
    }

    @Test
    fun discover_noHost_completesWithoutHosts() = runBlocking {
        val hosts = discovery(window = 250).discover().toList()

        assertTrue(hosts.isEmpty())
    }

    @Test
    fun discover_completesAfterWindow() = runBlocking {
        val start = System.nanoTime()
        discovery(window = 300).discover().toList()
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        assertTrue("took $elapsedMillis ms", elapsedMillis in 300..1500)
    }
}
