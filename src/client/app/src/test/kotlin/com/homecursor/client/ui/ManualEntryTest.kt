package com.homecursor.client.ui

import com.homecursor.client.connection.HostEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualEntryTest {
    @Test
    fun parse_addressOnly_usesDefaultPort() {
        assertEquals(HostEndpoint("192.168.1.20", "192.168.1.20", 47800, 47801), parseManualEndpoint("192.168.1.20"))
    }

    @Test
    fun parse_addressWithPort_usesPort() {
        assertEquals(50000, parseManualEndpoint("10.0.2.2:50000")?.tcpPort)
    }

    @Test
    fun parse_trimsWhitespace() {
        assertEquals("192.168.1.20", parseManualEndpoint("  192.168.1.20 ")?.address)
    }

    @Test
    fun parse_hostName_isAccepted() {
        assertEquals("my-pc.local", parseManualEndpoint("my-pc.local")?.address)
    }

    @Test
    fun parse_invalidInput_returnsNull() {
        for (input in listOf("", "   ", "192.168.1.20:", ":47800", "a b", "1.2.3.4:0", "1.2.3.4:65536", "1.2.3.4:abc", "1.2.3.4:1:2")) {
            assertNull("'$input'", parseManualEndpoint(input))
        }
    }
}
