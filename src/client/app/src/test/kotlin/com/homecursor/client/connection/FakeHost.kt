package com.homecursor.client.connection

import com.homecursor.client.protocol.MessageFraming
import com.homecursor.client.protocol.ProtocolMessage
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** A TCP host on loopback. Each accepted connection is passed to [handler] on its own thread. */
class FakeHost(private val handler: (Socket) -> Unit) : AutoCloseable {
    private val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    private val sockets = LinkedBlockingQueue<Socket>()
    val port: Int get() = server.localPort
    val endpoint get() = HostEndpoint("127.0.0.1", "Fake", port, 47801)

    init {
        thread(isDaemon = true) {
            try {
                while (true) {
                    val socket = server.accept()
                    sockets.add(socket)
                    thread(isDaemon = true) { runCatching { handler(socket) } }
                }
            } catch (_: java.net.SocketException) {
                // closed
            }
        }
    }

    override fun close() {
        server.close()
        sockets.forEach { runCatching { it.close() } }
    }

    companion object {
        fun welcome(pingIntervalSeconds: Int = 5, version: Int = 1) =
            ProtocolMessage.Welcome(version, 42, 47801, pingIntervalSeconds)

        /** Reads Hello and answers with [reply]. Returns the Hello. */
        fun accept(socket: Socket, reply: ProtocolMessage?): ProtocolMessage? {
            val hello = MessageFraming.read(socket.getInputStream())
            if (reply != null) MessageFraming.write(socket.getOutputStream(), reply)
            return hello
        }
    }
}

fun <T> LinkedBlockingQueue<T>.pollWithin(millis: Long): T? = poll(millis, TimeUnit.MILLISECONDS)
