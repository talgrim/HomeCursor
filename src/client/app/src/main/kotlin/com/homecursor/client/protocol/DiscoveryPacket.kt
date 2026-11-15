package com.homecursor.client.protocol

/** UDP discovery datagram: the ASCII magic "HCUR" followed by an encoded discovery message. */
object DiscoveryPacket {
    private val magic = ProtocolConstants.DISCOVERY_MAGIC

    fun encode(message: ProtocolMessage): ByteArray {
        require(message is ProtocolMessage.DiscoveryProbe || message is ProtocolMessage.DiscoveryReply) {
            "Only discovery messages can be sent in a discovery packet."
        }
        return magic + MessageCodec.encode(message)
    }

    /** Returns the discovery message, or null when the datagram is not a valid discovery packet. */
    fun tryDecode(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): ProtocolMessage? {
        if (length <= magic.size) return null
        for (i in magic.indices) if (buffer[offset + i] != magic[i]) return null
        return try {
            MessageCodec.decode(buffer, offset + magic.size, length - magic.size)
                .takeIf { it is ProtocolMessage.DiscoveryProbe || it is ProtocolMessage.DiscoveryReply }
        } catch (_: ProtocolException) {
            null
        }
    }
}
