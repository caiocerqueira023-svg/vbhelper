package com.tamerslab.gamesave

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lost Evolution layout tests against a synthetic save built from the recovered byte windows and
 * against the user's real save when `LE_SAVE_DIR` is set.
 */
class LostEvolutionSaveTest {
    private val profile = NdsStoryProfiles.LOST_EVOLUTION
    private val marker = LostEvolution.IDENTITY_MARKER

    private fun identityText(low: Long): String = LostEvolution.identityText((marker shl 32) or low)

    private fun record(
        identityLow: Long = 0,
        species: Int,
        name: String,
        level: Int,
        hp: Int,
        mp: Int,
        attack: Int,
        defense: Int,
        spirit: Int,
        speed: Int,
        exp: Long = 0,
        skills: List<Int> = emptyList(),
    ): ByteArray = ByteArray(LostEvolution.RECORD_BYTES).also { record ->
        if (identityLow != 0L) {
            LostEvolution.putU64(record, LostEvolution.OFFSET_IDENTITY, (marker shl 32) or identityLow)
            LostEvolution.putU32(record, LostEvolution.OFFSET_IDENTITY_COPY, identityLow)
            LostEvolution.putU32(record, LostEvolution.OFFSET_IDENTITY_COPY + 4, marker)
        }
        LostEvolution.putU16(record, LostEvolution.OFFSET_SPECIES, species)
        LostEvolutionTextCodec.encodeBytes(name).copyInto(record, LostEvolution.OFFSET_NAME)
        record[LostEvolution.OFFSET_VARIANT] = 1
        LostEvolution.putU32(record, LostEvolution.OFFSET_FLAGS, 0x40)
        LostEvolution.putU16(record, LostEvolution.OFFSET_HP, hp)
        LostEvolution.putU16(record, LostEvolution.OFFSET_MAX_HP, hp)
        LostEvolution.putU16(record, LostEvolution.OFFSET_MP, mp)
        LostEvolution.putU16(record, LostEvolution.OFFSET_MAX_MP, mp)
        LostEvolution.putU16(record, LostEvolution.OFFSET_ATTACK, attack)
        LostEvolution.putU16(record, LostEvolution.OFFSET_DEFENSE, defense)
        LostEvolution.putU16(record, LostEvolution.OFFSET_SPIRIT, spirit)
        LostEvolution.putU16(record, LostEvolution.OFFSET_FRIENDSHIP, 100)
        record[LostEvolution.OFFSET_APTITUDE] = 100
        record[LostEvolution.OFFSET_RANK] = 5
        record[LostEvolution.OFFSET_LEVEL] = level.toByte()
        record[LostEvolution.OFFSET_SPEED] = speed.toByte()
        LostEvolution.putU32(record, LostEvolution.OFFSET_EXP, exp)
        for (slot in 0 until LostEvolution.SKILL_SLOTS) {
            LostEvolution.putU16(record, LostEvolution.OFFSET_SKILLS + slot * 2, 0xFFFF)
        }
        skills.forEachIndexed { index, skill ->
            LostEvolution.putU16(record, LostEvolution.OFFSET_SKILLS + index * 2, skill)
        }
    }

    private fun putRecord(bytes: ByteArray, slot: Int, record: ByteArray) {
        record.copyInto(bytes, LostEvolution.ROSTER_SAVE_BASE + slot * LostEvolution.RECORD_BYTES)
    }

    /** Two valid copies; copy 2 is newest (higher sequence). Party holds records 1 and 2, farm record 3. */
    private fun fixture(): ByteArray {
        val bytes = ByteArray(LostEvolution.FILE_BYTES)
        val content = ByteArray(LostEvolution.SAVE_BYTES)
        LostEvolution.TAG.copyInto(content, LostEvolution.TAG_OFFSET)
        // Records: Agumon (id 1), Gabumon (id 2), ToyAgumon (id 3).
        putRecord(content, 0, record(1, 77, "Agumon", 4, 168, 157, 94, 54, 56, 20, exp = 26, skills = listOf(32, 105)))
        putRecord(content, 1, record(2, 82, "Gabumon", 2, 134, 141, 69, 47, 78, 20, exp = 4, skills = listOf(99)))
        putRecord(content, 2, record(3, 90, "ToyAgumon", 1, 144, 139, 64, 58, 45, 21))
        // Party: records 1 and 2.
        LostEvolution.putU64(content, LostEvolution.PARTY_SAVE_BASE, (marker shl 32) or 1)
        LostEvolution.putU64(content, LostEvolution.PARTY_SAVE_BASE + 8, (marker shl 32) or 2)
        // Farm: one group with record 3 as resident.
        LostEvolution.putU32(content, LostEvolution.FARM_SAVE_BASE, 1)
        LostEvolution.putU64(content, LostEvolution.FARM_SAVE_BASE + 4 + LostEvolution.FARM_RESIDENT_OFFSET,
            (marker shl 32) or 3)
        // Next identity counter: {low 4, marker}.
        LostEvolution.putU64(content, LostEvolution.NEXT_ID_SAVE_OFFSET, (marker shl 32) or 4)
        // Copy 1 sequence 1, copy 2 sequence 2 with identical content.
        LostEvolution.putU32(content, LostEvolution.SEQUENCE_OFFSET, 1)
        content.copyInto(bytes, 0)
        NdsStoryCopyIntegrity.seal(bytes, 0, profile)
        LostEvolution.putU32(content, LostEvolution.SEQUENCE_OFFSET, 2)
        content.copyInto(bytes, LostEvolution.COPY_STRIDE)
        NdsStoryCopyIntegrity.seal(bytes, LostEvolution.COPY_STRIDE, profile)
        content.fill(0)
        return bytes
    }

    @Test fun readsBothCopiesAndIdentifiesThePartymons() {
        val observation = LostEvolutionSaveReader().read(fixture())
        assertEquals(LostEvolution.GAME_ID, observation.gameId)
        assertEquals(LostEvolution.FORMAT_PROFILE, observation.formatProfile)
        assertEquals(mapOf(1 to true, 2 to true), observation.copyIntegrity)
        val copy2 = observation.records.filter { it.nativeParameters?.saveBlock == 2 }
        assertEquals(3, copy2.size)
        val agumon = copy2.single { it.nativeSpeciesId == 77L }
        assertEquals("Agumon", agumon.storedName)
        assertEquals(4, agumon.level)
        assertEquals(26L, agumon.totalExp)
        assertEquals(SaveContainer.PARTY, agumon.container)
        assertEquals(1, agumon.position)
        val stats = requireNotNull(agumon.nativeParameters)
        assertEquals(168, stats.maxHp)
        assertEquals(157, stats.maxMp)
        assertEquals(94, stats.attack)
        assertEquals(54, stats.defense)
        assertEquals(56, stats.spirit)
        assertEquals(20, stats.speed)
        val toy = copy2.single { it.nativeSpeciesId == 90L }
        assertEquals(SaveContainer.FARM, toy.container)
        assertEquals(identityText(3), toy.nativeIdentity)
        assertNotNull(agumon.nativeRecordBase64)
    }

    @Test fun newestCopyWins() {
        val observation = LostEvolutionSaveReader().read(fixture())
        assertEquals(2, NdsStoryCopySelection.latestCopy(observation))
        assertEquals(2, NdsStoryCopySelection.authoritativeCopy(observation))
    }

    @Test fun removingAnIndividualClearsItsRecordAndEveryReference() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        val target = observation.records.single { it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 77L }
        val edit = LostEvolutionSaveEdits.removeRecord(source, observation.contentRevision, target.recordKey)
        val candidate = edit.candidateBytes
        // both card copies now carry the same edited body with consecutive sequences and fresh MD5s
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, 0, profile))
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, LostEvolution.COPY_STRIDE, profile))
        assertArrayEquals(candidate.copyOfRange(0x1C, LostEvolution.SAVE_BYTES),
            candidate.copyOfRange(LostEvolution.COPY_STRIDE + 0x1C,
                LostEvolution.COPY_STRIDE + LostEvolution.SAVE_BYTES))
        val firstSequence = LostEvolution.u32(candidate, LostEvolution.SEQUENCE_OFFSET)
        val secondSequence = LostEvolution.u32(candidate, LostEvolution.COPY_STRIDE + LostEvolution.SEQUENCE_OFFSET)
        assertEquals(firstSequence + 1, secondSequence)
        val after = LostEvolutionSaveReader().read(candidate)
        val afterCopy = after.records.filter { it.nativeParameters?.saveBlock == 2 }
        assertEquals(2, afterCopy.size)
        assertTrue(afterCopy.none { it.nativeSpeciesId == 77L })
        // identity gone from the party slots
        assertTrue((0 until LostEvolution.PARTY_SLOTS).all { slot ->
            LostEvolution.u64(candidate, LostEvolution.COPY_STRIDE + LostEvolution.PARTY_SAVE_BASE + slot * 8) !=
                ((marker shl 32) or 1)
        })
        assertArrayEquals(source, LostEvolutionSaveEdits.restore(candidate, edit))
        assertTrue(LostEvolution.u32(candidate, 0 + LostEvolution.SEQUENCE_OFFSET) +
            1 == LostEvolution.u32(candidate, LostEvolution.COPY_STRIDE + LostEvolution.SEQUENCE_OFFSET))
    }

    @Test fun generatedRecordsGetTheNextIdentityAndAPlace() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        val generated = record(0, 101, "Falcomon", 1, 125, 137, 69, 44, 79, 20)
        val edit = LostEvolutionSaveEdits.insertGeneratedRecord(source, observation.contentRevision,
            java.util.Base64.getEncoder().encodeToString(generated))
        val candidate = edit.candidateBytes
        val after = LostEvolutionSaveReader().read(candidate)
        val inserted = requireNotNull(after.records.singleOrNull {
            it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 101L
        })
        assertEquals(identityText(4), inserted.nativeIdentity)
        assertEquals(SaveContainer.FARM, inserted.container)
        assertEquals(1, inserted.level)
        // the game's own next-identity counter advanced
        assertEquals(5L, LostEvolution.u64(candidate,
            LostEvolution.COPY_STRIDE + LostEvolution.NEXT_ID_SAVE_OFFSET) and 0xFFFFFFFFL)
        // every identity window and the tail slots match a game-created record
        val insertedBytes = java.util.Base64.getDecoder().decode(requireNotNull(inserted.nativeRecordBase64))
        assertEquals(0x030405F0L, LostEvolution.u32(insertedBytes, LostEvolution.OFFSET_IDENTITY_MARKER))
        assertEquals(4L, LostEvolution.u32(insertedBytes, LostEvolution.OFFSET_IDENTITY_COPY))
        assertEquals(0x030405F0L,
            LostEvolution.u32(insertedBytes, LostEvolution.OFFSET_IDENTITY_COPY + 4))
        for (slot in 0 until 6) {
            assertEquals(0xFFFF, LostEvolution.u16(insertedBytes,
                LostEvolution.OFFSET_TAIL_SLOTS + slot * 2))
        }
        assertArrayEquals(source, LostEvolutionSaveEdits.restore(candidate, edit))
    }

    @Test fun everyEditedSaveKeepsThePlayerStateChecksumValid() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        fun requireValidChecksum(bytes: ByteArray) {
            for (base in listOf(0, LostEvolution.COPY_STRIDE)) {
                assertTrue(LostEvolution.stateChecksumValid(
                    bytes.copyOfRange(base, base + LostEvolution.SAVE_BYTES)))
            }
        }
        val agumon = observation.records.single {
            it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 77L
        }
        requireValidChecksum(LostEvolutionSaveEdits.removeRecord(source,
            observation.contentRevision, agumon.recordKey).candidateBytes)
        val generated = record(0, 101, "Falcomon", 1, 125, 137, 69, 44, 79, 20)
        requireValidChecksum(LostEvolutionSaveEdits.insertGeneratedRecord(source,
            observation.contentRevision,
            java.util.Base64.getEncoder().encodeToString(generated)).candidateBytes)
        val retained = record(9, 77, "Agumon", 4, 168, 157, 94, 54, 56, 20)
        requireValidChecksum(LostEvolutionSaveEdits.insertRetainedRecord(source,
            observation.contentRevision,
            java.util.Base64.getEncoder().encodeToString(retained)).candidateBytes)
    }

    @Test fun chosenDestinationsPlaceTheRecordWhereThePlayerAsked() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        for ((requested, expected) in listOf(
            SaveContainer.PARTY to SaveContainer.PARTY,
            SaveContainer.FARM to SaveContainer.FARM,
            SaveContainer.BANK to SaveContainer.BANK)) {
            val generated = record(0, 101, "Falcomon", 1, 125, 137, 69, 44, 79, 20)
            val edit = LostEvolutionSaveEdits.insertGeneratedRecord(source,
                observation.contentRevision,
                java.util.Base64.getEncoder().encodeToString(generated), requested)
            val inserted = requireNotNull(LostEvolutionSaveReader().read(edit.candidateBytes)
                .records.singleOrNull { it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 101L })
            assertEquals(expected, inserted.container)
        }
    }
    @Test fun insertedSpeciesIsRegisteredInTheEncyclopediaBook() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        val generated = record(0, 101, "Falcomon", 1, 125, 137, 69, 44, 79, 20)
        val edit = LostEvolutionSaveEdits.insertGeneratedRecord(source,
            observation.contentRevision,
            java.util.Base64.getEncoder().encodeToString(generated))
        for (base in listOf(0, LostEvolution.COPY_STRIDE)) {
            val entry = LostEvolution.u64(edit.candidateBytes,
                base + LostEvolution.SPECIES_BOOK_SAVE_BASE + 101 * 8)
            assertEquals(LostEvolution.SPECIES_BOOK_OWNED, entry)
            assertTrue(LostEvolution.stateChecksumValid(
                edit.candidateBytes.copyOfRange(base, base + LostEvolution.SAVE_BYTES)))
        }
    }

    @Test fun legacyStoredRecordsAreStampedLikeGameCreatedOnes() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        // A record saved by an older app build: identity is present but the marker copy at +0x08 and
        // the tail slots were never initialized. The editor must repair both on insert.
        val legacy = record(9, 77, "Agumon", 4, 168, 157, 94, 54, 56, 20)
        LostEvolution.putU32(legacy, LostEvolution.OFFSET_IDENTITY_MARKER, 0)
        for (slot in 0 until 6) LostEvolution.putU16(legacy, LostEvolution.OFFSET_TAIL_SLOTS + slot * 2, 0)
        val edit = LostEvolutionSaveEdits.insertRetainedRecord(source, observation.contentRevision,
            java.util.Base64.getEncoder().encodeToString(legacy))
        val inserted = requireNotNull(LostEvolutionSaveReader().read(edit.candidateBytes).records.singleOrNull {
            it.nativeParameters?.saveBlock == 2 && it.nativeIdentity == identityText(9)
        })
        val bytes = java.util.Base64.getDecoder().decode(requireNotNull(inserted.nativeRecordBase64))
        assertEquals(0x030405F0L, LostEvolution.u32(bytes, LostEvolution.OFFSET_IDENTITY_MARKER))
        for (slot in 0 until 6) {
            assertEquals(0xFFFF, LostEvolution.u16(bytes, LostEvolution.OFFSET_TAIL_SLOTS + slot * 2))
        }
    }

    @Test fun planningAnEditNeverMutatesTheSourceBytes() {
        val source = fixture()
        val snapshot = source.copyOf()
        val observation = LostEvolutionSaveReader().read(source)
        val generated = LostEvolution.base64Encode(record(0, 101, "Falcomon", 1, 125, 137, 69, 44, 79, 20))
        val inserted = LostEvolutionSaveEdits.insertGeneratedRecord(source, observation.contentRevision, generated)
        assertArrayEquals(snapshot, source)
        assertTrue(inserted.candidateRevision != observation.contentRevision)
        val removeTarget = observation.records.first { it.nativeParameters?.saveBlock == 2 }
        LostEvolutionSaveEdits.removeRecord(source, observation.contentRevision, removeTarget.recordKey)
        assertArrayEquals(snapshot, source)
        val admitted = observation.records.first { it.nativeParameters?.saveBlock == 2 && it.container == SaveContainer.FARM }
        if (admitted != null) {
            LostEvolutionSaveEdits.admitToParty(source, observation.contentRevision, admitted.recordKey)
            assertArrayEquals(snapshot, source)
        }
        snapshot.fill(0)
    }

    @Test fun retainedRecordsGoToTheFarmWhenThePartyIsFull() {
        val source = fixture()
        // Fill the remaining party slots in a candidate so the insert has to fall back to the farm.
        val filled = source.copyOf()
        for (slot in 2 until LostEvolution.PARTY_SLOTS) {
            LostEvolution.putU64(filled, LostEvolution.COPY_STRIDE + LostEvolution.PARTY_SAVE_BASE + slot * 8,
                (marker shl 32) or (100L + slot))
        }
        NdsStoryCopyIntegrity.seal(filled, LostEvolution.COPY_STRIDE, profile)
        assertEquals(2, LostEvolutionSaveReader().read(filled).records.count {
            it.nativeParameters?.saveBlock == 2 && it.container == SaveContainer.PARTY
        })
        val observation2 = LostEvolutionSaveReader().read(filled)
        val generated = record(0, 101, "Falcomon", 1, 125, 137, 69, 44, 79, 20)
        val edit = LostEvolutionSaveEdits.insertGeneratedRecord(filled, observation2.contentRevision,
            java.util.Base64.getEncoder().encodeToString(generated))
        val after = LostEvolutionSaveReader().read(edit.candidateBytes)
        assertEquals(SaveContainer.FARM, requireNotNull(after.records.singleOrNull {
            it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 101L
        }).container)
    }

    @Test fun retainedIdentitiesKeepTheGameAllocatorAhead() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        val retained = record(9, 77, "Agumon", 4, 168, 157, 94, 54, 56, 20)
        val edit = LostEvolutionSaveEdits.insertRetainedRecord(source, observation.contentRevision,
            java.util.Base64.getEncoder().encodeToString(retained))
        val counter = LostEvolution.u64(edit.candidateBytes,
            LostEvolution.COPY_STRIDE + LostEvolution.NEXT_ID_SAVE_OFFSET)
        assertEquals(10L, counter and 0xFFFFFFFFL)
        assertEquals(identityText(9), edit.nativeIdentity)
    }

    @Test fun replacingARecordKeepsItsIdentity() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        val agumon = observation.records.single {
            it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 77L
        }
        val bytes = java.util.Base64.getDecoder().decode(requireNotNull(agumon.nativeRecordBase64))
        LostEvolution.putU32(bytes, LostEvolution.OFFSET_EXP, 999)
        LostEvolution.putU16(bytes, LostEvolution.OFFSET_MAX_HP, 200)
        val edit = LostEvolutionSaveEdits.replaceRetainedRecord(source, observation.contentRevision,
            java.util.Base64.getEncoder().encodeToString(bytes))
        val after = LostEvolutionSaveReader().read(edit.candidateBytes)
        val replaced = after.records.single {
            it.nativeParameters?.saveBlock == 2 && it.nativeIdentity == agumon.nativeIdentity
        }
        assertEquals(999L, replaced.totalExp)
        assertEquals(200, requireNotNull(replaced.nativeParameters).maxHp)
        assertArrayEquals(source, LostEvolutionSaveEdits.restore(edit.candidateBytes, edit))
    }

    @Test fun refusingTheLastIndividualAndForeignBytes() {
        val single = fixture().copyOf()
        // remove two of the three from copy 2, then the third must be refused
        var current = single
        var revision = LostEvolutionSaveReader().read(current).contentRevision
        repeat(2) {
            val observation = LostEvolutionSaveReader().read(current)
            val target = observation.records.first { it.nativeParameters?.saveBlock == 2 }
            current = LostEvolutionSaveEdits.removeRecord(current, revision, target.recordKey).candidateBytes
            revision = LostEvolutionSaveReader().read(current).contentRevision
        }
        val observation = LostEvolutionSaveReader().read(current)
        val last = observation.records.single { it.nativeParameters?.saveBlock == 2 }
        assertThrows(IllegalArgumentException::class.java) {
            LostEvolutionSaveEdits.removeRecord(current, observation.contentRevision, last.recordKey)
        }
        val corrupted = current.copyOf()
        corrupted[LostEvolution.TAG_OFFSET] = 0
        corrupted[LostEvolution.COPY_STRIDE + LostEvolution.TAG_OFFSET] = 0
        assertThrows(SaveReadException::class.java) { LostEvolutionSaveReader().read(corrupted) }
    }

    @Test fun admissionMovesAFarmResidentIntoTheParty() {
        val source = fixture()
        val observation = LostEvolutionSaveReader().read(source)
        val toy = observation.records.single {
            it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 90L
        }
        assertEquals(SaveContainer.FARM, toy.container)
        val edit = LostEvolutionSaveEdits.admitToParty(source, observation.contentRevision, toy.recordKey)
        val after = LostEvolutionSaveReader().read(edit.candidateBytes)
        val admitted = after.records.single {
            it.nativeParameters?.saveBlock == 2 && it.nativeSpeciesId == 90L
        }
        assertEquals(SaveContainer.PARTY, admitted.container)
        assertArrayEquals(source, LostEvolutionSaveEdits.restore(edit.candidateBytes, edit))
    }

    @Test fun realSavePlansAndRestoresAMoveoutWhenAvailable() {
        val directory = System.getenv("LE_SAVE_DIR") ?: return
        val files = java.io.File(directory).listFiles { file -> file.isFile && file.name.endsWith(".sav") }
            ?: return
        val save = files.firstOrNull { it.name.contains("Lost Evolution") } ?: return
        val source = save.readBytes()
        val observation = LostEvolutionSaveReader().read(source)
        val copy = requireNotNull(NdsStoryCopySelection.authoritativeCopy(observation))
        val scoped = observation.records.filter { it.nativeParameters?.saveBlock == copy }
        assertTrue(scoped.size >= 2)
        val target = scoped.last()
        val edit = LostEvolutionSaveEdits.removeRecord(source, observation.contentRevision, target.recordKey)
        val candidate = edit.candidateBytes
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, 0, profile))
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, LostEvolution.COPY_STRIDE, profile))
        val after = LostEvolutionSaveReader().read(candidate)
        val afterScoped = after.records.filter { it.nativeParameters?.saveBlock == 2 }
        assertEquals(scoped.size - 1, afterScoped.size)
        assertTrue(afterScoped.none { it.nativeIdentity == target.nativeIdentity })
        assertEquals(source.size, candidate.size)
        assertArrayEquals(source, LostEvolutionSaveEdits.restore(candidate, edit))
    }

    /**
     * Replays the exact first device send that failed with "could not update this source".
     * Gated by LE_SAVE_DIR plus LE_REPLAY_RECORD (base64 record) and optionally
     * LE_REPLAY_CANDIDATE (the candidate the buggy run produced): the fixed editor must leave the
     * source untouched and reproduce the same candidate bytes.
     */
    @Test fun realSaveReplaysTheFirstDeviceSendDeterministicallyWhenAvailable() {
        val directory = System.getenv("LE_SAVE_DIR") ?: return
        val recordFile = System.getenv("LE_REPLAY_RECORD")?.let { java.io.File(it) } ?: return
        val files = java.io.File(directory).listFiles { file -> file.isFile && file.name.endsWith(".sav") }
            ?: return
        val save = files.firstOrNull { it.name.contains("Lost Evolution") } ?: return
        val source = save.readBytes()
        val snapshot = source.copyOf()
        val observation = LostEvolutionSaveReader().read(source)
        val recordBase64 = recordFile.readText().trim()
        val edit = LostEvolutionSaveEdits.insertGeneratedRecord(source, observation.contentRevision, recordBase64)
        assertArrayEquals(snapshot, source)
        val candidate = edit.candidateBytes
        System.getenv("LE_REPLAY_CANDIDATE")?.let { path ->
            assertArrayEquals(java.io.File(path).readBytes(), candidate)
        }
        val copy = requireNotNull(NdsStoryCopySelection.authoritativeCopy(observation))
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, (copy - 1) * LostEvolution.COPY_STRIDE, profile))
        val after = LostEvolutionSaveReader().read(candidate)
        assertTrue(after.records.any {
            it.nativeParameters?.saveBlock == copy && it.nativeIdentity == edit.nativeIdentity
        })
        snapshot.fill(0)
    }

    @Test fun realSaveDecodesWhenAvailable() {
        val directory = System.getenv("LE_SAVE_DIR") ?: return
        val files = java.io.File(directory).listFiles { file -> file.isFile && file.name.endsWith(".sav") }
            ?: return
        val save = files.firstOrNull { it.name.contains("Lost Evolution") } ?: return
        val observation = LostEvolutionSaveReader().read(save.readBytes())
        assertEquals(true, observation.copyIntegrity.values.any { it })
        val active = observation.records.filter { it.nativeParameters?.saveBlock == 2 }
        println("LE real save: " + active.joinToString("; ") {
            "${it.nativeSpeciesId}:${it.storedName}:lv${it.level}:${it.container}:${it.nativeParameters?.maxHp}"
        })
        val names = observation.records.map { it.storedName }.toSet()
        assertTrue("expected a known Lost Evolution roster, got $names", names.isNotEmpty())
        for (record in observation.records.filter { it.membership == RecordMembership.ACTIVE_RECORD }) {
            assertTrue(record.storedName.isNotBlank())
            assertTrue(record.nativeSpeciesId in 0 until LostEvolution.SPECIES_COUNT)
            assertNotNull(record.nativeRecordBase64)
            assertNull(record.issues.firstOrNull { it == RecordIssue.IMPLAUSIBLE_VALUES })
        }
    }
}
