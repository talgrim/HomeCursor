package com.homecursor.client.discovery

import java.net.InetAddress
import java.net.NetworkInterface

/** The addresses a discovery probe is broadcast to. */
interface BroadcastTargets {
    fun targets(): List<InetAddress>
}

/**
 * The limited broadcast address plus the directed broadcast address of every active IPv4 interface.
 * The limited broadcast alone often leaves through one interface only.
 */
class NetworkInterfaceBroadcastTargets : BroadcastTargets {
    override fun targets(): List<InetAddress> {
        val directed = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
        return (listOf(InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))) + directed).distinct()
    }
}
