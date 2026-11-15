package com.homecursor.client.protocol

import com.homecursor.client.protocol.ProtocolMessage.DiscoveryProbe
import com.homecursor.client.protocol.ProtocolMessage.DiscoveryReply
import com.homecursor.client.protocol.ProtocolMessage.Hello
import com.homecursor.client.protocol.ProtocolMessage.Key
import com.homecursor.client.protocol.ProtocolMessage.MouseButtonChange
import com.homecursor.client.protocol.ProtocolMessage.MouseMove
import com.homecursor.client.protocol.ProtocolMessage.Ping
import com.homecursor.client.protocol.ProtocolMessage.Pong
import com.homecursor.client.protocol.ProtocolMessage.Reject
import com.homecursor.client.protocol.ProtocolMessage.Scroll
import com.homecursor.client.protocol.ProtocolMessage.Text
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
    fun mouseMove_encodesToSpecBytes() {
        assertArrayEquals(bytes(0x10, 0x05, 0x00, 0xFB, 0xFF), MessageCodec.encode(MouseMove(5, -5)))
    }

    @Test
    fun mouseMove_decodesFromSpecBytes() {
        assertEquals(MouseMove(-2, 0x0102), MessageCodec.decode(bytes(0x10, 0xFE, 0xFF, 0x02, 0x01)))
    }

    @Test
    fun mouseMove_int16Limits_roundTrip() {
        for (message in listOf(MouseMove(32767, -32768), MouseMove(-32768, 32767), MouseMove(0, 0))) {
            assertEquals(message, MessageCodec.decode(MessageCodec.encode(message)))
        }
    }

    @Test
    fun mouseMove_int16Limits_encodeToSpecBytes() {
        assertArrayEquals(bytes(0x10, 0xFF, 0x7F, 0x00, 0x80), MessageCodec.encode(MouseMove(32767, -32768)))
    }

    @Test
    fun encode_mouseMoveOutOfInt16Range_throws() {
        assertThrows(IllegalArgumentException::class.java) { MessageCodec.encode(MouseMove(32768, 0)) }
        assertThrows(IllegalArgumentException::class.java) { MessageCodec.encode(MouseMove(0, -32769)) }
    }

    @Test
    fun mouseButton_encodesToSpecBytes() {
        assertArrayEquals(bytes(0x11, 0x01, 0x01), MessageCodec.encode(MouseButtonChange(MouseButton.Left, true)))
        assertArrayEquals(bytes(0x11, 0x02, 0x00), MessageCodec.encode(MouseButtonChange(MouseButton.Right, false)))
        assertArrayEquals(bytes(0x11, 0x03, 0x01), MessageCodec.encode(MouseButtonChange(MouseButton.Middle, true)))
    }

    @Test
    fun mouseButton_decodesFromSpecBytes() {
        assertEquals(MouseButtonChange(MouseButton.Right, true), MessageCodec.decode(bytes(0x11, 0x02, 0x01)))
    }

    @Test
    fun scroll_encodesToSpecBytes() {
        assertArrayEquals(bytes(0x12, 0x78, 0x00, 0x88, 0xFF), MessageCodec.encode(Scroll(120, -120)))
    }

    @Test
    fun scroll_decodesFromSpecBytes() {
        assertEquals(Scroll(-1, 240), MessageCodec.decode(bytes(0x12, 0xFF, 0xFF, 0xF0, 0x00)))
    }

    @Test
    fun encode_scrollOutOfInt16Range_throws() {
        assertThrows(IllegalArgumentException::class.java) { MessageCodec.encode(Scroll(40000, 0)) }
    }

    @Test
    fun allMessages_roundTrip() {
        val messages = listOf(
            Hello(1, "Pixel ü 漢字"),
            Welcome(1, 7, 47801, 5),
            Reject(RejectReason.Busy),
            Ping,
            Pong,
            MouseMove(-300, 450),
            MouseButtonChange(MouseButton.Left, true),
            MouseButtonChange(MouseButton.Middle, false),
            Scroll(-120, 360),
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
    fun decode_invalidMouseButton_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x11, 0x00, 0x01)) }
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x11, 0x04, 0x01)) }
    }

    @Test
    fun decode_truncatedMessage_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x02, 0x01, 0x04)) }
    }

    @Test
    fun decode_truncatedInt16_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x10, 0x05, 0x00, 0xFB)) }
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x12, 0x78)) }
    }

    @Test
    fun decode_truncatedMouseButton_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x11, 0x01)) }
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

    @Test
    fun key_encodesToSpecBytes() {
        assertArrayEquals(bytes(0x13, 0x11, 0x01), MessageCodec.encode(Key(SpecialKey.Alt, true)))
        assertArrayEquals(bytes(0x13, 0x01, 0x00), MessageCodec.encode(Key(SpecialKey.Enter, false)))
        assertArrayEquals(bytes(0x13, 0x43, 0x01), MessageCodec.encode(Key(SpecialKey.D, true)))
    }

    @Test
    fun key_decodesFromSpecBytes() {
        assertEquals(Key(SpecialKey.Space, true), MessageCodec.decode(bytes(0x13, 0x13, 0x01)))
    }

    @Test
    fun key_everyKey_roundTrips() {
        for (key in SpecialKey.entries) {
            val message = Key(key, isDown = true)
            assertEquals(message, MessageCodec.decode(MessageCodec.encode(message)))
        }
    }

    @Test
    fun decode_keyWithUnknownCode_throws() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x13, 0x63 + 40, 0x01)) }
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(bytes(0x13, 0x00, 0x01)) }
    }

    @Test
    fun text_encodesToSpecBytes() {
        assertArrayEquals(bytes(0x14, 0x03, 0x00, 0x61, 0xC5, 0xBC), MessageCodec.encode(Text("a\u017C")))
    }

    @Test
    fun text_withEmoji_roundTrips() {
        val message = Text("Za\u017C\u00F3\u0142\u0107 \uD83D\uDE42")
        assertEquals(message, MessageCodec.decode(MessageCodec.encode(message)))
    }

    @Test
    fun encode_textLongerThanLimit_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            MessageCodec.encode(Text("a".repeat(ProtocolConstants.MAX_STRING_BYTES + 1)))
        }
    }
}
