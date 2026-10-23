package com.github.nacabaro.vbhelper.gamesave

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class NdsLauncherBannerTest {
    private fun fixture(): ByteArray = ByteArray(0x200 + 0x240).also { bytes ->
        "ADNE".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0xC)
        bytes[0x68] = 0; bytes[0x69] = 2 // Banner begins at 0x200.
        bytes[0x200] = 1
        bytes[0x200 + 0x220 + 2] = 31 // Palette index 1: red, RGB555.
        bytes[0x200 + 0x220 + 4] = 0xE0.toByte(); bytes[0x200 + 0x220 + 5] = 3 // Index 2: green.
        bytes[0x200 + 0x20] = 0x21 // First tile, low nibble then high nibble.
        bytes[0x200 + 0x20 + 32] = 1 // Next tile begins at x=8, not y=2.
        bytes[0x200 + 0x20 + 15 * 32 + 31] = 0x10 // Last pixel x=31,y=31.
    }

    @Test fun decodesTheActualDsTiledBannerWithTransparentPaletteZero() {
        val source = fixture()
        val original = source.copyOf()
        val banner = NdsLauncherBanner.read(ByteArrayInputStream(source))
        assertEquals("ADNE", banner.gameCode)
        assertEquals(1024, banner.argb.size)
        assertEquals(0xFFFF0000.toInt(), banner.argb[0])
        assertEquals(0xFF00FF00.toInt(), banner.argb[1])
        assertEquals(0xFFFF0000.toInt(), banner.argb[8])
        assertEquals(0, banner.argb[2])
        assertEquals(0xFFFF0000.toInt(), banner.argb[1023])
        assertArrayEquals(original, source)
    }

    @Test fun alsoReadsStaticIconsInDsiBanners() {
        val bytes = fixture().also { it[0x200] = 3; it[0x201] = 1 }
        assertEquals("ADNE", NdsLauncherBanner.read(ByteArrayInputStream(bytes)).gameCode)
    }

    @Test fun rejectsTruncatedOffsetsUnknownVersionsAndNonGameHeaders() {
        val variants = listOf(fixture().copyOf(20), fixture().copyOf(0x230),
            fixture().also { it[0x68] = 1; it[0x69] = 0 },
            fixture().also { it[0x6B] = 0x7F },
            fixture().also { it[0x200] = 9 },
            fixture().also { it[0xC] = 0 })
        variants.forEach { bytes -> assertThrows(IllegalArgumentException::class.java) {
            NdsLauncherBanner.read(ByteArrayInputStream(bytes))
        } }
    }
}
