package com.homecursor.client.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Writes the little-endian field types of the wire format. */
internal class WireWriter {
    private val out = ByteArrayOutputStream()

    fun writeByte(value: Int) {
        require(value in 0..0xFF) { "Value $value does not fit in a byte." }
        out.write(value)
    }

    fun writeUInt16(value: Int) {
        require(value in 0..0xFFFF) { "Value $value does not fit in a UInt16." }
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    fun writeUInt32(value: Long) {
        require(value in 0..0xFFFF_FFFFL) { "Value $value does not fit in a UInt32." }
        for (shift in 0 until 32 step 8) out.write(((value ushr shift) and 0xFF).toInt())
    }

    fun writeBool(value: Boolean) = writeByte(if (value) 1 else 0)

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= ProtocolConstants.MAX_STRING_BYTES) {
            "String is longer than ${ProtocolConstants.MAX_STRING_BYTES} UTF-8 bytes."
        }
        writeUInt16(bytes.size)
        out.write(bytes)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Reads the little-endian field types of the wire format. A cut-off field throws [ProtocolException]. */
internal class WireReader(private val buffer: ByteArray, offset: Int, private val end: Int) {
    private var position = offset

    fun readByte(): Int {
        ensure(1)
        return buffer[position++].toInt() and 0xFF
    }

    fun readUInt16(): Int {
        ensure(2)
        val value = (buffer[position].toInt() and 0xFF) or ((buffer[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return value
    }

    fun readUInt32(): Long {
        ensure(4)
        var value = 0L
        for (i in 0 until 4) value = value or ((buffer[position + i].toLong() and 0xFF) shl (8 * i))
        position += 4
        return value
    }

    fun readBool(): Boolean = readByte() != 0

    fun readString(): String {
        val length = readUInt16()
        if (length > ProtocolConstants.MAX_STRING_BYTES) {
            throw ProtocolException("String is longer than ${ProtocolConstants.MAX_STRING_BYTES} UTF-8 bytes.")
        }
        ensure(length)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            val text = decoder.decode(ByteBuffer.wrap(buffer, position, length)).toString()
            position += length
            return text
        } catch (e: CharacterCodingException) {
            throw ProtocolException("String is not valid UTF-8.", e)
        }
    }

    private fun ensure(count: Int) {
        if (end - position < count) throw ProtocolException("Message is truncated.")
    }
}
