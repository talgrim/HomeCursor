package com.homecursor.client.protocol

import java.io.InputStream
import java.io.OutputStream

/** TCP framing: a UInt16 little-endian length followed by that many bytes of an encoded message. */
object MessageFraming {
    private const val HEADER_LENGTH = 2

    fun write(stream: OutputStream, message: ProtocolMessage) {
        val payload = MessageCodec.encode(message)
        require(payload.size <= ProtocolConstants.MAX_MESSAGE_BYTES) {
            "Message is longer than ${ProtocolConstants.MAX_MESSAGE_BYTES} bytes."
        }
        val frame = ByteArray(HEADER_LENGTH + payload.size)
        frame[0] = (payload.size and 0xFF).toByte()
        frame[1] = ((payload.size ushr 8) and 0xFF).toByte()
        payload.copyInto(frame, HEADER_LENGTH)
        stream.write(frame)
        stream.flush()
    }

    /**
     * Reads one message, blocking. Returns null when the stream ended cleanly between two frames.
     * @throws ProtocolException the frame is malformed or the stream ended inside a frame.
     */
    fun read(stream: InputStream): ProtocolMessage? {
        val header = ByteArray(HEADER_LENGTH)
        if (!tryReadExactly(stream, header)) return null

        val length = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8)
        if (length == 0 || length > ProtocolConstants.MAX_MESSAGE_BYTES) {
            throw ProtocolException("Invalid frame length $length.")
        }

        val payload = ByteArray(length)
        if (!tryReadExactly(stream, payload)) throw ProtocolException("Stream ended inside a frame.")
        return MessageCodec.decode(payload, 0, length)
    }

    private fun tryReadExactly(stream: InputStream, buffer: ByteArray): Boolean {
        var total = 0
        while (total < buffer.size) {
            val read = stream.read(buffer, total, buffer.size - total)
            if (read < 0) {
                if (total == 0) return false
                throw ProtocolException("Stream ended inside a frame.")
            }
            total += read
        }
        return true
    }
}
