package com.homecursor.client.protocol

/** Why the host refused a connection. [code] is the wire value. */
enum class RejectReason(val code: Int) {
    Busy(1),
    UnsupportedVersion(2);

    companion object {
        fun fromCode(code: Int): RejectReason? = entries.firstOrNull { it.code == code }
    }
}

/** Wire value of every message type. */
internal object MessageType {
    const val HELLO = 1
    const val WELCOME = 2
    const val REJECT = 3
    const val PING = 4
    const val PONG = 5
    const val DISCOVERY_PROBE = 32
    const val DISCOVERY_REPLY = 33
}

/** The protocol messages a client sends or receives while discovering and connecting to a host. */
sealed interface ProtocolMessage {
    /** Client to host: first message on the TCP connection. */
    data class Hello(val protocolVersion: Int, val clientName: String) : ProtocolMessage

    /** Host to client: the connection is accepted. */
    data class Welcome(
        val protocolVersion: Int,
        val sessionId: Long,
        val udpPort: Int,
        val pingIntervalSeconds: Int,
    ) : ProtocolMessage

    /** Host to client: the connection is refused and will be closed. */
    data class Reject(val reason: RejectReason) : ProtocolMessage

    /** Client to host: keep-alive. */
    data object Ping : ProtocolMessage

    /** Host to client: answer to [Ping]. */
    data object Pong : ProtocolMessage

    /** Client to host (UDP broadcast): who is out there? */
    data class DiscoveryProbe(val protocolVersion: Int) : ProtocolMessage

    /** Host to client (UDP unicast): the answer to a [DiscoveryProbe]. */
    data class DiscoveryReply(
        val protocolVersion: Int,
        val hostName: String,
        val tcpPort: Int,
        val udpPort: Int,
        val isBusy: Boolean,
    ) : ProtocolMessage
}
