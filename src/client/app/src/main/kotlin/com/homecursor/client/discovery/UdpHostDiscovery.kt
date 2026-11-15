package com.homecursor.client.discovery

import com.homecursor.client.protocol.DiscoveryPacket
import com.homecursor.client.protocol.ProtocolConstants
import com.homecursor.client.protocol.ProtocolMessage
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.coroutineContext

/**
 * Discovery by UDP broadcast: sends a probe to every target (repeated, because UDP can lose datagrams)
 * and collects the unicast replies for [windowMillis].
 */
class UdpHostDiscovery(
    private val targets: BroadcastTargets,
    private val discoveryPort: Int = ProtocolConstants.DEFAULT_DISCOVERY_PORT,
    private val windowMillis: Long = 1_500,
    private val probeOffsetsMillis: List<Long> = listOf(0, 300, 700),
) : HostDiscovery {

    override fun discover(): Flow<DiscoveredHost> = flow {
        val probe = DiscoveryPacket.encode(ProtocolMessage.DiscoveryProbe(ProtocolConstants.VERSION))
        val destinations = targets.targets()
        val seen = HashSet<String>()
        val buffer = ByteArray(ProtocolConstants.MAX_MESSAGE_BYTES)

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = POLL_MILLIS
            val start = System.nanoTime()
            var nextProbe = 0

            while (elapsedMillis(start) < windowMillis) {
                coroutineContext.ensureActive()

                if (nextProbe < probeOffsetsMillis.size && elapsedMillis(start) >= probeOffsetsMillis[nextProbe]) {
                    nextProbe++
                    for (destination in destinations) {
                        try {
                            socket.send(DatagramPacket(probe, probe.size, destination, discoveryPort))
                        } catch (_: IOException) {
                            // This interface cannot send right now; the others still can.
                        }
                    }
                }

                val datagram = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(datagram)
                } catch (_: SocketTimeoutException) {
                    continue
                }

                val reply = DiscoveryPacket.tryDecode(datagram.data, 0, datagram.length)
                    as? ProtocolMessage.DiscoveryReply ?: continue
                if (reply.protocolVersion != ProtocolConstants.VERSION || reply.tcpPort == 0) continue

                val address = datagram.address.hostAddress ?: continue
                if (seen.add(address)) {
                    emit(DiscoveredHost(address, reply.hostName, reply.tcpPort, reply.udpPort, reply.isBusy))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun elapsedMillis(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000

    private companion object {
        const val POLL_MILLIS = 50
    }
}
