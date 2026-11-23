package com.tamerslab.gamesave

import org.junit.Assert.*
import org.junit.Test

/**
 * Dawn/Dusk profile behavior, verified against the byte layout recovered from the shared ARM9
 * engine code and cross-checked on two retail saves (see analysis/ds2/DAWN_DUSK_SAVE_LAYOUT.md).
 * All fixtures are synthetic images built from that layout, never private save bytes.
 */
class Ds2SaveEditsTest {
    private val profile get() = NdsStoryProfiles.DAWN_DUSK
    private val COPY_BYTES = 0x7B2C
    private val COPY_STRIDE = 0x7B80
    private val STRIDE = 0x164

    private fun put16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun put32(bytes: ByteArray, at: Int, value: Long) {
        repeat(4) { bytes[at + it] = ((value shr (it * 8)) and 0xFF).toByte() }
    }

    private fun nameCodes(text: String): List<Int> = text.map { char ->
        when (char) {
            in 'A'..'Z' -> 0x20 + (char - 'A')
            in 'a'..'z' -> 0x3A + (char - 'a')
            else -> 0x00
        }
    }

    private fun writeRecord(bytes: ByteArray, slot: Int, id: Long, species: Long, name: String, level: Int) {
        val base = 0x288 + slot * 0x164
        put32(bytes, base, id)
        put32(bytes, base + 4, species)
        nameCodes(name).take(8).forEachIndexed { index, code -> put16(bytes, base + 8 + index * 2, code) }
        put16(bytes, base + 0x18, 0xFFFF)
        put32(bytes, base + 0x3C, 300)          // hp
        put32(bytes, base + 0x40, 300)          // max hp
        put16(bytes, base + 0x44, 200)          // mp
        put16(bytes, base + 0x46, 200)          // max mp
        put16(bytes, base + 0x48, 100)          // attack
        put16(bytes, base + 0x4A, 90)           // defense
        put16(bytes, base + 0x4C, 80)           // spirit
        put16(bytes, base + 0x4E, 70)           // speed
        put16(bytes, base + 0x50, 5)            // species unk50
        put16(bytes, base + 0x52, 40)           // max level / aptitude
        put16(bytes, base + 0x70, 0x21)         // unk70 == 33
        put16(bytes, base + 0x74, 0)
        put16(bytes, base + 0x76, level)        // level
        put32(bytes, base + 0x9C, 646)          // exp to next
        put32(bytes, base + 0xB0, 0x18)         // history stage flags
        put32(bytes, base + 0xB4 + (if (species == 0x9CL) 1 else 2) * 4, species)
        bytes[base + 0x1B] = 0                  // exp curve
        bytes[base + 0x1E] = if (species == 0x9CL) 1 else 2
    }

    /** One populated copy (the shape of the supplied fresh retail saves), sealed like the game. */
    private fun fixture(): ByteArray = ByteArray(0x10000) { 0xFF.toByte() }.also { bytes ->
        bytes.fill(0, 0, 0x7B2C)
        "DSDIG2\u0000\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x10)
        put32(bytes, 0x18, 0)
        "Fafa".forEachIndexed { index, char -> put16(bytes, 0x238 + index * 2, 0x20 + (char - 'a')) }
        put16(bytes, 0x23C, 0xFFFF)
        put32(bytes, 0x244, 100)
        put32(bytes, 0x25C, 0x40)
        put32(bytes, 0x260, 0x3F)
        put32(bytes, 0x264, 0x40)
        writeRecord(bytes, 0, 0x3F, 0x9C, "Coronamon", 20)
        writeRecord(bytes, 1, 0x40, 0xE2, "Reppamon", 35)
        NdsStoryCopyIntegrity.seal(bytes, 0, profile)
    }

    @Test fun readsTheDawnDuskLayoutAndParty() {
        val bytes = fixture()
        val observation = NdsStoryWorldDs2Reader().read(bytes)
        assertEquals("digimon_world_ds2_nds", observation.gameId)
        assertEquals("ds2-usa-raw-v1", observation.formatProfile)
        assertTrue(observation.copyIntegrity[1] == true)
        assertEquals(2, observation.activeCount)
        val coronamon = observation.records.single { it.storedName == "Coronamo" }
        assertEquals(SaveContainer.PARTY, coronamon.container)
        assertEquals(0x9CL, coronamon.nativeSpeciesId)
        assertEquals(20, coronamon.level)
        assertEquals(1, coronamon.position)
        assertEquals("save-local:63", coronamon.nativeIdentity)
        val parameters = requireNotNull(coronamon.nativeParameters)
        assertEquals(300, parameters.maxHp)
        assertEquals(100, parameters.attack)
        assertEquals(40, parameters.aptitude)
        assertEquals(9, parameters.expCounters.size)
    }

    @Test fun worldDsReaderAndDawnDuskReaderNeverDecodeEachOthersSaves() {
        val ds2 = fixture()
        assertEquals(SaveReadFailure.UNRECOGNIZED_LAYOUT, assertThrows(SaveReadException::class.java) {
            WorldDsSaveReader().read(ds2)
        }.reason)
        // A World-DS-shaped file must not be claimed by the Dawn/Dusk reader either.
        val worldDs = WorldDsSaveReaderTestFixture.minimal()
        assertThrows(SaveReadException::class.java) { NdsStorySaveReader(profile).read(worldDs) }
        // The shared registry resolves each family to exactly one reader.
        assertEquals("digimon_world_ds2_nds", SaveReaderRegistry().read(ds2.inputStream()).gameId)
        assertEquals(WorldDsSaveReader.GAME_ID, SaveReaderRegistry().read(worldDs.inputStream()).gameId)
    }

    @Test fun removalClearsPartyAndRecordAndRestoresByteExactly() {
        val source = fixture()
        val observation = NdsStoryWorldDs2Reader().read(source)
        val edit = Ds2SaveEdits.removeForResearch(source, observation.contentRevision, "copy:1:record:1")
        val candidate = edit.candidateBytes
        assertFalse(candidate.contentEquals(source))
        val after = NdsStoryWorldDs2Reader().read(candidate)
        assertEquals(1, after.activeCount)
        assertEquals("save-local:63", after.records.single().nativeIdentity)
        // party array compaction: the removed identity is gone, the keeper stays
        assertEquals(listOf(0x3FL, 0L), List(2) { index ->
            (0 until 4).fold(0L) { value, i -> value or ((candidate[0x260 + index * 4 + i].toLong() and 255) shl (8 * i)) }
        })
        // header resealed and sequence bumped
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, 0, profile))
        assertEquals(1L, (0 until 4).fold(0L) { value, i -> value or ((candidate[0x18 + i].toLong() and 255) shl (8 * i)) })
        assertArrayEquals(source, Ds2SaveEdits.restore(candidate, edit))
    }

    @Test fun removalRefusesTheLastPartyMemberAndStaleRevisions() {
        val source = fixture()
        val observation = NdsStoryWorldDs2Reader().read(source)
        val first = Ds2SaveEdits.removeForResearch(source, observation.contentRevision, "copy:1:record:0")
        val remaining = NdsStoryWorldDs2Reader().read(first.candidateBytes)
        assertThrows(IllegalArgumentException::class.java) {
            Ds2SaveEdits.removeForResearch(first.candidateBytes, remaining.contentRevision, "copy:1:record:1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Ds2SaveEdits.removeForResearch(source, "stale-revision", "copy:1:record:1")
        }
    }

    @Test fun retainedRecordInsertsIntoFreeSlotAndJoinsTheParty() {
        val destination = fixture()
        val removed = Ds2SaveEdits.removeForResearch(destination,
            NdsStoryWorldDs2Reader().read(destination).contentRevision, "copy:1:record:1")
        val retained = "copy:1:record:1"
        // the removed record bytes are exactly reinsertable
        val original = NdsStoryWorldDs2Reader().read(destination).records.single { it.recordKey == retained }
        val base64 = requireNotNull(original.nativeRecordBase64)
        val insertion = Ds2SaveEdits.insertRetainedRecord(removed.candidateBytes,
            NdsStoryWorldDs2Reader().read(removed.candidateBytes).contentRevision, base64)
        val after = NdsStoryWorldDs2Reader().read(insertion.candidateBytes)
        assertEquals(2, after.activeCount)
        assertTrue(after.records.any { it.nativeIdentity == "save-local:64" && it.container == SaveContainer.PARTY })
        assertArrayEquals(removed.candidateBytes, Ds2SaveEdits.restore(insertion.candidateBytes, insertion))
    }

    /**
     * The shape a played save actually has: both card slots written, the newer one authoritative.
     * The older slot legitimately still contains the roster from before the last move-out.
     */
    private fun dualCopyFixture(): ByteArray = fixture().also { bytes ->
        // Populate two more records so a removal still leaves a usable party behind.
        writeRecord(bytes, 2, 0x42, 0xE2, "Renamon", 21)
        writeRecord(bytes, 3, 0x43, 0x14B, "Kyubimon", 22)
        put32(bytes, 0x260 + 2 * 4, 0x42)
        put32(bytes, 0x260 + 3 * 4, 0x43)
        put32(bytes, 0x25C, 0x43)
        NdsStoryCopyIntegrity.seal(bytes, 0, profile)
        // copy 2 mirrors copy 1, then becomes the newest with one record already moved out.
        bytes.copyOfRange(0, COPY_BYTES).copyInto(bytes, COPY_STRIDE)
        val removed = 0x40L
        for (slot in 0 until 0x7B2C / STRIDE) {
            val base = COPY_STRIDE + 0x288 + slot * STRIDE
            if (base + STRIDE > bytes.size) break
            if (put32Read(bytes, base) == removed) {
                bytes.fill(0, base, base + STRIDE)
                put32(bytes, base + 4, 0xFFFFFFFFL)
            }
        }
        for (index in 0 until 10) {
            val at = COPY_STRIDE + 0x260 + index * 4
            if (put32Read(bytes, at) == removed) put32(bytes, at, 0)
        }
        put32(bytes, COPY_STRIDE + 0x25C, 0x40)
        put32(bytes, COPY_STRIDE + 0x18, 5)
        NdsStoryCopyIntegrity.seal(bytes, COPY_STRIDE, profile)
        put32(bytes, 0x18, 4)
        NdsStoryCopyIntegrity.seal(bytes, 0, profile)
    }

    private fun put32Read(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, index -> value or ((bytes[at + index].toLong() and 255) shl (8 * index)) }

    @Test fun dualCopySavesAreEditedOnTheAuthoritativeSlotOnly() {
        val source = dualCopyFixture()
        val observation = NdsStoryWorldDs2Reader().read(source)
        assertEquals(2, observation.copyIntegrity.size)
        assertEquals(2, NdsStoryCopySelection.authoritativeCopy(observation))
        val edit = Ds2SaveEdits.removeForResearch(source, observation.contentRevision, "copy:2:record:0")
        val candidate = edit.candidateBytes
        // both card copies are edited and sealed, because the retail loader may fall back to either
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, 0, profile))
        assertTrue(NdsStoryCopyIntegrity.isValid(candidate, COPY_STRIDE, profile))
        val after = NdsStoryWorldDs2Reader().read(candidate)
        for (copy in listOf(1, 2)) {
            val before = observation.records.count { it.nativeParameters?.saveBlock == copy }
            val records = after.records.filter { it.nativeParameters?.saveBlock == copy }
            assertEquals(before - 1, records.size)
            assertTrue(records.none { it.nativeIdentity == "save-local:63" })
        }
        assertArrayEquals(source, Ds2SaveEdits.restore(candidate, edit))
    }

    @Test fun returningARecordThatOnlyTheOlderSlotStillHasInsertsIntoTheAuthority() {
        val source = dualCopyFixture()
        val observation = NdsStoryWorldDs2Reader().read(source)
        // 0x40 was moved out of the authoritative slot but the stale slot still lists it.
        val stale = observation.records.single {
            it.nativeIdentity == "save-local:64" && it.nativeParameters?.saveBlock == 1
        }
        val base64 = requireNotNull(stale.nativeRecordBase64)
        val edit = Ds2SaveEdits.insertRetainedRecord(source, observation.contentRevision, base64)
        val after = NdsStoryWorldDs2Reader().read(edit.candidateBytes)
        for (copy in listOf(1, 2)) {
            assertTrue(after.records.any {
                it.nativeIdentity == "save-local:64" && it.nativeParameters?.saveBlock == copy &&
                    it.container == SaveContainer.PARTY
            })
        }
    }

    @Test fun replacingARecordOnADualCopySaveOnlyTouchesTheAuthoritativeSlot() {
        val source = dualCopyFixture()
        val observation = NdsStoryWorldDs2Reader().read(source)
        val existing = observation.records.single {
            it.nativeIdentity == "save-local:63" && it.nativeParameters?.saveBlock == 2
        }
        val bytes = Ds2RecordCodec.decodeBase64(requireNotNull(existing.nativeRecordBase64))
        bytes[0x76] = 44 // raise the stored level
        val edit = Ds2SaveEdits.replaceRetainedRecord(source, observation.contentRevision,
            Ds2RecordCodec.base64(bytes))
        val after = NdsStoryWorldDs2Reader().read(edit.candidateBytes)
        for (copy in listOf(1, 2)) {
            assertEquals(44, after.records.single {
                it.nativeIdentity == "save-local:63" && it.nativeParameters?.saveBlock == copy
            }.level)
        }
        assertArrayEquals(source, Ds2SaveEdits.restore(edit.candidateBytes, edit))
    }
    private val coronamonDefaults = Ds2RecordCodec.Ds2RecordDefaults(
        unk1A = 0, unk50 = 5, abilities = listOf(1, 36, 255, 255), growth = 0,
        skills = listOf(32, 52, 111, 59), resistances = listOf(1000, 200, 900, 700, 700, 500, 400, 500),
        expCurve = 0, baseLevel = 1, maxLevel = 32, unk27 = 189,
    )

    @Test fun generatedRecordMatchesTheEnginesCreationShape() {
        val stats = Ds2RecordCodec.Ds2GeneratedStats(level = 12, hp = 300, maxHp = 300, mp = 200, maxMp = 200,
            attack = 100, defense = 90, spirit = 80, speed = 70, maxLevel = 32)
        val generated = Ds2RecordCodec.generate(0, 156, "Coronamon", "Coronamon", stats,
            defaults = coronamonDefaults, lineage = listOf(0x56))
        println("GENERATED HEX: " + generated.bytes.joinToString("") { "%02x".format(it) })
        val b = generated.bytes
        fun u8(at: Int) = b[at].toInt() and 255
        fun u16(at: Int) = (b[at].toInt() and 255) or ((b[at + 1].toInt() and 255) shl 8)
        fun u32(at: Int) = (0 until 4).fold(0L) { v, i -> v or ((b[at + i].toLong() and 255) shl (8 * i)) }
        assertEquals(0x164, b.size)
        assertEquals(0L, u32(0))
        assertEquals(156L, u32(4))
        assertEquals("Coronamon", generated.name)
        val storedCodes = (0 until 9).map { (generated.bytes[0x08 + it * 2].toInt() and 255) or
            ((generated.bytes[0x09 + it * 2].toInt() and 255) shl 8) }.takeWhile { it != 0xFFFF }
        assertEquals("Coronamo", Ds2RecordCodec.decodeName(storedCodes))
        assertEquals(0, u8(0x1A)); assertEquals(0, u8(0x1B)); assertEquals(0, u8(0x1C))
        assertEquals(0, u8(0x1D))
        assertEquals(1, u8(0x1E))
        assertEquals(listOf(1, 36, 0xFFFF, 0xFFFF), (0 until 4).map { u16(0x22 + it * 2) })
        assertEquals(156L, u32(0x2C))
        assertEquals(listOf(-1L, -1L, -1L), (1 until 4).map { u32(0x2C + it * 4).toInt().toLong() })
        assertEquals(300L, u32(0x3C)); assertEquals(300L, u32(0x40))
        assertEquals(200, u16(0x44)); assertEquals(200, u16(0x46))
        assertEquals(listOf(100, 90, 80, 70), (0 until 4).map { u16(0x48 + it * 2) })
        assertEquals(5, u16(0x50)); assertEquals(32, u16(0x52))
        assertEquals(listOf(32, 52, 111, 59), (0 until 4).map { u16(0x54 + it * 2) })
        assertEquals(0xFFFF, u16(0x5C))
        assertEquals(0x21, u16(0x70)); assertEquals(0, u16(0x74)); assertEquals(12, u16(0x76))
        assertEquals(0L, u32(0x78)); assertEquals(0L, u32(0x98))
        assertEquals(256L, u32(0x9C))   // curve 0 value for level 12
        assertEquals(0x18L, u32(0xB0))
        assertEquals(listOf(0x56L, 0x9CL, -1L, -1L, -1L), (0 until 5).map { u32(0xB4 + it * 4).toInt().toLong() })
        assertEquals(0x0040, u16(0xD2))  // bitmap word 5, bit 6 -> species 0x56
        assertEquals(0x1000, u16(0xDA))  // bitmap word 9, bit 12 -> species 0x9C
        assertEquals(1, u8(0x107))
        assertEquals(listOf(1000, 200, 900, 700, 700, 500, 400, 500), (0 until 8).map { u16(0x108 + it * 2) })
        assertEquals(0, u16(0x120)); assertEquals(0L, u32(0x124))
    }

    @Test fun bankDestinationStoresTheRecordWithoutAPartyReferenceAndFarmIsRefused() {
        val destination = fixture()
        val stats = Ds2RecordCodec.Ds2GeneratedStats(level = 12, hp = 300, maxHp = 300, mp = 200, maxMp = 200,
            attack = 100, defense = 90, spirit = 80, speed = 70, maxLevel = 32)
        val generated = Ds2RecordCodec.generate(0, 156, "Renamon", "Renamon", stats,
            defaults = coronamonDefaults, lineage = listOf(0x56))
        val edit = Ds2SaveEdits.insertGeneratedRecord(destination,
            NdsStoryWorldDs2Reader().read(destination).contentRevision,
            Ds2RecordCodec.base64(generated.bytes), SaveContainer.BANK)
        val inserted = NdsStoryWorldDs2Reader().read(edit.candidateBytes)
            .records.single { it.nativeIdentity == "save-local:65" }
        assertEquals(SaveContainer.UNASSIGNED, inserted.container)
        assertThrows(IllegalArgumentException::class.java) {
            Ds2SaveEdits.insertGeneratedRecord(destination,
                NdsStoryWorldDs2Reader().read(destination).contentRevision,
                Ds2RecordCodec.base64(generated.bytes), SaveContainer.FARM)
        }
    }
    @Test fun generatedRecordInsertsWithTheNextIdentityAndJoinsTheParty() {
        val destination = fixture()
        val stats = Ds2RecordCodec.Ds2GeneratedStats(level = 12, hp = 300, maxHp = 300, mp = 200, maxMp = 200,
            attack = 100, defense = 90, spirit = 80, speed = 70, maxLevel = 32)
        val generated = Ds2RecordCodec.generate(0, 156, "Renamon", "Renamon", stats,
            defaults = coronamonDefaults, lineage = listOf(0x56))
        val edit = Ds2SaveEdits.insertGeneratedRecord(destination,
            NdsStoryWorldDs2Reader().read(destination).contentRevision,
            Ds2RecordCodec.base64(generated.bytes))
        val after = NdsStoryWorldDs2Reader().read(edit.candidateBytes)
        // fixture lastId is 0x40, so the new record takes 0x41
        val inserted = after.records.single { it.nativeIdentity == "save-local:65" }
        assertEquals(SaveContainer.PARTY, inserted.container)
        assertEquals(156L, inserted.nativeSpeciesId)
        assertEquals("Renamon", inserted.storedName)
        assertEquals(12, inserted.level)
        assertEquals(65L, (0 until 4).fold(0L) { v, i ->
            v or ((edit.candidateBytes[0x25C + i].toLong() and 255) shl (8 * i))
        })
        assertTrue(NdsStoryCopyIntegrity.isValid(edit.candidateBytes, 0, profile))
        assertArrayEquals(destination, Ds2SaveEdits.restore(edit.candidateBytes, edit))
    }

    @Test fun seededGenerationKeepsIdentityAndUnknownWindows() {
        val destination = fixture()
        val observation = NdsStoryWorldDs2Reader().read(destination)
        val original = observation.records.single { it.nativeIdentity == "save-local:64" }
        val seed = Ds2RecordCodec.decodeBase64(requireNotNull(original.nativeRecordBase64))
        seed[0x140] = 0x5A  // an unknown tail byte the engine never rewrites
        val stats = Ds2RecordCodec.Ds2GeneratedStats(level = 40, hp = 700, maxHp = 700, mp = 300, maxMp = 300,
            attack = 200, defense = 180, spirit = 160, speed = 140, maxLevel = 45)
        val defaults = Ds2RecordCodec.Ds2RecordDefaults(unk1A = 0, unk50 = 5,
            abilities = listOf(0x11, 0x4E, 0xFF, 0xFF), growth = 4, skills = listOf(27, 24, 17, 0xFFFF),
            resistances = List(8) { 500 }, expCurve = 0, baseLevel = 1, maxLevel = 45, unk27 = 189)
        val generated = Ds2RecordCodec.generate(0, 0xE2, "Reppamon", "Reppamon", stats, seed,
            defaults, lineage = listOf(0x91, 0x4A))
        assertEquals(64L, Ds2RecordCodec.localId(generated.bytes))
        assertEquals(0x5A, generated.bytes[0x140].toInt() and 255)
        val edit = Ds2SaveEdits.replaceRetainedRecord(destination, observation.contentRevision,
            Ds2RecordCodec.base64(generated.bytes))
        val after = NdsStoryWorldDs2Reader().read(edit.candidateBytes)
        val replaced = after.records.single { it.nativeIdentity == "save-local:64" }
        assertEquals(40, replaced.level)
        assertEquals(SaveContainer.PARTY, replaced.container)
        assertEquals(0x5A, Ds2RecordCodec.decodeBase64(requireNotNull(replaced.nativeRecordBase64))[0x140].toInt() and 255)
        assertArrayEquals(destination, Ds2SaveEdits.restore(edit.candidateBytes, edit))
    }
    @Test fun generatedSendsThroughTheRegisteredEditorAssignAnIdentity() {
        // Regression: the editor facade once routed generated inserts to the retained path, which
        // rejects identity 0 ("A retained record must carry a nonzero local identity").
        val destination = dualCopyFixture()
        val observation = NdsStoryWorldDs2Reader().read(destination)
        val editor = requireNotNull(NdsStorySaveEditors.forObservation(observation))
        val stats = Ds2RecordCodec.Ds2GeneratedStats(level = 30, hp = 500, maxHp = 500, mp = 250, maxMp = 250,
            attack = 150, defense = 130, spirit = 110, speed = 90, maxLevel = 32)
        val generated = Ds2RecordCodec.generate(0, 156, "Renamon", "Renamon", stats,
            defaults = coronamonDefaults, lineage = listOf(0x56))
        assertEquals(0L, Ds2RecordCodec.localId(generated.bytes))
        val edit = editor.insertGenerated(destination, observation.contentRevision,
            Ds2RecordCodec.base64(generated.bytes))
        val after = NdsStoryWorldDs2Reader().read(edit.candidateBytes)
        val inserted = after.records.single {
            it.nativeIdentity == edit.nativeIdentity && it.nativeParameters?.saveBlock == 2
        }
        assertTrue(inserted.nativeIdentity != null)
        assertEquals(30, inserted.level)
        assertEquals(156L, inserted.nativeSpeciesId)
        assertEquals(SaveContainer.PARTY, inserted.container)
    }
    @Test fun engineProducedSequenceTieWithEquivalentCopiesIsResolvedToTheLowestCopy() {
        // The engine stores counter 1 when it saves into an erased slot, so a save edited by the app
        // to counter 1 and then saved by the game ends with both slots at 1 and the same roster.
        val bytes = fixture().also { image ->
            image.copyOfRange(0, COPY_BYTES).copyInto(image, COPY_STRIDE)
            // party ring order differs between the game's copies; content is the same
            put32(image, COPY_STRIDE + 0x260, 0x40)
            put32(image, COPY_STRIDE + 0x260 + 4, 0x3F)
            put32(image, 0x260, 0x3F)
            put32(image, 0x260 + 4, 0x40)
            put32(image, 0x18, 1)
            put32(image, COPY_STRIDE + 0x18, 1)
            NdsStoryCopyIntegrity.seal(image, 0, profile)
            NdsStoryCopyIntegrity.seal(image, COPY_STRIDE, profile)
        }
        val observation = NdsStoryWorldDs2Reader().read(bytes)
        assertTrue(NdsStoryCopySelection.equivalentCopies(observation, 1, 2))
        assertEquals(1, NdsStoryCopySelection.latestCopy(observation))
        assertEquals(1, NdsStoryCopySelection.authoritativeCopy(observation))
        // and the writer can now move a record out, bumping the chosen copy into a unique newest slot
        val edit = Ds2SaveEdits.removeForResearch(bytes, observation.contentRevision, "copy:1:record:1")
        assertEquals(2L, (0 until 4).fold(0L) { value, i ->
            value or ((edit.candidateBytes[i + 0x18].toLong() and 255) shl (8 * i))
        })
        val after = NdsStoryWorldDs2Reader().read(edit.candidateBytes)
        assertEquals(listOf(2L, 2L), listOf(1, 2).map { copy ->
            (0 until 4).fold(0L) { value, i ->
                val at = (copy - 1) * COPY_STRIDE + 0x18
                value or ((edit.candidateBytes[at + i].toLong() and 255) shl (8 * i))
            }
        })
        assertEquals(1, after.records.count { it.nativeParameters?.saveBlock == 1 })
        assertEquals(1, after.records.count { it.nativeParameters?.saveBlock == 2 })
        assertArrayEquals(bytes, Ds2SaveEdits.restore(edit.candidateBytes, edit))
    }

    @Test fun aTieBetweenDifferentRostersStillHasNoDefensibleCopy() {
        val bytes = fixture().also { image ->
            image.copyOfRange(0, COPY_BYTES).copyInto(image, COPY_STRIDE)
            // copy 2 lost a record the other copy still has: the copies disagree
            val base = COPY_STRIDE + 0x288 + 0x164
            image.fill(0, base, base + 0x164)
            put32(image, base + 4, 0xFFFFFFFFL)
            for (index in 0 until 10) {
                val at = COPY_STRIDE + 0x260 + index * 4
                if (put32Read(image, at) == 0x40L) put32(image, at, 0)
            }
            put32(image, 0x18, 1)
            put32(image, COPY_STRIDE + 0x18, 1)
            NdsStoryCopyIntegrity.seal(image, 0, profile)
            NdsStoryCopyIntegrity.seal(image, COPY_STRIDE, profile)
        }
        val observation = NdsStoryWorldDs2Reader().read(bytes)
        assertNull(NdsStoryCopySelection.latestCopy(observation))
        assertNull(NdsStoryCopySelection.authoritativeCopy(observation))
        assertThrows(IllegalArgumentException::class.java) {
            Ds2SaveEdits.removeForResearch(bytes, observation.contentRevision, "copy:1:record:1")
        }
    }
    @Test fun corruptHeaderSaveIsNotEditableButStillReadable() {
        val bytes = fixture()
        bytes[0x300] = (bytes[0x300] + 1).toByte()
        val observation = NdsStoryWorldDs2Reader().read(bytes)
        assertFalse(observation.copyIntegrity[1] ?: true)
        assertThrows(IllegalArgumentException::class.java) {
            Ds2SaveEdits.removeForResearch(bytes, observation.contentRevision, "copy:1:record:1")
        }
    }
}

/** Reader alias used by the tests, so the production entry point stays explicit. */
private typealias NdsStoryWorldDs2Reader = WorldDs2SaveReader

/** Minimal World DS synthetic save used only to prove cross-family rejection. */
private object WorldDsSaveReaderTestFixture {
    fun minimal(): ByteArray = ByteArray(0x10000) { 0xFF.toByte() }.also { bytes ->
        bytes.fill(0, 0, 0x7DC0)
        repeat(16) { bytes[it] = (it + 1).toByte() }
        fun put32(at: Int, value: Long) = repeat(4) { bytes[at + it] = ((value shr (it * 8)) and 0xFF).toByte() }
        put32(0x208, 10)
        put32(0x20C, 7)
        put32(0x234, 7)
        put32(0x234 + 4, 20)
        bytes[0x234 + 0x74] = 3
        bytes[0x234 + 0x75] = 0
    }
}
