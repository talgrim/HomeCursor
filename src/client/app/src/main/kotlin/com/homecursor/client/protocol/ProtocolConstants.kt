package com.homecursor.client.protocol

/** Constants of the HomeCursor wire protocol, version 1 (see `docs/protocol.md`). */
object ProtocolConstants {
    const val VERSION = 1

    const val DEFAULT_TCP_PORT = 47800
    const val DEFAULT_UDP_PORT = 47801
    const val DEFAULT_DISCOVERY_PORT = 47802

    const val MAX_STRING_BYTES = 2048
    const val MAX_MESSAGE_BYTES = 8192

    /** ASCII magic that starts every discovery datagram. */
    val DISCOVERY_MAGIC: ByteArray = "HCUR".toByteArray(Charsets.US_ASCII)
}
