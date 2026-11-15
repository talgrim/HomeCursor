package com.homecursor.client.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DiscoveryPacketTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun encode_probe_prefixesMagic() {
        assertArrayEquals(bytes(0x48, 0x43, 0x55, 0x52, 0x20, 0x01), DiscoveryPacket.encode(ProtocolMessage.DiscoveryProbe(1)))
    }

    @Test
    fun encode_nonDiscoveryMessage_throws() {
        assertThrows(IllegalArgumentException::class.java) { DiscoveryPacket.encode(ProtocolMessage.Ping) }
    }

    @Test
    fun tryDecode_reply_roundTrips() {
        val reply = ProtocolMessage.DiscoveryReply(1, "Desktop", 47800, 47801, true)
        assertEquals(reply, DiscoveryPacket.tryDecode(DiscoveryPacket.encode(reply)))
    }

    @Test
    fun tryDecode_honoursLength() {
        val packet = DiscoveryPacket.encode(ProtocolMessage.DiscoveryProbe(1))
        val padded = packet + ByteArray(100)
        assertEquals(ProtocolMessage.DiscoveryProbe(1), DiscoveryPacket.tryDecode(padded, 0, packet.size))
    }

    @Test
    fun tryDecode_wrongMagic_returnsNull() {
        assertNull(DiscoveryPacket.tryDecode(bytes(0x58, 0x43, 0x55, 0x52, 0x20, 0x01)))
    }

    @Test
    fun tryDecode_magicOnly_returnsNull() {
        assertNull(DiscoveryPacket.tryDecode(bytes(0x48, 0x43, 0x55, 0x52)))
    }

    @Test
    fun tryDecode_garbageBody_returnsNull() {
        assertNull(DiscoveryPacket.tryDecode(bytes(0x48, 0x43, 0x55, 0x52, 0x63)))
    }

    @Test
    fun tryDecode_nonDiscoveryMessage_returnsNull() {
        assertNull(DiscoveryPacket.tryDecode(bytes(0x48, 0x43, 0x55, 0x52, 0x04)))
    }
}
