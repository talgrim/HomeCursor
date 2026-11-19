package com.homecursor.client.discovery

/** A host that answered a discovery probe. [address] is the IPv4 address the reply came from. */
data class DiscoveredHost(
    val address: String,
    val name: String,
    val tcpPort: Int,
    val udpPort: Int,
    val isBusy: Boolean,
)
