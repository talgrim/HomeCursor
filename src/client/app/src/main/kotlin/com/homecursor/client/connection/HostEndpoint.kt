package com.homecursor.client.connection

import com.homecursor.client.protocol.ProtocolConstants

/** Where and how to reach a host. This is also what is remembered between app starts. */
data class HostEndpoint(
    val address: String,
    val name: String,
    val tcpPort: Int = ProtocolConstants.DEFAULT_TCP_PORT,
    val udpPort: Int = ProtocolConstants.DEFAULT_UDP_PORT,
)
