package com.homecursor.client.protocol

import com.homecursor.client.protocol.ProtocolMessage.DiscoveryProbe
import com.homecursor.client.protocol.ProtocolMessage.DiscoveryReply
import com.homecursor.client.protocol.ProtocolMessage.Hello
import com.homecursor.client.protocol.ProtocolMessage.Ping
import com.homecursor.client.protocol.ProtocolMessage.Pong
import com.homecursor.client.protocol.ProtocolMessage.Reject
import com.homecursor.client.protocol.ProtocolMessage.Welcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageCodecTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun hello_encodesToSpecBytes() {
        assertArrayEquals(bytes(0x01, 0x01, 0x02, 0x00, 0x61, 0x62), MessageCodec.encode(Hello(1, "ab")))
    }

    @Test
    fun welcome_decodesFromSpecBytes() {
        val decoded = MessageCodec.decode(bytes(0x02, 0x01, 0x04, 0x03, 0x02, 0x01, 0xB9, 0xBA, 0x05))
        assertEquals(Welcome(1, 0x01020304L, 47801, 5), decoded)
    }

    @Test
    fun welcome_sessionIdAboveInt32_roundTrips() {
        val message = Welcome(1, 0xFFFF_FFFFL, 47801, 5)
        assertEquals(message, MessageCodec.decode(MessageCodec.encode(message)))
    }

    @Test
    fun reject_decodesBothReasons() {
        assertEquals(Reject(RejectReason.Busy), MessageCodec.decode(bytes(0x03, 0x01)))
        assertEquals(Reject(RejectReason.UnsupportedVersion), MessageCodec.decode(bytes(0x03, 0x02)))
    }

    @Test
    fun pingAndPong_areSingleByte() {
        assertArrayEquals(bytes(0x04), MessageCodec.encode(Ping))
        assertEquals(Pong, MessageCodec.decode(bytes(0x05)))
    }

    @Test
    fun discoveryProbe_encodesToSpecBytes() {
        assertArrayEquals(bytes(0x20, 0x01), MessageCodec.encode(DiscoveryProbe(1)))
    }

    @Test
    fun discoveryReply_decodesFromSpecBytes() {
        val decoded = MessageCodec.decode(bytes(0x21, 0x01, 0x02, 0x00, 0x50, 0x43, 0xB8, 0xBA, 0xB9, 0xBA, 0x01))
        assertEquals(DiscoveryReply(1, "PC", 47800, 47801, true), decoded)
    }

    @Test
    fun allMessages_roundTrip() {
        val messages = listOf(
            Hello(1, "Pixel ü 漢字"),
            Welcome(1, 7, 47801, 5),
            Reject(RejectReason.Busy),
            Ping,
            Pong,
            DiscoveryProbe(1),
            DiscoveryReply(1, "Desktop", 47800, 47801, false),
        )
        for (message in messages) assertEquals(message, MessageCodec.decode(MessageCodec.encode(message)))
    }

    @Test
    fun decode_honoursOffsetAndCount() {
        val buffer = bytes(0xFF, 0xFF, 0x20, 0x01, 0xFF)
        assertEquals(DiscoveryProbe(1), MessageCodec.decode(buffer, 2, 2))
    }

    @Test
    fun decode_emptyMessage_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(ByteArray(0)) }
    }

    @Test
    fun decode_unknownType_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x63)) }
    }

    @Test
    fun decode_invalidRejectReason_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x03, 0x09)) }
    }

    @Test
    fun decode_truncatedMessage_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x02, 0x01, 0x04)) }
    }

    @Test
    fun decode_stringLongerThanBuffer_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x01, 0x01, 0x05, 0x00, 0x61)) }
    }

    @Test
    fun decode_stringOverLimit_throws() {
        // length 2049 = 0x0801
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x01, 0x01, 0x01, 0x08)) }
    }

    @Test
    fun decode_invalidUtf8_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x01, 0x01, 0x01, 0x00, 0xFF)) }
    }

    @Test
    fun decode_messageOverLimit_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(ByteArray(8193)) }
    }

    @Test
    fun encode_stringOverLimit_throws() {
        assertThrows(IllegalArgumentException::class.java) { MessageCodec.encode(Hello(1, "x".repeat(2049))) }
    }
}
