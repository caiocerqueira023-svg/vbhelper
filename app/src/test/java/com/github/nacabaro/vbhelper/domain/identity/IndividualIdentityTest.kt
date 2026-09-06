package com.github.nacabaro.vbhelper.domain.identity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndividualIdentityTest {
    @Test
    fun roundTripPreservesIdentity() {
        val identity = "00112233445566778899aa"

        assertEquals(identity, IndividualIdentity.decode(IndividualIdentity.encode(identity)))
    }

    @Test
    fun decodeRejectsForeignOrEmptyReservedData() {
        assertNull(IndividualIdentity.decode(ByteArray(12)))

        val encoded = IndividualIdentity.encode("00112233445566778899aa")
        encoded[0] = 0
        assertNull(IndividualIdentity.decode(encoded))
    }

    @Test
    fun encodeUsesExactlyTwelveBytes() {
        assertArrayEquals(
            byteArrayOf(
                0xB6.toByte(), 0x00, 0x11, 0x22, 0x33, 0x44,
                0x55, 0x66, 0x77, 0x88.toByte(), 0x99.toByte(), 0xAA.toByte()
            ),
            IndividualIdentity.encode("00112233445566778899aa")
        )
    }
}
