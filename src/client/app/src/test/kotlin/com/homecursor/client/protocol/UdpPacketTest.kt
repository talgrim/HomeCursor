package com.homecursor.client.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UdpPacketTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun encode_mouseMove_writesSessionIdSequenceAndMessage() {
        val packet = UdpPacket.encode(0x01020304L, 5, ProtocolMessage.MouseMove(5, -5))
        assertArrayEquals(
            bytes(0x04, 0x03, 0x02, 0x01, 0x05, 0x00, 0x00, 0x00, 0x10, 0x05, 0x00, 0xFB, 0xFF),
            packet,
        )
    }

    @Test
    fun encode_scroll_writesSessionIdSequenceAndMessage() {
        val packet = UdpPacket.encode(1, 2, ProtocolMessage.Scroll(0, -120))
        assertArrayEquals(
            bytes(0x01, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x12, 0x00, 0x00, 0x88, 0xFF),
            packet,
        )
    }

    @Test
    fun encode_headerIsEightBytes() {
        val packet = UdpPacket.encode(1, 1, ProtocolMessage.MouseMove(1, 1))
        assertEquals(UdpPacket.HEADER_LENGTH + MessageCodec.encode(ProtocolMessage.MouseMove(1, 1)).size, packet.size)
    }

    @Test
    fun encode_maxUInt32Values_areLittleEndian() {
        val packet = UdpPacket.encode(0xFFFF_FFFFL, 0xFFFF_FFFEL, ProtocolMessage.MouseMove(0, 0))
        assertArrayEquals(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFE, 0xFF, 0xFF, 0xFF), packet.copyOfRange(0, 8))
    }

    @Test
    fun encode_bodyDecodesWithMessageCodec() {
        val message = ProtocolMessage.Scroll(240, -360)
        val packet = UdpPacket.encode(7, 9, message)
        assertEquals(message, MessageCodec.decode(packet, UdpPacket.HEADER_LENGTH))
    }

    @Test
    fun encode_nonPointerMessage_throws() {
        assertThrows(IllegalArgumentException::class.java) { UdpPacket.encode(1, 1, ProtocolMessage.Ping) }
        assertThrows(IllegalArgumentException::class.java) {
            UdpPacket.encode(1, 1, ProtocolMessage.MouseButtonChange(MouseButton.Left, true))
        }
    }

    @Test
    fun encode_idOutsideUInt32_throws() {
        assertThrows(IllegalArgumentException::class.java) { UdpPacket.encode(-1, 0, ProtocolMessage.MouseMove(0, 0)) }
        assertThrows(IllegalArgumentException::class.java) {
            UdpPacket.encode(0, 0x1_0000_0000L, ProtocolMessage.MouseMove(0, 0))
        }
    }
}
