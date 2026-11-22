package com.homecursor.client.update

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files

/** Talks to a real server on a loopback port. */
class UrlConnectionHttpFetcherTest {
    private lateinit var server: HttpServer
    private val directory: File = Files.createTempDirectory("fetcher").toFile()

    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            val body = "hello".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        directory.deleteRecursively()
    }

    @Test
    fun getText_returnsTheBody() {
        assertEquals("hello", UrlConnectionHttpFetcher(allowInsecure = true).getText("$baseUrl/ok"))
    }

    @Test
    fun download_writesTheBodyToTheFile() {
        val target = File(directory, "out.bin")

        UrlConnectionHttpFetcher(allowInsecure = true).download("$baseUrl/ok", target)

        assertEquals("hello", target.readText())
    }

    @Test
    fun getText_errorStatus_throwsIOException() {
        try {
            UrlConnectionHttpFetcher(allowInsecure = true).getText("$baseUrl/missing")
            org.junit.Assert.fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test(expected = IOException::class)
    fun getText_plainHttp_isRefusedByDefault() {
        UrlConnectionHttpFetcher().getText("$baseUrl/ok")
    }
}
