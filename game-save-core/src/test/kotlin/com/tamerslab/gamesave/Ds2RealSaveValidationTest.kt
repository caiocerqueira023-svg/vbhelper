package com.tamerslab.gamesave

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in validation against the user's real Dawn/Dusk saves. The files are never committed and the
 * test skips unless DS2_SAVE_DIR points at a folder containing them.
 */
class Ds2RealSaveValidationTest {
    private val directory = System.getenv("DS2_SAVE_DIR")?.let(::File)

    private fun saves(): List<File> {
        val dir = directory
        assumeTrue("DS2_SAVE_DIR is not set", dir != null && dir.isDirectory)
        val files = dir!!.listFiles { file -> file.isFile && file.extension.equals("sav", true) }.orEmpty()
        assumeTrue("DS2_SAVE_DIR has no .sav files", files.isNotEmpty())
        return files.sortedBy { it.name }
    }

    @Test fun realSavesDecodeWithTheRecoveredLayout() {
        for (file in saves()) {
            val raw = WorldDs2SaveReader().read(file.readBytes())
            // Played saves can carry two populated slots (even tied at the same counter); the
            // automatic selection normalizes them so one copy is the active roster.
            val observation = NdsStoryCopySelection.applyAutomatic(raw)
            assertEquals("digimon_world_ds2_nds", observation.gameId)
            assertTrue("${file.name}: no populated copy validated", observation.copyIntegrity.values.any { it })
            assertTrue("${file.name}: no party records", observation.activeCount >= 1)
            for (record in observation.records.filter { it.membership == RecordMembership.ACTIVE_RECORD }) {
                assertTrue("${file.name}: species outside the Dawn/Dusk range",
                    record.nativeSpeciesId in 0x41L..0x1F4L)
                assertTrue("${file.name}: level outside 1..99", record.level in 1..99)
                assertTrue("${file.name}: stored name is not decodable", record.storedName.isNotBlank())
                assertNotNull("${file.name}: native parameters are missing", record.nativeParameters)
                assertEquals("${file.name}: record bytes are not the documented size",
                    0x164, Base64Length(record.nativeRecordBase64))
            }
            for (record in observation.records.filter { it.membership == RecordMembership.ACTIVE_RECORD && it.container == SaveContainer.PARTY }) {
                assertTrue("${file.name}: party member has review issues ${record.issues}", record.issues.isEmpty())
            }
        }
    }

    @Test fun removalAndReturnRebuildTheExactRealBytes() {
        for (file in saves()) {
            val source = file.readBytes()
            val observation = NdsStoryCopySelection.applyAutomatic(WorldDs2SaveReader().read(source))
            val target = observation.records.filter { it.membership == RecordMembership.ACTIVE_RECORD && it.container == SaveContainer.PARTY }
                .takeIf { it.size > 1 } ?: continue
            val edit = Ds2SaveEdits.removeForResearch(source, observation.contentRevision, target.first().recordKey)
            val after = NdsStoryCopySelection.applyAutomatic(WorldDs2SaveReader().read(edit.candidateBytes))
            assertEquals(observation.activeCount - 1, after.activeCount)
            assertTrue("${file.name}: candidate header is invalid",
                NdsStoryCopyIntegrity.isValid(edit.candidateBytes, 0, NdsStoryProfiles.DAWN_DUSK))
            assertArrayEquals(source, Ds2SaveEdits.restore(edit.candidateBytes, edit))
        }
    }

    @Test fun aSynthesizedRecordCanBeInsertedIntoTheCurrentRealSave() {
        for (file in saves()) {
            val source = file.readBytes()
            val observation = NdsStoryCopySelection.applyAutomatic(WorldDs2SaveReader().read(source))
            val party = observation.records.filter {
                it.membership == RecordMembership.ACTIVE_RECORD && it.container == SaveContainer.PARTY
            }
            val species = party.firstOrNull()?.nativeSpeciesId ?: continue
            val defaults = Ds2RecordCodec.Ds2RecordDefaults(
                unk1A = 0, unk50 = 5, abilities = listOf(1, 36, 255, 255), growth = 0,
                skills = listOf(32, 52, 111, 59), resistances = listOf(1000, 200, 900, 700, 700, 500, 400, 500),
                expCurve = 0, baseLevel = 1, maxLevel = 32, unk27 = 189)
            val generated = Ds2RecordCodec.generate(0, species, "TabTest", "Testmon",
                Ds2RecordCodec.Ds2GeneratedStats(12, 300, 300, 200, 200, 100, 90, 80, 70, 32),
                defaults = defaults, lineage = emptyList())
            // the exact failing operation: a synthesized record (identity 0) through the editor
            val editor = requireNotNull(NdsStorySaveEditors.forObservation(observation))
            val edit = editor.insertGenerated(source, observation.contentRevision,
                Ds2RecordCodec.base64(generated.bytes))
            val after = NdsStoryCopySelection.applyAutomatic(WorldDs2SaveReader().read(edit.candidateBytes))
            assertNotNull("${file.name}: the synthesized record did not read back",
                after.records.singleOrNull { it.nativeIdentity == edit.nativeIdentity })
            assertEquals("${file.name}: the saved copy lost its header",
                true, NdsStoryCopyIntegrity.isValid(edit.candidateBytes, 0, NdsStoryProfiles.DAWN_DUSK))
            assertArrayEquals(source, Ds2SaveEdits.restore(edit.candidateBytes, edit))
        }
    }
    private fun Base64Length(encoded: String?): Int =
        if (encoded == null) 0 else java.util.Base64.getDecoder().decode(encoded).size
}
