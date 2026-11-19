package com.homecursor.client.ui

import com.homecursor.client.connection.HostEndpoint
import com.homecursor.client.protocol.ProtocolConstants

private val manualEntryPattern = Regex("""^([^\s:]+)(?::(\d{1,5}))?$""")

/** Parses "address" or "address:port" typed by the user. Returns null if it is not valid. */
fun parseManualEndpoint(input: String): HostEndpoint? {
    val match = manualEntryPattern.matchEntire(input.trim()) ?: return null
    val address = match.groupValues[1]
    val port = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: ProtocolConstants.DEFAULT_TCP_PORT
    if (port !in 1..65535) return null
    return HostEndpoint(address = address, name = address, tcpPort = port)
}
