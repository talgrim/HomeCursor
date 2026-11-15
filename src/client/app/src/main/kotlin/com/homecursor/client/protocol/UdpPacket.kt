package com.homecursor.client.protocol

/**
 * UDP pointer datagram: a UInt32 session id and a UInt32 sequence number (both little-endian) followed by an
 * encoded [ProtocolMessage.MouseMove] or [ProtocolMessage.Scroll]. Mirrors `HomeCursor.Protocol.UdpPacket`.
 *
 * The host drops a datagram whose sequence is not newer than every one it already accepted, so the sender must
 * increase [sequence] by one per datagram (it wraps at 2^32).
 */
object UdpPacket {
    const val HEADER_LENGTH = 8

    /** @throws IllegalArgumentException [message] is not a pointer message, or an id is out of the UInt32 range. */
    fun encode(sessionId: Long, sequence: Long, message: ProtocolMessage): ByteArray {
        require(message is ProtocolMessage.MouseMove || message is ProtocolMessage.Scroll) {
            "Only MouseMove and Scroll can be sent in a UDP packet."
        }
        val writer = WireWriter()
        writer.writeUInt32(sessionId)
        writer.writeUInt32(sequence)
        return writer.toByteArray() + MessageCodec.encode(message)
    }
}
