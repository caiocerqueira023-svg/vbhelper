package com.tamerslab.hardware.vpet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DigiROM builders must match the recovered lector.js byte-for-byte. */
class VpetDigiRomTest {

    private val golden = VpetGoldenVectors.load()

    @Test
    fun goldenRomVectorsMatch() {
        assertFalse("golden file has no rom vectors", golden.roms.isEmpty())
        for (vector in golden.roms) {
            assertEquals(
                "${vector.model.wireKey} v${vector.version}",
                vector.command,
                VpetDigiRom.commandFor(vector.model, vector.version),
            )
        }
    }

    @Test
    fun everyModelHasAllSixteenVersions() {
        assertEquals(6 * 16, golden.roms.size)
        for (model in VpetModel.entries) {
            val versions = golden.roms.filter { it.model == model }.map { it.version }.sorted()
            assertEquals(model.wireKey, (0..15).toList(), versions)
        }
    }

    @Test
    fun exactDigiRomsForVersionZeroAndFifteen() {
        assertEquals("V2-0301-0D0F-000E-008E-05AE-012E-000E-000E-000E-700E", VpetDigiRom.commandFor(VpetModel.DM20, 0))
        assertEquals("V2-0301-0D0F-00FE-008E-05AE-012E-000E-000E-000E-800E", VpetDigiRom.commandFor(VpetModel.DM20, 15))
        assertEquals("V2-230E-AA2E-560E-182E-0CAE-103E", VpetDigiRom.commandFor(VpetModel.DMX, 0))
        assertEquals("V2-23FE-AA2E-560E-182E-0CAE-103E", VpetDigiRom.commandFor(VpetModel.DMX, 15))
        assertEquals("V2-000E-1D8E-000E-000E-010E-000E-000E-000E-000E-A00E", VpetDigiRom.commandFor(VpetModel.PEN20, 0))
        assertEquals("V2-00FE-1D8E-000E-000E-010E-000E-000E-000E-000E-B00E", VpetDigiRom.commandFor(VpetModel.PEN20, 15))
        assertEquals("V2-030E-212E-288E-182E-018E-F00E", VpetDigiRom.commandFor(VpetModel.PENZ, 0))
        assertEquals("V2-03FE-212E-288E-182E-018E-000E", VpetDigiRom.commandFor(VpetModel.PENZ, 15))
    }

    @Test
    fun colorCommandsUseTheIntentionalRoto2Checksum() {
        assertEquals(
            "C1-47444C4300000000000B007300019406-47444C43000200020000000000006C74",
            VpetDigiRom.commandFor(VpetModel.DMC, 0),
        )
        assertEquals(
            "C2-4744447000000000000B007300018C33-47444470000200020000000000007447",
            VpetDigiRom.commandFor(VpetModel.PENC, 0),
        )
        // Color commands do not carry the requested version.
        for (version in 0..15) {
            assertEquals(VpetDigiRom.commandFor(VpetModel.DMC, 0), VpetDigiRom.commandFor(VpetModel.DMC, version))
            assertEquals(VpetDigiRom.commandFor(VpetModel.PENC, 0), VpetDigiRom.commandFor(VpetModel.PENC, version))
        }
    }

    @Test
    fun stopCommandIsZero() {
        assertEquals("0", VpetDigiRom.STOP_COMMAND)
    }

    @Test
    fun modelMetadataMatchesTheContract() {
        assertEquals(
            listOf("dm20", "dmx", "pen20", "penz", "dmc", "penc"),
            VpetModel.entries.map { it.wireKey },
        )
        assertEquals(
            listOf(false, false, false, false, true, true),
            VpetModel.entries.map { it.colorProtocol },
        )
        for (model in VpetModel.entries) {
            assertEquals(model, VpetModel.fromKey(model.wireKey))
            assertTrue("${model.wireKey} has no display name", model.displayName.isNotBlank())
        }
        assertEquals(VpetModel.DMX, VpetModel.fromKey(" DMX "))
        assertNull(VpetModel.fromKey("unknown"))
    }
}
