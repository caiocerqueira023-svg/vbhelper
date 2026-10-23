package com.github.nacabaro.vbhelper.gamesave

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class WorldDsPartyEditsTest {
    private fun put(bytes: ByteArray, at: Int, value: Long, width: Int = 4) {
        repeat(width) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, index -> value or ((bytes[at + index].toLong() and 255) shl (8 * index)) }

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

    private fun recordInto(bytes: ByteArray, at: Int, id: Long) {
        // Unknown native history/ancestry bytes must survive archival and restoration too.
        bytes.fill(0x35, at, at + 0x180)
        put(bytes, at, id); put(bytes, at + 4, 20)
        put(bytes, at + 8, 0x63, 2); put(bytes, at + 10, 0xFFFF, 2)
        put(bytes, at + 0x3C, 100); put(bytes, at + 0x40, 110)
        listOf(0x44, 0x46, 0x48, 0x4A, 0x4C, 0x4E).forEach { put(bytes, at + it, 20, 2) }
        put(bytes, at + 0x52, 25, 2); put(bytes, at + 0x74, 3, 2)
        repeat(8) { put(bytes, at + 0x78 + it * 4, (it * 11).toLong()) }
        put(bytes, at + 0x108, 0, 2)
    }

    private fun expectation(id: Long): ByteArray = ByteArray(0x180).also { recordInto(it, 0, id) }

    private fun retained(id: Long): String = Base64.getEncoder().encodeToString(expectation(id))

    private fun minimalDestination(lastId: Long, ids: List<Long>): ByteArray = ByteArray(65536).also { bytes ->
        repeat(16) { bytes[it] = (it + 1).toByte() }
        put(bytes, 0x208, lastId)
        ids.forEachIndexed { index, id ->
            put(bytes, 0x20C + index * 4, id)
            recordInto(bytes, 0x234 + index * 0x180, id)
        }
    }

    private fun fixture(): ByteArray = ByteArray(65536) { 0xFF.toByte() }.also { bytes ->
        bytes.fill(0, 0, 0x7DC0)
        repeat(16) { bytes[it] = (it + 1).toByte() }
        put(bytes, 0x208, 30)
        listOf(7L, 12L, 20L).forEachIndexed { index, id ->
            put(bytes, 0x20C + index * 4, id)
            recordInto(bytes, 0x234 + index * 0x180, id)
        }
    }

    private fun seal(bytes: ByteArray, base: Int) {
        val digest = java.security.MessageDigest.getInstance("MD5")
        digest.update(ByteArray(16))
        digest.update(bytes, base + 16, WorldDsSaveReader.SAVE_BYTES - 16)
        digest.digest().copyInto(bytes, base)
    }

    @Test fun readerExposesEachCopysSaveCounterForNewestCopySelection() {
        val source = fixture().also { it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE) }
        put(source, 0x18, 10)
        put(source, WorldDsSaveReader.COPY_STRIDE + 0x18, 11)
        seal(source, 0)
        seal(source, WorldDsSaveReader.COPY_STRIDE)
        val observation = WorldDsSaveReader().read(source)
        assertEquals(10L, observation.records.first { it.nativeParameters?.saveBlock == 1 }.nativeParameters?.saveSequence)
        assertEquals(11L, observation.records.first { it.nativeParameters?.saveBlock == 2 }.nativeParameters?.saveSequence)
        assertEquals(mapOf(1 to true, 2 to true), observation.copyIntegrity)
        assertEquals(2, WorldDsCopySelection.latestCopy(observation))
    }

    @Test fun theEditResealsEveryPopulatedCopyHeaderToTheMd5OfItsOwnBytes() {
        val source = fixture().also { it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE) }
        val removal = WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:1:record:0")
        val digest = java.security.MessageDigest.getInstance("MD5")
        for (base in listOf(0, WorldDsSaveReader.COPY_STRIDE)) {
            digest.reset()
            digest.update(ByteArray(16))
            digest.update(removal.candidateBytes, base + 16, WorldDsSaveReader.SAVE_BYTES - 16)
            assertArrayEquals("copy header at $base", digest.digest(), removal.candidateBytes.copyOfRange(base, base + 16))
            assertFalse("the resealed header must not equal the source's", removal.candidateBytes.copyOfRange(base, base + 16).contentEquals(source.copyOfRange(base, base + 16)))
        }
        val restored = WorldDsPartyEdits.restoreResearchRemoval(removal.candidateBytes, removal)
        assertArrayEquals(source.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE), restored.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE))
        assertArrayEquals(source.copyOfRange(WorldDsSaveReader.COPY_STRIDE, WorldDsSaveReader.FILE_BYTES), restored.copyOfRange(WorldDsSaveReader.COPY_STRIDE, WorldDsSaveReader.FILE_BYTES))
    }

    @Test fun removalRetainsEveryNativeByteLeavesAPartyHoleAndChangesOnlyTheTwoWindows() {
        val source = fixture()
        val original = source.copyOf()
        val observation = WorldDsSaveReader().read(source)
        val target = observation.records.single { it.position == 2 }
        val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, target.recordKey)
        val candidate = removal.candidateBytes
        assertArrayEquals(original, source)
        assertArrayEquals(source.copyOfRange(0x3B4, 0x534), Base64.getDecoder().decode(removal.nativeRecordBase64))
        assertTrue(candidate.copyOfRange(0x3B4, 0x534).all { it == 0.toByte() })
        val after = WorldDsSaveReader().read(candidate)
        assertEquals(listOf("save-local:7", "save-local:20"), after.records.sortedBy { it.position }.map { it.nativeIdentity })
        assertEquals(listOf(1, 3), after.records.sortedBy { it.position }.map { it.position })
        assertEquals(observation.records.filter { it.position != 2 }.map { it.nativeRecordBase64 }, after.records.map { it.nativeRecordBase64 })
        source.indices.filter { it !in 0 until 16 && it !in 0x20C until 0x234 && it !in 0x3B4 until 0x534 }
            .forEach { assertEquals("Unrelated byte at $it", source[it], candidate[it]) }
        assertArrayEquals(original, WorldDsPartyEdits.restoreResearchRemoval(candidate, removal))
    }

    @Test fun firstAndLastPartyPositionsRestoreByteIdenticallyAndSupportTheSecondSector() {
        for (position in listOf(1, 3)) for (copy in listOf(1, 2)) {
            val source = fixture()
            if (copy == 2) {
                source.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(source, WorldDsSaveReader.COPY_STRIDE)
                source.fill(0xFF.toByte(), 0, WorldDsSaveReader.COPY_STRIDE)
            }
            val observation = WorldDsSaveReader().read(source)
            val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision,
                observation.records.single { it.position == position }.recordKey)
            assertArrayEquals(source, WorldDsPartyEdits.restoreResearchRemoval(removal.candidateBytes, removal))
        }
    }

    @Test fun rejectsStaleSourceAndChangedRemovalInsteadOfApplyingAnOldReturn() {
        val source = fixture()
        val observation = WorldDsSaveReader().read(source)
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.removeForResearch(source, "stale", observation.records.first().recordKey)
        }
        val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, observation.records.first().recordKey)
        val changed = removal.candidateBytes.also { it[0x1F0] = 10 }
        assertThrows(IllegalArgumentException::class.java) { WorldDsPartyEdits.restoreResearchRemoval(changed, removal) }
    }

    @Test fun refusesTheLastUsableMemberEvenWhenOtherPartyMembersAreGuestsOrEggs() {
        val source = fixture()
        put(source, 0x234 + 0x180 + 0x108, 2, 2)
        put(source, 0x234 + 2 * 0x180 + 0x108, 0x10, 2)
        val observation = WorldDsSaveReader().read(source)
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, observation.records.first().recordKey)
        }
    }

    @Test fun refusesFarmGuestsUnknownTargetsAndDuplicateReferences() {
        val cases = listOf(
            fixture().also { put(it, 0x20C, 0); put(it, 0x3E40, 25); put(it, 0x3E40 + 0x44, 7) },
            fixture().also { put(it, 0x234 + 0x108, 2, 2) },
            fixture().also { put(it, 0x20C + 12, 7) },
        )
        cases.forEach { source ->
            assertThrows(IllegalArgumentException::class.java) {
                WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:1:record:0")
            }
        }
        val source = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:1:record:39")
        }
    }

    @Test fun consistentSaveCopiesReceiveTheSameRemovalWrittenToBothAtOnce() {
        val source = fixture().also { it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE) }
        val original = source.copyOf()
        val observation = WorldDsSaveReader().read(source)
        val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, "copy:1:record:0")
        val candidate = removal.candidateBytes
        val after = WorldDsSaveReader().read(candidate)
        assertEquals(4, after.records.size)
        assertEquals(listOf("save-local:12", "save-local:20", "save-local:12", "save-local:20"),
            after.records.sortedWith(compareBy({ it.nativeParameters!!.saveBlock }, { it.position })).map { it.nativeIdentity })
        assertEquals(listOf(2, 3, 2, 3),
            after.records.sortedWith(compareBy({ it.nativeParameters!!.saveBlock }, { it.position })).map { it.position })
        for (base in listOf(0, WorldDsSaveReader.COPY_STRIDE)) {
            assertTrue(candidate.copyOfRange(base + 0x234, base + 0x3B4).all { it == 0.toByte() })
            assertEquals(listOf(0L, 12L, 20L), List(3) { u32(candidate, base + 0x20C + it * 4) })
            assertEquals(30L, u32(candidate, base + 0x208))
        }
        val windows = listOf(0, WorldDsSaveReader.COPY_STRIDE).flatMap { base ->
            listOf(base until base + 16, base + 0x20C until base + 0x234, base + 0x234 until base + 0x3B4)
        }
        original.indices.filter { index -> windows.none { index in it } }
            .forEach { assertEquals("Unrelated byte at $it", original[it], candidate[it]) }
        assertArrayEquals(original, WorldDsPartyEdits.restoreResearchRemoval(candidate, removal))
    }

    @Test fun divergingSaveCopiesRemoveOnlyTheIndividualsOwnSlotPerCopy() {
        val missingInSecondCopy = fixture().also { source ->
            source.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(source, WorldDsSaveReader.COPY_STRIDE)
            put(source, WorldDsSaveReader.COPY_STRIDE + 0x20C, 12)
            put(source, WorldDsSaveReader.COPY_STRIDE + 0x210, 20)
            put(source, WorldDsSaveReader.COPY_STRIDE + 0x214, 0)
            put(source, WorldDsSaveReader.COPY_STRIDE + 0x234, 0)
        }
        val removal = WorldDsPartyEdits.removeForResearch(missingInSecondCopy, revision(missingInSecondCopy), "copy:1:record:0")
        val after = WorldDsSaveReader().read(removal.candidateBytes)
        assertEquals(listOf("save-local:12", "save-local:20", "save-local:12", "save-local:20"),
            after.records.sortedWith(compareBy({ it.nativeParameters!!.saveBlock }, { it.position })).map { it.nativeIdentity })
        assertArrayEquals(missingInSecondCopy, WorldDsPartyEdits.restoreResearchRemoval(removal.candidateBytes, removal))
        val differingBytes = fixture().also { source ->
            source.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(source, WorldDsSaveReader.COPY_STRIDE)
            source[WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x30] = 0x77
        }
        val divergent = WorldDsPartyEdits.removeForResearch(differingBytes, revision(differingBytes), "copy:1:record:0")
        assertEquals(listOf("save-local:12", "save-local:20", "save-local:12", "save-local:20"),
            WorldDsSaveReader().read(divergent.candidateBytes).records
                .sortedWith(compareBy({ it.nativeParameters!!.saveBlock }, { it.position })).map { it.nativeIdentity })
        assertArrayEquals(differingBytes, WorldDsPartyEdits.restoreResearchRemoval(divergent.candidateBytes, divergent))
    }

    @Test fun removalLocatesTheIdentityAtDifferentSlotsInEachCopyWithoutDeletingANeighbour() {
        val source = fixture().also { bytes ->
            bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
            val second = WorldDsSaveReader.COPY_STRIDE
            // In the second copy id 12 lives in slot 5 and slot 1 holds an unrelated live record.
            put(bytes, second + 0x234 + 5 * 0x180, 12)
            put(bytes, second + 0x234 + 5 * 0x180 + 4, 20)
            put(bytes, second + 8, 0x63, 2); put(bytes, second + 10, 0xFFFF, 2)
            put(bytes, second + 0x234 + 1 * 0x180, 90)
            put(bytes, second + 0x234 + 1 * 0x180 + 8, 0x63, 2); put(bytes, second + 0x234 + 1 * 0x180 + 10, 0xFFFF, 2)
        }
        val removal = WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:1:record:1")
        assertEquals(listOf(1, 5), removal.slots)
        val after = WorldDsSaveReader().read(removal.candidateBytes)
        assertTrue(after.records.none { it.nativeIdentity == "save-local:12" })
        // The unrelated record planted at the second copy's slot 1 survives.
        assertTrue(after.records.any { it.nativeIdentity == "save-local:90" })
        assertTrue(after.records.any { it.nativeIdentity == "save-local:7" })
        assertTrue(after.records.any { it.nativeIdentity == "save-local:20" })
        assertTrue(removal.candidateBytes.copyOfRange(WorldDsSaveReader.COPY_STRIDE + 0x234 + 5 * 0x180,
            WorldDsSaveReader.COPY_STRIDE + 0x234 + 6 * 0x180).all { it == 0.toByte() })
        assertArrayEquals(source, WorldDsPartyEdits.restoreResearchRemoval(removal.candidateBytes, removal))
    }

    private fun bankFixture(): ByteArray = ByteArray(65536) { 0xFF.toByte() }.also { bytes ->
        bytes.fill(0, 0, 0x7DC0)
        repeat(16) { bytes[it] = (it + 1).toByte() }
        put(bytes, 0x208, 30)
        put(bytes, 0x20C, 7)
        recordInto(bytes, 0x234, 7)
        // A live record nothing references: the game's storage side, not the party.
        recordInto(bytes, 0x234 + 0x180, 25)
    }

    @Test fun storageRemovalClearsOnlyTheSlotAndLeavesThePartyUntouched() {
        val source = bankFixture()
        val original = source.copyOf()
        val observation = WorldDsSaveReader().read(source)
        val target = observation.records.single { it.recordKey == "copy:1:record:1" }
        assertEquals(SaveContainer.UNASSIGNED, target.container)
        val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, target.recordKey)
        val candidate = removal.candidateBytes
        assertTrue(candidate.copyOfRange(0x234 + 0x180, 0x234 + 0x300).all { it == 0.toByte() })
        assertEquals(7L, u32(candidate, 0x20C))
        assertEquals(0L, u32(candidate, 0x210))
        assertEquals(30L, u32(candidate, 0x208))
        val after = WorldDsSaveReader().read(candidate)
        assertEquals(listOf("save-local:7"), after.records.map { it.nativeIdentity })
        original.indices.filter { it !in 0 until 16 && it !in 0x234 + 0x180 until 0x234 + 0x300 }
            .forEach { assertEquals("Unrelated byte at $it", original[it], candidate[it]) }
        assertArrayEquals(original, WorldDsPartyEdits.restoreResearchRemoval(candidate, removal))
    }

    @Test fun storageRemovalReachesEveryPopulatedCopyWithoutTouchingTheirParties() {
        val source = bankFixture().also { it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE) }
        val original = source.copyOf()
        val observation = WorldDsSaveReader().read(source)
        val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, "copy:1:record:1")
        val candidate = removal.candidateBytes
        for (base in listOf(0, WorldDsSaveReader.COPY_STRIDE)) {
            assertTrue(candidate.copyOfRange(base + 0x234 + 0x180, base + 0x234 + 0x300).all { it == 0.toByte() })
            assertEquals(7L, u32(candidate, base + 0x20C))
            assertEquals(30L, u32(candidate, base + 0x208))
        }
        val after = WorldDsSaveReader().read(candidate)
        assertEquals(listOf("save-local:7", "save-local:7"), after.records.map { it.nativeIdentity })
        val windows = listOf(0, WorldDsSaveReader.COPY_STRIDE).flatMap { base ->
            listOf(base until base + 16, base + 0x234 + 0x180 until base + 0x234 + 0x300)
        }
        original.indices.filter { index -> windows.none { index in it } }
            .forEach { assertEquals("Unrelated byte at $it", original[it], candidate[it]) }
        assertArrayEquals(original, WorldDsPartyEdits.restoreResearchRemoval(candidate, removal))
    }

    @Test fun admissionAppendsTheStorageIdentityToEveryPopulatedCopyPartyAndUndoesByteIdentically() {
        val source = bankFixture().also { it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE) }
        val original = source.copyOf()
        val observation = WorldDsSaveReader().read(source)
        val admission = WorldDsPartyEdits.admitStorageRecordToParty(source, observation.contentRevision, "copy:1:record:1")
        val candidate = admission.candidateBytes
        for (base in listOf(0, WorldDsSaveReader.COPY_STRIDE)) {
            assertEquals(listOf(7L, 25L), List(2) { u32(candidate, base + 0x20C + it * 4) })
        }
        val after = WorldDsSaveReader().read(candidate)
        assertEquals(4, after.records.size)
        assertEquals(listOf("save-local:7", "save-local:25", "save-local:7", "save-local:25"),
            after.records.sortedWith(compareBy({ it.nativeParameters!!.saveBlock }, { it.position })).map { it.nativeIdentity })
        assertEquals(listOf(1, 2, 1, 2),
            after.records.sortedWith(compareBy({ it.nativeParameters!!.saveBlock }, { it.position })).map { it.position })
        val windows = listOf(0, WorldDsSaveReader.COPY_STRIDE).flatMap { base ->
            listOf(base until base + 16, base + 0x20C until base + 0x234)
        }
        original.indices.filter { index -> windows.none { index in it } }
            .forEach { assertEquals("Unrelated byte at $it", original[it], candidate[it]) }
        assertArrayEquals(original, WorldDsPartyEdits.restoreResearchAdmission(candidate, admission))
    }

    @Test fun admissionFillsTheFirstFreePartySlotWithoutCompactingExistingHoles() {
        val source = fixture().also { bytes ->
            put(bytes, 0x210, 0)
            recordInto(bytes, 0x234 + 3 * 0x180, 25)
        }
        val observation = WorldDsSaveReader().read(source)
        val admission = WorldDsPartyEdits.admitStorageRecordToParty(source, observation.contentRevision, "copy:1:record:3")
        assertEquals(listOf(7L, 25L, 20L), List(3) { u32(admission.candidateBytes, 0x20C + it * 4) })
        val admitted = WorldDsSaveReader().read(admission.candidateBytes).records
            .single { it.nativeIdentity == "save-local:25" }
        assertEquals(SaveContainer.PARTY, admitted.container)
        assertEquals(2, admitted.position)
    }

    @Test fun admissionUsesTheLastVisiblePartySlotAndNeverTheTwoReservedSlots() {
        val source = minimalDestination(9, (1L..7L).toList()).also { bytes ->
            recordInto(bytes, 0x234 + 7 * 0x180, 8)
        }
        val admission = WorldDsPartyEdits.admitStorageRecordToParty(source, revision(source), "copy:1:record:7")
        assertEquals(8L, u32(admission.candidateBytes, 0x20C + 7 * 4))
        assertEquals(0L, u32(admission.candidateBytes, 0x20C + 8 * 4))
        assertEquals(0L, u32(admission.candidateBytes, 0x20C + 9 * 4))
        val admitted = WorldDsSaveReader().read(admission.candidateBytes).records
            .single { it.nativeIdentity == "save-local:8" }
        assertEquals(SaveContainer.PARTY, admitted.container)
        assertEquals(8, admitted.position)
    }

    @Test fun admissionRefusesReferencedRecordsFullPartiesFarmResidentsAndLiveFlags() {
        val source = bankFixture()
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.admitStorageRecordToParty(source, revision(source), "copy:1:record:0")
        }
        val fullParty = ByteArray(65536).also { bytes ->
            bytes.fill(0, 0, 0x7DC0)
            repeat(16) { bytes[it] = (it + 1).toByte() }
            put(bytes, 0x208, 9)
            (1L..8L).forEachIndexed { index, id ->
                put(bytes, 0x20C + index * 4, id)
                recordInto(bytes, 0x234 + index * 0x180, id)
            }
            // Slots 8 and 9 are reserved; the ninth record must stay in storage even though they are empty.
            recordInto(bytes, 0x234 + 8 * 0x180, 9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.admitStorageRecordToParty(fullParty, revision(fullParty), "copy:1:record:8")
        }
        val farmResident = bankFixture().also { put(it, 0x3E40 + 0x44, 25) }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.admitStorageRecordToParty(farmResident, revision(farmResident), "copy:1:record:1")
        }
        val flagged = bankFixture().also { put(it, 0x234 + 0x180 + 0x108, 2, 2) }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.admitStorageRecordToParty(flagged, revision(flagged), "copy:1:record:1")
        }
        val admission = WorldDsPartyEdits.admitStorageRecordToParty(source, revision(source), "copy:1:record:1")
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.restoreResearchAdmission(admission.candidateBytes.also { it[0x1F0] = 10 }, admission)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.admitStorageRecordToParty(source, "stale", "copy:1:record:1")
        }
    }

    @Test fun storageRemovalRefusesTheLastUsableDigimonAndReferencedStorageRecords() {
        val onlyRecord = bankFixture().also { put(it, 0x20C, 0); put(it, 0x234, 0) }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.removeForResearch(onlyRecord, revision(onlyRecord), "copy:1:record:1")
        }
        val itemReferenced = bankFixture().also { put(it, 0x43D8 + 4, 25) }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.removeForResearch(itemReferenced, revision(itemReferenced), "copy:1:record:1")
        }
        val flagged = bankFixture().also { put(it, 0x234 + 0x180 + 0x108, 2, 2) }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.removeForResearch(flagged, revision(flagged), "copy:1:record:1")
        }
    }

    @Test fun unmodeledStatusBitsSurviveRemovalAndExactReturnVerbatim() {
        val source = bankFixture().also { put(it, 0x234 + 0x180 + 0x108, 0x40, 2) }
        val removal = WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:1:record:1")
        assertTrue(removal.candidateBytes.copyOfRange(0x234 + 0x180, 0x234 + 0x300).all { it == 0.toByte() })
        val insertion = WorldDsPartyEdits.insertRetainedRecord(removal.candidateBytes, removal.candidateRevision,
            removal.nativeRecordBase64)
        assertArrayEquals(source.copyOfRange(0x234 + 0x180, 0x234 + 0x300),
            insertion.candidateBytes.copyOfRange(0x3B4, 0x534))
        assertEquals(0x40, u16(insertion.candidateBytes, 0x3B4 + 0x108))
    }

    @Test fun liveOwnershipFlagsAreStillRefusedForRemovalAndReturn() {
        listOf(2, 4, 8, 0x10, 0x20).forEach { flags ->
            val flagged = bankFixture().also { put(it, 0x234 + 0x180 + 0x108, flags.toLong(), 2) }
            assertThrows(IllegalArgumentException::class.java) {
                WorldDsPartyEdits.removeForResearch(flagged, revision(flagged), "copy:1:record:1")
            }
        }
        val destination = minimalDestination(10, listOf(7L))
        listOf(2, 4, 8, 0x10, 0x20).forEach { flags ->
            val flagged = expectation(12).also { put(it, 0x108, flags.toLong(), 2) }
            assertThrows(IllegalArgumentException::class.java) {
                WorldDsPartyEdits.insertRetainedRecord(destination, revision(destination),
                    Base64.getEncoder().encodeToString(flagged))
            }
        }
        val unmodeled = expectation(12).also { put(it, 0x108, 0x40, 2) }
        val insertion = WorldDsPartyEdits.insertRetainedRecord(destination, revision(destination),
            Base64.getEncoder().encodeToString(unmodeled))
        assertEquals(2, insertion.position)
        assertArrayEquals(unmodeled, insertion.candidateBytes.copyOfRange(0x3B4, 0x534))
    }

    @Test fun repairReplacesAnExistingRecordInPlaceAndUndoesByteIdentically() {
        val source = fixture()
        val original = source.copyOf()
        val replacement = expectation(7).also { record ->
            put(record, 0x1A, 7)
            put(record, 0x50, 2, 2)
            put(record, 0x22, 16, 2)
            put(record, 0x2C, 20)
            put(record, 0x54, 3, 2); put(record, 0x56, 145, 2)
            put(record, 0x70, 33, 2)
            put(record, 0x108, 0x40, 2)
        }
        val repair = WorldDsPartyEdits.replaceRetainedRecord(source, revision(source),
            Base64.getEncoder().encodeToString(replacement))
        assertEquals("save-local:7", repair.nativeIdentity)
        assertEquals("copy:1:record:0", repair.recordKey)
        assertArrayEquals(replacement, repair.candidateBytes.copyOfRange(0x234, 0x3B4))
        assertEquals(30L, u32(repair.candidateBytes, 0x208))
        assertEquals(listOf(7L, 12L, 20L), List(3) { u32(repair.candidateBytes, 0x20C + it * 4) })
        val after = WorldDsSaveReader().read(repair.candidateBytes)
        assertEquals(3, after.records.size)
        assertEquals("save-local:7", after.records.single { it.position == 1 }.nativeIdentity)
        assertArrayEquals(original, WorldDsPartyEdits.restoreResearchReplacement(repair.candidateBytes, repair))
    }

    @Test fun repairWritesEveryPopulatedCopyAndRefusesACopyMissingTheIdentity() {
        val source = fixture().also {
            it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE)
        }
        val replacement = expectation(7).also { put(it, 0x1A, 7); put(it, 0x108, 0x40, 2) }
        val repair = WorldDsPartyEdits.replaceRetainedRecord(source, revision(source),
            Base64.getEncoder().encodeToString(replacement))
        for (base in listOf(0, WorldDsSaveReader.COPY_STRIDE)) {
            assertArrayEquals(replacement, repair.candidateBytes.copyOfRange(base + 0x234, base + 0x3B4))
            assertEquals(7L, u32(repair.candidateBytes, base + 0x20C))
            assertEquals(30L, u32(repair.candidateBytes, base + 0x208))
        }
        assertArrayEquals(source, WorldDsPartyEdits.restoreResearchReplacement(repair.candidateBytes, repair))

        val missing = fixture().also {
            it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE)
            put(it, WorldDsSaveReader.COPY_STRIDE + 0x234, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.replaceRetainedRecord(missing, revision(missing),
                Base64.getEncoder().encodeToString(replacement))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.replaceRetainedRecord(source, "stale", Base64.getEncoder().encodeToString(replacement))
        }
        val flagged = fixture().also { put(it, 0x234 + 0x108, 2, 2) }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.replaceRetainedRecord(flagged, revision(flagged),
                Base64.getEncoder().encodeToString(replacement))
        }
    }

    @Test fun insertionWritesEveryPopulatedCopyAndOnlyRaisesTheirOwnHighWaterMarks() {
        val destination = fixture().also { it.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(it, WorldDsSaveReader.COPY_STRIDE) }
        val insertion = WorldDsPartyEdits.insertRetainedRecord(destination, revision(destination), retained(40))
        assertEquals(3, insertion.slot)
        assertEquals(4, insertion.position)
        val returned = WorldDsSaveReader().read(insertion.candidateBytes)
        assertEquals(8, returned.records.size)
        for (base in listOf(0, WorldDsSaveReader.COPY_STRIDE)) {
            assertEquals(40L, u32(insertion.candidateBytes, base + 0x208))
            assertEquals(listOf(7L, 12L, 20L, 40L), List(4) { u32(insertion.candidateBytes, base + 0x20C + it * 4) })
        }
        listOf("copy:1:record:3", "copy:2:record:3").forEach { key ->
            val inserted = returned.records.single { it.recordKey == key }
            assertEquals("save-local:40", inserted.nativeIdentity)
            assertEquals(4, inserted.position)
            assertTrue(inserted.issues.all { it == RecordIssue.AMBIGUOUS_SAVE_BLOCK })
        }
        assertArrayEquals(destination, WorldDsPartyEdits.restoreResearchInsertion(insertion.candidateBytes, insertion))
    }

    @Test fun insertionUsesEachCopysOwnFreeSlot() {
        val destination = fixture().also { source ->
            source.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(source, WorldDsSaveReader.COPY_STRIDE)
            put(source, WorldDsSaveReader.COPY_STRIDE + 0x234 + 3 * 0x180, 30)
        }
        val insertion = WorldDsPartyEdits.insertRetainedRecord(destination, revision(destination), retained(40))
        assertEquals(listOf(3, 4), insertion.slots)
    }

    @Test fun refusesExternalItemOrSpecialSelectionReferencesToTheRemovedMember() {
        listOf(0x43D8 + 4, 0x443C).forEach { reference ->
            val source = fixture().also { put(it, reference, 7) }
            assertThrows(IllegalArgumentException::class.java) {
                WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:1:record:0")
            }
        }
    }

    @Test fun returnIntoItsOriginalSaveReusesTheZeroedSlotAndAppendsTheIdentityToTheParty() {
        val source = fixture()
        val removal = WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:1:record:1")
        val destination = removal.candidateBytes
        val insertion = WorldDsPartyEdits.insertRetainedRecord(destination, removal.candidateRevision, removal.nativeRecordBase64)
        assertEquals(1, insertion.slot)
        assertEquals(2, insertion.position)
        assertEquals("save-local:12", insertion.nativeIdentity)
        val returned = WorldDsSaveReader().read(insertion.candidateBytes)
        assertEquals(listOf("save-local:7", "save-local:12", "save-local:20"),
            returned.records.sortedBy { it.position }.map { it.nativeIdentity })
        assertEquals(listOf(1, 2, 3), returned.records.sortedBy { it.position }.map { it.position })
        assertArrayEquals(Base64.getDecoder().decode(removal.nativeRecordBase64),
            insertion.candidateBytes.copyOfRange(0x3B4, 0x534))
        destination.indices.filter { it !in 0 until 16 && it !in 0x20C until 0x234 && it !in 0x3B4 until 0x534 }
            .forEach { assertEquals("Unrelated byte at $it", destination[it], insertion.candidateBytes[it]) }
        assertArrayEquals(destination, WorldDsPartyEdits.restoreResearchInsertion(insertion.candidateBytes, insertion))
        val changed = insertion.candidateBytes.also { it[0x1F0] = 10 }
        assertThrows(IllegalArgumentException::class.java) { WorldDsPartyEdits.restoreResearchInsertion(changed, insertion) }
    }

    @Test fun insertionUsesAnErasedSlotAndUndoRestoresItsOriginalBytes() {
        val source = fixture().also { bytes ->
            bytes.fill(0xFF.toByte(), 0x234 + 3 * 0x180 + 4, 0x234 + 4 * 0x180)
            put(bytes, 0x234 + 3 * 0x180, 0)
        }
        val insertion = WorldDsPartyEdits.insertRetainedRecord(source, revision(source), retained(25))
        assertEquals(3, insertion.slot)
        assertEquals(4, insertion.position)
        assertArrayEquals(source, WorldDsPartyEdits.restoreResearchInsertion(insertion.candidateBytes, insertion))
    }

    @Test fun insertionWorksOnTheSecondSaveCopyAndOnlyRaisesItsOwnHighWaterMark() {
        val source = fixture()
        val retained = requireNotNull(WorldDsSaveReader().read(source).records.single { it.nativeIdentity == "save-local:12" }.nativeRecordBase64)
        val destination = minimalDestination(10, listOf(7L)).also { bytes ->
            bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
            bytes.fill(0, 0, WorldDsSaveReader.COPY_STRIDE)
        }
        val insertion = WorldDsPartyEdits.insertRetainedRecord(destination, revision(destination), retained)
        assertEquals(1, insertion.slot)
        assertEquals(2, insertion.position)
        assertEquals(12L, u32(insertion.candidateBytes, WorldDsSaveReader.COPY_STRIDE + 0x208))
        assertTrue(insertion.candidateBytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).all { it == 0.toByte() })
        val returned = WorldDsSaveReader().read(insertion.candidateBytes)
        assertEquals(listOf("save-local:7", "save-local:12"), returned.records.sortedBy { it.position }.map { it.nativeIdentity })
        assertEquals(2, returned.records.single { it.nativeIdentity == "save-local:12" }.nativeParameters!!.saveBlock)
        assertArrayEquals(destination, WorldDsPartyEdits.restoreResearchInsertion(insertion.candidateBytes, insertion))
    }

    @Test fun insertionSkipsSlotsWithUnknownStateAndRefusesATakenIdentity() {
        val destination = minimalDestination(10, listOf(7L)).also { bytes ->
            put(bytes, 0x234 + 0x180, 9)
        }
        val insertion = WorldDsPartyEdits.insertRetainedRecord(destination, revision(destination), retained(12))
        assertEquals(2, insertion.slot)
        val collision = minimalDestination(30, listOf(7L, 12L))
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.insertRetainedRecord(collision, revision(collision), retained(12))
        }
    }

    @Test fun insertionRefusesFullPartiesAndFullRecordTables() {
        val fullParty = ByteArray(65536).also { bytes ->
            repeat(16) { bytes[it] = (it + 1).toByte() }
            put(bytes, 0x208, 8)
            (1L..8L).forEachIndexed { index, id ->
                put(bytes, 0x20C + index * 4, id)
                recordInto(bytes, 0x234 + index * 0x180, id)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.insertRetainedRecord(fullParty, revision(fullParty), retained(41))
        }
        val fullTable = ByteArray(65536).also { bytes ->
            repeat(16) { bytes[it] = (it + 1).toByte() }
            put(bytes, 0x208, 40)
            (1L..5L).forEach { id -> put(bytes, 0x20C + (id - 1).toInt() * 4, id) }
            (1L..40L).forEach { id -> recordInto(bytes, 0x234 + (id - 1).toInt() * 0x180, id) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.insertRetainedRecord(fullTable, revision(fullTable), retained(41))
        }
    }

    @Test fun generatedInsertionAssignsAFreshIdentityAfterTheHighWaterMark() {
        val destination = fixture()
        val stats = WorldDsStatConversion.toNative(WorldDsStatConversion.AppStats(stage = 2,
            baseHp = 100, baseBp = 100, baseAp = 100, trainingHp = 40, trainingBp = 50, trainingAp = 60))
        val seed = ByteArray(0x180)
        seed[0x100] = 0x7F
        val generated = WorldDsRecordCodec.generate(7, 20, "AGUMON", "AGUMON", stats, seed)
        val insertion = WorldDsPartyEdits.insertGeneratedRecord(destination, revision(destination),
            WorldDsRecordCodec.base64(generated.bytes))
        val after = WorldDsSaveReader().read(insertion.candidateBytes)
        val inserted = after.records.single { it.nativeIdentity == "save-local:31" }
        assertEquals(4, after.records.size)
        assertEquals(4, inserted.position)
        assertEquals(SaveContainer.PARTY, inserted.container)
        assertEquals(31L, u32(Base64.getDecoder().decode(requireNotNull(inserted.nativeRecordBase64)), 0))
        assertEquals(0x7F, Base64.getDecoder().decode(requireNotNull(inserted.nativeRecordBase64))[0x100].toInt() and 255)
        assertEquals(31L, u32(insertion.candidateBytes, 0x208))
    }

    @Test fun generatedInsertionUsesTheHighestExistingRecordIdentityEvenAboveTheHighWaterMark() {
        val destination = minimalDestination(40, listOf(7L, 40L))
        val stats = WorldDsStatConversion.toNative(WorldDsStatConversion.AppStats(stage = 1,
            baseHp = 50, baseBp = 50, baseAp = 50))
        val generated = WorldDsRecordCodec.generate(12, 20, "AGUMON", "AGUMON", stats)
        val insertion = WorldDsPartyEdits.insertGeneratedRecord(destination, revision(destination),
            WorldDsRecordCodec.base64(generated.bytes))
        assertEquals("save-local:41", insertion.nativeIdentity)
        assertEquals(41L, u32(insertion.candidateBytes, 0x208))
    }

    @Test fun generatedInsertionRejectsMalformedRecords() {
        val destination = minimalDestination(10, listOf(7L))
        val stats = WorldDsStatConversion.toNative(WorldDsStatConversion.AppStats(stage = 1,
            baseHp = 50, baseBp = 50, baseAp = 50))
        val generated = WorldDsRecordCodec.generate(8, 20, "AGUMON", "AGUMON", stats)
        val malformed = listOf(
            generated.bytes.copyOf(383),
            generated.bytes.copyOf().also { put(it, 4, 426) },
            generated.bytes.copyOf().also { put(it, 0x74, 0, 2) },
            generated.bytes.copyOf().also { put(it, 0x108, 2, 2) },
            generated.bytes.copyOf().also { put(it, 0x78, 100000) },
        )
        malformed.forEach { record ->
            assertThrows(IllegalArgumentException::class.java) {
                WorldDsPartyEdits.insertGeneratedRecord(destination, revision(destination),
                    Base64.getEncoder().encodeToString(record))
            }
        }
    }

    @Test fun removalUsesPerCopyContainersWhenTheOlderCopyDisagrees() {
        val source = bankFixture().also { bytes ->
            bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
            // Authority says storage; the older copy references the same identity from its party.
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x210, 25)
        }
        val observation = WorldDsSaveReader().read(source)
        val target = observation.records.single { it.recordKey == "copy:1:record:1" }
        assertEquals(SaveContainer.UNASSIGNED, target.container)
        val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, target.recordKey)
        val after = WorldDsSaveReader().read(removal.candidateBytes)
        assertTrue(after.records.none { it.nativeIdentity == "save-local:25" })
        assertEquals(listOf(7L, 0L), List(2) { u32(removal.candidateBytes, 0x20C + it * 4) })
        assertEquals(7L, u32(removal.candidateBytes, WorldDsSaveReader.COPY_STRIDE + 0x20C))
        assertArrayEquals(source, WorldDsPartyEdits.restoreResearchRemoval(removal.candidateBytes, removal))
    }

    @Test fun removalClearsAnOlderCopysDanglingPartyReference() {
        val source = bankFixture().also { bytes ->
            bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x210, 25)
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x180, 0)
        }
        val observation = WorldDsSaveReader().read(source)
        val removal = WorldDsPartyEdits.removeForResearch(source, observation.contentRevision, "copy:1:record:1")
        assertEquals(listOf(7L, 0L), List(2) { u32(removal.candidateBytes, WorldDsSaveReader.COPY_STRIDE + 0x20C + it * 4) })
        assertArrayEquals(source, WorldDsPartyEdits.restoreResearchRemoval(removal.candidateBytes, removal))
    }

    @Test fun admissionClearsAnOlderCopysDanglingPartyReference() {
        val source = bankFixture().also { bytes ->
            bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x210, 25)
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x180, 0)
        }
        val observation = WorldDsSaveReader().read(source)
        val admission = WorldDsPartyEdits.admitStorageRecordToParty(source, observation.contentRevision, "copy:1:record:1")
        assertEquals(listOf(7L, 25L), List(2) { u32(admission.candidateBytes, 0x20C + it * 4) })
        assertEquals(listOf(7L, 0L), List(2) { u32(admission.candidateBytes, WorldDsSaveReader.COPY_STRIDE + 0x20C + it * 4) })
        assertArrayEquals(source, WorldDsPartyEdits.restoreResearchAdmission(admission.candidateBytes, admission))
    }

    @Test fun removalProtectsTheAuthoritysLastPartyMemberEvenWhenTheOlderCopyHasOthers() {
        val source = fixture().also { bytes ->
            bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x210, 0)
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x214, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.removeForResearch(source, revision(source), "copy:2:record:0")
        }
    }

    @Test fun admissionSucceedsWhenAnOlderCopyDoesNotContainTheRecord() {
        val source = bankFixture().also { bytes ->
            bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
            put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x180, 0)
        }
        val observation = WorldDsSaveReader().read(source)
        val target = observation.records.single { it.recordKey == "copy:1:record:1" }
        val admission = WorldDsPartyEdits.admitStorageRecordToParty(source, observation.contentRevision, target.recordKey)
        val after = WorldDsSaveReader().read(admission.candidateBytes)
        assertEquals(listOf(7L, 25L), List(2) { u32(admission.candidateBytes, 0x20C + it * 4) })
        assertEquals(listOf(7L, 0L), List(2) { u32(admission.candidateBytes, WorldDsSaveReader.COPY_STRIDE + 0x20C + it * 4) })
        assertTrue(after.records.none { it.nativeIdentity == "save-local:25" && it.nativeParameters?.saveBlock == 2 })
        assertArrayEquals(source, WorldDsPartyEdits.restoreResearchAdmission(admission.candidateBytes, admission))
    }

    @Test fun insertionRejectsMalformedRetainedRecordsAndStaleDestinations() {
        val destination = minimalDestination(10, listOf(7L))
        val cases = listOf(
            ByteArray(383),
            expectation(0),
            expectation(12).also { put(it, 4, 426) },
            expectation(12).also { put(it, 0x108, 2, 2) },
            expectation(12).also { put(it, 0x74, 0, 2) },
            expectation(12).also { put(it, 10, 0x63, 2) },
            expectation(12).also { put(it, 0x40, 50) },
            expectation(12).also { put(it, 0x78, 100000) },
        )
        cases.forEach { record ->
            assertThrows(IllegalArgumentException::class.java) {
                WorldDsPartyEdits.insertRetainedRecord(destination, revision(destination),
                    Base64.getEncoder().encodeToString(record))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsPartyEdits.insertRetainedRecord(destination, "stale", retained(41))
        }
    }
}
