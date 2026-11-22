package com.homecursor.client.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * [HttpFetcher] on `HttpURLConnection`, so no HTTP library is needed. Only https addresses are used unless
 * [allowInsecure] is set (for tests against a loopback server).
 */
class UrlConnectionHttpFetcher(private val allowInsecure: Boolean = false) : HttpFetcher {
    override fun getText(url: String): String = open(url) { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_TEXT_BYTES) throw IOException("The response is too large")
        }
        output.toString(Charsets.UTF_8.name())
    }

    override fun download(url: String, target: File) {
        open(url) { input -> target.outputStream().use { output -> input.copyTo(output) } }
    }

    private fun <T> open(url: String, read: (InputStream) -> T): T {
        val parsed = URL(url)
        if (parsed.protocol != "https" && !(allowInsecure && parsed.protocol == "http")) {
            throw IOException("Refusing to fetch $url: only https is used")
        }

        val connection = parsed.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("The server answered HTTP $status")
            return connection.inputStream.use(read)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MAX_TEXT_BYTES = 1_000_000
        const val BUFFER_SIZE = 8 * 1024
    }
}
