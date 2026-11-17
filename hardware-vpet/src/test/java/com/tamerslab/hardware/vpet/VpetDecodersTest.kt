package com.tamerslab.hardware.vpet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decoders and recovered tables must match the lector.js golden vectors. */
class VpetDecodersTest {

    private val golden = VpetGoldenVectors.load()

    @Test
    fun goldenDecodeVectorsMatchTheRecoveredJavaScript() {
        assertTrue("golden file has no decode vectors", golden.decodes.size >= 6)
        for (vector in golden.decodes) {
            val label = "${vector.model.wireKey} v${vector.version} i${vector.index}"
            val result = VpetDecoders.decode(vector.model, vector.lines, vector.version)
            assertNotNull("no result for $label", result)
            result ?: continue
            assertEquals(label, vector.model, result.model)
            assertEquals(label, vector.version, result.version)
            assertEquals(label, vector.index, result.index)
            assertEquals(label, vector.name, result.name)
            assertEquals(label, vector.stage, result.stage)
            assertEquals(label, vector.attribute, result.attribute)
            assertEquals(label, vector.power, result.power)
            assertEquals(label, vector.payloadHex, result.payloadHex)
            assertEquals(label, vector.checksumValid, result.checksumValid)
        }
    }

    @Test
    fun recoveredTablesMatchTheGeneratedCounts() {
        assertEquals(
            linkedSetOf("dm20", "pen20", "z1", "z2", "x0", "x1", "x2", "dmc", "penc"),
            VpetTables.listNames(),
        )
        for ((name, count) in golden.tableCounts) {
            assertEquals("slot count of $name", count, VpetTables.slotCount(name))
        }
        assertEquals("Koromon", VpetTables.entry("dm20", 1)?.name)
        assertEquals(0, VpetTables.entry("dm20", 1)?.stage)
        assertEquals(3, VpetTables.entry("dm20", 1)?.attribute)
        assertNull(VpetTables.entry("dm20", 0))
        assertNull(VpetTables.entry("dm20", 100_000))
        assertEquals("Agumon", VpetTables.keyedEntry("dmc", "01-02")?.name)
        assertEquals("Mochimon", VpetTables.keyedEntry("penc", "1-01")?.name)
        assertNull(VpetTables.keyedEntry("dmc", "99-99"))
        assertNull(VpetTables.rawValue("unknown"))
        assertTrue(VpetTables.rawValue("dm20")?.startsWith("|") == true)
    }

    @Test
    fun everyModelRejectsEmptyInput() {
        for (model in VpetModel.entries) {
            assertNull(model.wireKey, VpetDecoders.decode(model, emptyList(), 0))
        }
    }

    @Test
    fun requestedVersionDoesNotChangeTheDecodedResult() {
        val vector = vectorFor(VpetModel.PEN20)
        val first = VpetDecoders.decode(VpetModel.PEN20, vector.lines, 0)
        val second = VpetDecoders.decode(VpetModel.PEN20, vector.lines, 9)
        assertNotNull(first)
        assertEquals(first, second)
    }

    // -----------------------------------------------------------------------------------
    // DM20
    // -----------------------------------------------------------------------------------

    @Test
    fun dm20RejectsIncompletePacketSetsAndAcceptsPartialReads() {
        assertNull(VpetDecoders.decode(VpetModel.DM20, emptyList(), 0))
        val lines = packetsOf(VpetModel.DM20)
        assertNull(VpetDecoders.decode(VpetModel.DM20, lines.take(3), 0))

        val partial = VpetDecoders.decode(VpetModel.DM20, lines.take(4), 0)
        assertNotNull(partial)
        partial ?: return
        assertFalse(partial.checksumValid)
        assertEquals(4, partial.index)
        assertEquals("Greymon", partial.name)
        assertNull(partial.power)
    }

    @Test
    fun dm20FlagsChecksumMismatchWithoutDroppingTheRead() {
        val lines = packetsOf(VpetModel.DM20)
        val broken = withLastPacketWord(lines, lastPacketWord(lines) xor 0x1)
        val result = VpetDecoders.decode(VpetModel.DM20, broken, 3)
        assertNotNull(result)
        result ?: return
        assertFalse(result.checksumValid)
        assertEquals("Greymon", result.name)
    }

    @Test
    fun dm20UnknownIndexLeavesNameNull() {
        val lines = packetsOf(VpetModel.DM20).toMutableList()
        // q[3] carries index << 6 | attribute << 4 | 0xE; 1000 is outside the recovered table.
        lines[3] = "r:${VpetProtocol.hex4((1000 shl 6) or 0xE)}"
        val result = VpetDecoders.decode(VpetModel.DM20, lines, 3)
        assertNotNull(result)
        result ?: return
        assertEquals(1000, result.index)
        assertNull(result.name)
    }

    // -----------------------------------------------------------------------------------
    // Pen20
    // -----------------------------------------------------------------------------------

    @Test
    fun pen20FlagsChecksumMismatch() {
        val lines = packetsOf(VpetModel.PEN20)
        val broken = withLastPacketWord(lines, lastPacketWord(lines) xor 0x1)
        val result = VpetDecoders.decode(VpetModel.PEN20, broken, 6)
        assertNotNull(result)
        result ?: return
        assertFalse(result.checksumValid)
        assertEquals("Okuwamon", result.name)
    }

    @Test
    fun pen20FallsBackToTheDm20Layout() {
        val result = VpetDecoders.decode(VpetModel.PEN20, packetsOf(VpetModel.DM20), 3)
        assertNotNull(result)
        result ?: return
        assertEquals(VpetModel.PEN20, result.model)
        assertEquals(3, result.version)
        assertEquals("Greymon", result.name)
    }

    // -----------------------------------------------------------------------------------
    // PenZ
    // -----------------------------------------------------------------------------------

    @Test
    fun penzFlagsChecksumMismatch() {
        val lines = packetsOf(VpetModel.PENZ)
        val broken = withLastPacketWord(lines, lastPacketWord(lines) xor 0x1)
        val result = VpetDecoders.decode(VpetModel.PENZ, broken, 6)
        assertNotNull(result)
        result ?: return
        assertFalse(result.checksumValid)
        assertEquals("Golemon", result.name)
    }

    @Test
    fun penzRejectsUnknownVersions() {
        val lines = packetsOf(VpetModel.PENZ).toMutableList()
        // Version 12 is outside lector.js PENZ_VER (6..11); the low nibble must stay 0xE.
        lines[0] = "r:00CE"
        assertNull(VpetDecoders.decode(VpetModel.PENZ, lines, 12))
    }

    // -----------------------------------------------------------------------------------
    // DMX
    // -----------------------------------------------------------------------------------

    @Test
    fun dmxFiltersPacketsWithoutLowNibbleE() {
        assertNull(VpetDecoders.decode(VpetModel.DMX, listOf("r:1234", "r:5678", "r:9ABC"), 0))
        val lines = packetsOf(VpetModel.DMX).toMutableList()
        lines[2] = "r:0001"
        val result = VpetDecoders.decode(VpetModel.DMX, lines, 4)
        assertNotNull(result)
        result ?: return
        assertFalse(result.checksumValid)
        assertEquals("Tsumemon", result.name)
    }

    @Test
    fun dmxHidesTheNameAtIndexZero() {
        val lines = packetsOf(VpetModel.DMX).toMutableList()
        lines[1] = "r:${VpetProtocol.hex4((3 shl 4) or 0xE)}"
        val result = VpetDecoders.decode(VpetModel.DMX, lines, 4)
        assertNotNull(result)
        result ?: return
        assertEquals(0, result.index)
        assertNull(result.name)
    }

    // -----------------------------------------------------------------------------------
    // DMC
    // -----------------------------------------------------------------------------------

    @Test
    fun dmcResolvesTheVipVersionMapping() {
        // Version 1 has no direct "01-17" entry; 17 only exists in the VIP tables (17-17).
        val vip = golden.decodes.first { it.model == VpetModel.DMC && it.version == 1 && it.index == 17 }
        val payload = VpetDecoders.decodePayload(VpetModel.DMC, vip.lines)
        assertNotNull(payload)
        payload ?: return
        assertEquals("Omegamon Alter-S", payload.name)
        assertEquals("17-17", payload.colorKey)
        assertNull("a matching VIP version must not warn", payload.warning)
    }

    @Test
    fun dmcVipFallbackWarnsWhenTheNumberDoesNotFitTheVersion() {
        // 18 only exists in VIP version 18 and does not fit version 1: lector.js still decodes
        // it (18-18) but reports the mismatch.
        val vip = golden.decodes.first { it.model == VpetModel.DMC && it.version == 1 && it.index == 18 }
        val payload = VpetDecoders.decodePayload(VpetModel.DMC, vip.lines)
        assertNotNull(payload)
        payload ?: return
        assertEquals("18-18", payload.colorKey)
        assertNotNull("expected the VIP mismatch warning", payload.warning)
    }

    @Test
    fun dmcUnknownEntryKeepsTheResultWithoutName() {
        val block = colorBlock(0x4744, 0x4C43, 0, 0, 250, 100, 0, 0)
        val result = VpetDecoders.decode(VpetModel.DMC, listOf("r:$block"), 0)
        assertNotNull(result)
        result ?: return
        assertNull(result.name)
        assertEquals(1, result.version)
        assertEquals(250, result.index)
        assertEquals(100, result.power)
        assertTrue(result.checksumValid)
    }

    @Test
    fun dmcDecodesAFullC1CodeThatIsNotOneOfOurs() {
        val firstBlock = "47444C43" + "00000000" + "0002" + "000000000000"
        val secondBlock = "47444C4300020002000000000000938B"
        val code = "C1-$firstBlock-$secondBlock"
        val result = VpetDecoders.decode(VpetModel.DMC, listOf(code), 0)
        assertNotNull(result)
        result ?: return
        assertEquals(1, result.version)
        assertEquals(2, result.index)
        assertEquals("Agumon", result.name)
        assertEquals("$firstBlock $secondBlock", result.payloadHex)
    }

    @Test
    fun dmcIgnoresItsOwnSentCodes() {
        assertNull(VpetDecoders.decode(VpetModel.DMC, listOf(VpetDigiRom.commandFor(VpetModel.DMC, 0)), 0))
        assertNull(VpetDecoders.decode(VpetModel.DMC, listOf(VpetProtocol.DMC_RIVAL), 0))
    }

    @Test
    fun dmcUsesOnlyTheFirstBlockAfterEachStartMarker() {
        val first = colorBlock(0x4744, 0x4C43, 0, 0, 1, 100, 0, 0)
        val second = colorBlock(0x4744, 0x4C43, 0, 0, 2, 100, 0, 0)

        val repeated = VpetDecoders.decode(VpetModel.DMC, listOf("r:$first", "r:$second"), 0)
        assertNotNull(repeated)
        assertEquals(1, repeated?.index)

        val restarted = VpetDecoders.decode(VpetModel.DMC, listOf("s:$first", "r:$second"), 0)
        assertNotNull(restarted)
        assertEquals(2, restarted?.index)
    }

    // -----------------------------------------------------------------------------------
    // PenC
    // -----------------------------------------------------------------------------------

    @Test
    fun pencRejectsBlocksWithInvalidChecksum() {
        val validLine = vectorFor(VpetModel.PENC).lines.single()
        assertNull(VpetDecoders.decode(VpetModel.PENC, listOf(incrementColorTail(validLine)), 0))
    }

    @Test
    fun pencIgnoresBattleResultBlocks() {
        val block = pencBlockWithChecksum(0x4744, 0x4470, 18, 0, 1, 50, 0)
        assertNull(VpetDecoders.decode(VpetModel.PENC, listOf("r:$block"), 0))
    }

    @Test
    fun pencUnknownSpeciesKeepsVersionPowerAndChecksum() {
        val block = pencBlockWithChecksum(0x4744, 0x4470, 1, 0, 250, 50, 0)
        val result = VpetDecoders.decode(VpetModel.PENC, listOf("r:$block"), 0)
        assertNotNull(result)
        result ?: return
        assertEquals(0, result.version)
        assertEquals(250, result.index)
        assertNull(result.name)
        assertEquals(50, result.power)
        assertTrue(result.checksumValid)
    }

    // -----------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------

    private fun vectorFor(model: VpetModel): VpetGoldenVectors.DecodeVector =
        golden.decodes.first { it.model == model }

    private fun packetsOf(model: VpetModel): List<String> = vectorFor(model).lines

    private fun withLastPacketWord(lines: List<String>, word: Int): List<String> =
        lines.dropLast(1) + "r:${VpetProtocol.hex4(word)}"

    private fun lastPacketWord(lines: List<String>): Int =
        lines.last().removePrefix("r:").toInt(16)

    private fun colorBlock(vararg words: Int): String =
        words.joinToString("") { it.toString(16).uppercase().padStart(4, '0') }

    private fun incrementColorTail(line: String): String {
        val block = line.removePrefix("r:")
        val tail = (block.takeLast(4).toInt(16) + 1) and 0xFFFF
        return "r:" + block.dropLast(4) + tail.toString(16).uppercase().padStart(4, '0')
    }

    private fun pencBlockWithChecksum(vararg words: Int): String {
        val checksum = words.sum() and 0xFFFF
        return (words.toList() + checksum).joinToString("") {
            it.toString(16).uppercase().padStart(4, '0')
        }
    }
}
