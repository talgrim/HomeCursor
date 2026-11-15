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

/**
 * Binary encoding of [ProtocolMessage]: one type byte followed by little-endian fields.
 * Mirrors `HomeCursor.Protocol.MessageCodec` on the host.
 */
object MessageCodec {
    fun encode(message: ProtocolMessage): ByteArray {
        val writer = WireWriter()
        when (message) {
            is Hello -> {
                writer.writeByte(MessageType.HELLO)
                writer.writeByte(message.protocolVersion)
                writer.writeString(message.clientName)
            }
            is Welcome -> {
                writer.writeByte(MessageType.WELCOME)
                writer.writeByte(message.protocolVersion)
                writer.writeUInt32(message.sessionId)
                writer.writeUInt16(message.udpPort)
                writer.writeByte(message.pingIntervalSeconds)
            }
            is Reject -> {
                writer.writeByte(MessageType.REJECT)
                writer.writeByte(message.reason.code)
            }
            Ping -> writer.writeByte(MessageType.PING)
            Pong -> writer.writeByte(MessageType.PONG)
            is MouseMove -> {
                writer.writeByte(MessageType.MOUSE_MOVE)
                writer.writeInt16(message.dx)
                writer.writeInt16(message.dy)
            }
            is MouseButtonChange -> {
                writer.writeByte(MessageType.MOUSE_BUTTON)
                writer.writeByte(message.button.code)
                writer.writeBool(message.isDown)
            }
            is Scroll -> {
                writer.writeByte(MessageType.SCROLL)
                writer.writeInt16(message.horizontal)
                writer.writeInt16(message.vertical)
            }
            is Key -> {
                writer.writeByte(MessageType.KEY)
                writer.writeByte(message.key.code)
                writer.writeBool(message.isDown)
            }
            is Text -> {
                writer.writeByte(MessageType.TEXT)
                writer.writeString(message.text)
            }
            is DiscoveryProbe -> {
                writer.writeByte(MessageType.DISCOVERY_PROBE)
                writer.writeByte(message.protocolVersion)
            }
            is DiscoveryReply -> {
                writer.writeByte(MessageType.DISCOVERY_REPLY)
                writer.writeByte(message.protocolVersion)
                writer.writeString(message.hostName)
                writer.writeUInt16(message.tcpPort)
                writer.writeUInt16(message.udpPort)
                writer.writeBool(message.isBusy)
            }
        }
        return writer.toByteArray()
    }

    /** @throws ProtocolException the bytes are empty, too long, truncated, of an unknown type or invalid. */
    fun decode(buffer: ByteArray, offset: Int = 0, count: Int = buffer.size - offset): ProtocolMessage {
        if (count < 1) throw ProtocolException("Message is empty.")
        if (count > ProtocolConstants.MAX_MESSAGE_BYTES) {
            throw ProtocolException("Message is longer than ${ProtocolConstants.MAX_MESSAGE_BYTES} bytes.")
        }
        val reader = WireReader(buffer, offset, offset + count)
        return when (val type = reader.readByte()) {
            MessageType.HELLO -> Hello(reader.readByte(), reader.readString())
            MessageType.WELCOME -> Welcome(reader.readByte(), reader.readUInt32(), reader.readUInt16(), reader.readByte())
            MessageType.REJECT -> {
                val code = reader.readByte()
                Reject(RejectReason.fromCode(code) ?: throw ProtocolException("Invalid RejectReason value $code."))
            }
            MessageType.PING -> Ping
            MessageType.PONG -> Pong
            MessageType.MOUSE_MOVE -> MouseMove(reader.readInt16(), reader.readInt16())
            MessageType.MOUSE_BUTTON -> {
                val code = reader.readByte()
                MouseButtonChange(
                    MouseButton.fromCode(code) ?: throw ProtocolException("Invalid MouseButton value $code."),
                    reader.readBool(),
                )
            }
            MessageType.SCROLL -> Scroll(reader.readInt16(), reader.readInt16())
            MessageType.KEY -> {
                val code = reader.readByte()
                Key(
                    SpecialKey.fromCode(code) ?: throw ProtocolException("Invalid SpecialKey value $code."),
                    reader.readBool(),
                )
            }
            MessageType.TEXT -> Text(reader.readString())
            MessageType.DISCOVERY_PROBE -> DiscoveryProbe(reader.readByte())
            MessageType.DISCOVERY_REPLY ->
                DiscoveryReply(reader.readByte(), reader.readString(), reader.readUInt16(), reader.readUInt16(), reader.readBool())
            else -> throw ProtocolException("Unknown message type $type.")
        }
    }
}
