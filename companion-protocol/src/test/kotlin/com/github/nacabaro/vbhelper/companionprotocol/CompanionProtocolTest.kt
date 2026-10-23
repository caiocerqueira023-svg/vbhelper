package com.github.nacabaro.vbhelper.companionprotocol

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class CompanionProtocolTest {
    private val pair = CompanionPairing("00000000-0000-0000-0000-000000000001", "192.168.1.2", 7428, "a".repeat(64), "b".repeat(43))
    @Test fun pairingRoundTripsWithoutLoggingItsSecret() {
        val parsed = CompanionPairing.parse(pair.link())
        assertEquals(pair.token, parsed.token); assertEquals(pair.host, parsed.host)
        assertFalse(parsed.toString().contains(pair.token))
    }
    @Test fun rejectsDuplicateFieldsAndUnexpectedSchemes() {
        assertThrows(IllegalArgumentException::class.java) { CompanionPairing.parse(pair.link() + "&token=" + pair.token) }
        assertThrows(IllegalArgumentException::class.java) { CompanionPairing.parse(pair.link().replace("vbhelper-companion:", "http:")) }
    }
    @Test fun restrictsEndpointsToLocalNetworks() {
        assertTrue(CompanionProtocol.local(InetAddress.getByName("192.168.1.2")))
        assertTrue(CompanionProtocol.local(InetAddress.getByName("127.0.0.1")))
        assertFalse(CompanionProtocol.local(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun rejectsOversizedMessages() {
        assertThrows(IllegalArgumentException::class.java) { CompanionProtocol.bounded(ByteArray(17).inputStream(), 16) }
    }
}
