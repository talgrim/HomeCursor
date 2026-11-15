package com.homecursor.client.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageFramingTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun write_prefixesLittleEndianLength() {
        val out = ByteArrayOutputStream()
        MessageFraming.write(out, ProtocolMessage.Hello(1, "ab"))
        assertArrayEquals(bytes(0x06, 0x00, 0x01, 0x01, 0x02, 0x00, 0x61, 0x62), out.toByteArray())
    }

    @Test
    fun read_returnsConsecutiveFrames() {
        val stream = ByteArrayInputStream(bytes(0x01, 0x00, 0x04, 0x01, 0x00, 0x05))
        assertEquals(ProtocolMessage.Ping, MessageFraming.read(stream))
        assertEquals(ProtocolMessage.Pong, MessageFraming.read(stream))
        assertNull(MessageFraming.read(stream))
    }

    @Test
    fun read_emptyStream_returnsNull() {
        assertNull(MessageFraming.read(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun read_zeroLength_throws() {
        assertThrows(ProtocolException::class.java) { MessageFraming.read(ByteArrayInputStream(bytes(0x00, 0x00))) }
    }

    @Test
    fun read_lengthOverLimit_throws() {
        // 8193 = 0x2001
        assertThrows(ProtocolException::class.java) { MessageFraming.read(ByteArrayInputStream(bytes(0x01, 0x20))) }
    }

    @Test
    fun read_endInsideHeader_throws() {
        assertThrows(ProtocolException::class.java) { MessageFraming.read(ByteArrayInputStream(bytes(0x01))) }
    }

    @Test
    fun read_endInsidePayload_throws() {
        assertThrows(ProtocolException::class.java) { MessageFraming.read(ByteArrayInputStream(bytes(0x05, 0x00, 0x02))) }
    }
}
