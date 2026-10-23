package com.github.nacabaro.vbhelper.gamesave

import java.io.ByteArrayInputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Independent synthetic fixture shaped by the supplied DraStic observation, not private save bytes. */
class WorldDsSaveReaderTest {
    private fun put(bytes: ByteArray, at: Int, value: Long, width: Int = 4) {
        repeat(width) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }
    private fun fixture(): ByteArray = ByteArray(0x10000) { 0xFF.toByte() }.also { bytes ->
        bytes.fill(0, 0, 0x7DC0)
        repeat(16) { bytes[it] = (it + 1).toByte() } // Opaque header, deliberately no DSDIGI signature.
        put(bytes, 0x208, 10); put(bytes, 0x20C, 7)
        val at = 0x234
        put(bytes, at, 7); put(bytes, at + 4, 20)
        listOf(0x63, 0x6E, 0x7C, 0x7D, 0xFFFF).forEachIndexed { i, code -> put(bytes, at + 8 + i * 2, code.toLong(), 2) }
        put(bytes, at + 0x3C, 100); put(bytes, at + 0x40, 110)
        put(bytes, at + 0x44, 20, 2); put(bytes, at + 0x46, 25, 2)
        for (offset in listOf(0x48, 0x4A, 0x4C, 0x4E)) put(bytes, at + offset, 30, 2)
        put(bytes, at + 0x52, 25, 2); put(bytes, at + 0x74, 3, 2)
        repeat(8) { put(bytes, at + 0x78 + it * 4, (it * 11).toLong()) }
    }

    @Test fun registryReadsObservedDrasticShapeWithoutTheResearchSignature() {
        val bytes = fixture()
        val original = bytes.copyOf()
        val result = SaveReaderRegistry().read(ByteArrayInputStream(bytes))
        assertEquals(WorldDsSaveReader.GAME_ID, result.gameId)
        assertEquals(1, result.activeCount)
        assertEquals(0, result.reviewCount)
        val record = result.records.single()
        assertEquals("Test", record.storedName)
        assertEquals(SaveContainer.PARTY, record.container)
        assertNull(record.personalityId)
        assertNull(record.totalExp)
        assertNull(record.talentRaw)
        assertNull(record.bondRaw)
        val parameters = requireNotNull(record.nativeParameters)
        assertEquals(listOf(0L, 11L, 22L, 33L, 44L, 55L, 66L, 77L), parameters.expCounters)
        assertEquals(25, parameters.aptitude)
        assertEquals(110, parameters.maxHp)
        assertEquals(false, result.copyIntegrity[1])
        assertArrayEquals(bytes.copyOfRange(0x234, 0x3B4), Base64.getDecoder().decode(record.nativeRecordBase64))
        assertTrue(result.capabilities.canImportSnapshot)
        assertTrue(result.capabilities.provisionalSnapshotCopy)
        assertFalse(result.capabilities.canInsert)
        assertFalse(result.capabilities.canRemove)
        assertTrue(ObservationNotice.SAVE_INTEGRITY_UNVERIFIED in result.notices)
        assertArrayEquals(original, bytes)
    }

    @Test fun populatedSecondBlockWorksWithoutPromotingTheFirstErasedBlock() {
        val bytes = fixture()
        bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
        bytes.fill(0xFF.toByte(), 0, WorldDsSaveReader.COPY_STRIDE)
        val observation = WorldDsSaveReader().read(bytes)
        assertEquals(1, observation.records.size)
        assertEquals(2, observation.records.single().nativeParameters!!.saveBlock)
    }

    @Test fun twoPopulatedBlocksRemainVisibleButNoCurrentRosterIsInvented() {
        val bytes = fixture()
        bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
        put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x74, 4, 2)
        val observation = WorldDsSaveReader().read(bytes)
        assertEquals(2, observation.reviewCount)
        assertEquals(0, observation.activeCount)
        assertTrue(ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED in observation.notices)
        assertEquals(2, observation.records.map { it.recordKey }.distinct().size)
        assertTrue(observation.records.all { RecordIssue.AMBIGUOUS_SAVE_BLOCK in it.issues })
    }

    @Test fun farmAndUnassignedResidentsAreNotMislabelledAsBank() {
        val bytes = fixture()
        put(bytes, 0x20C, 0)
        put(bytes, 0x3E40, 8); put(bytes, 0x3E40 + 0x44, 7)
        assertEquals(SaveContainer.FARM, WorldDsSaveReader().read(bytes).records.single().container)
        put(bytes, 0x3E40, 0)
        assertEquals(SaveContainer.UNASSIGNED, WorldDsSaveReader().read(bytes).records.single().container)
    }

    @Test fun shapelessFilesAreStillRejectedButGameplayValueAnomaliesOnlyFlagRecords() {
        val shapeless = listOf(fixture().copyOf(65535), ByteArray(65536), ByteArray(65536) { 0xFF.toByte() },
            fixture().also { put(it, 0x208, 0) }, fixture().also { put(it, 0x208, 0xFFFFFF00L) },
            fixture().also { it.fill(0, 0x234, 0x3E34) })
        shapeless.forEach { bytes -> assertThrows(SaveReadException::class.java) { WorldDsSaveReader().read(bytes) } }
        val anomalies = listOf(fixture().also { put(it, 0x234 + 4, 900) }, fixture().also { put(it, 0x234 + 0x74, 500, 2) },
            fixture().also { put(it, 0x234 + 0x40, 0) }, fixture().also { put(it, 0x234 + 0x78, 100000) })
        anomalies.forEach { bytes ->
            val record = WorldDsSaveReader().read(bytes).records.single()
            assertTrue(RecordIssue.IMPLAUSIBLE_VALUES in record.issues)
            assertEquals(RecordMembership.NEEDS_REVIEW, record.membership)
        }
    }

    @Test fun gameWrittenSignatureHeaderAndZeroedHeaderAreBothRecognized() {
        val signed = fixture().also { "DSDIGI\u0000\u0000".toByteArray(Charsets.US_ASCII).copyInto(it, 0x10) }
        val zeroed = fixture().also { it.fill(0, 0, 16) }
        for (bytes in listOf(signed, zeroed)) {
            val observation = WorldDsSaveReader().read(bytes)
            assertEquals(1, observation.activeCount)
            assertEquals(0, observation.reviewCount)
            assertEquals("Test", observation.records.single().storedName)
        }
    }

    @Test fun danglingPartyReferenceAndMissingNameTerminatorStayReadable() {
        // 8 is below lastId but has no record: a reference the game handed out and later released.
        val dangling = fixture().also { put(it, 0x20C + 3 * 4, 8) }
        val danglingRecord = WorldDsSaveReader().read(dangling).records.single()
        assertEquals(SaveContainer.PARTY, danglingRecord.container)
        assertEquals("save-local:7", danglingRecord.nativeIdentity)
        val unterminated = fixture().also { repeat(9) { index -> put(it, 0x234 + 8 + index * 2, (0x50 + index).toLong(), 2) } }
        assertEquals("ABCDEFGHI", WorldDsSaveReader().read(unterminated).records.single().storedName)
    }

    @Test fun randomPrefixIdSequencesWithBookkeepingReferencesReadLikeRealSaves() {
        data class Shape(val start: Long, val lastId: Long, val recordId: Long)
        val shapes = listOf(
            // A played phone save: random 256-aligned start plus the handout count in the low bytes.
            Shape(0xD3A5F100L, 0xD3A5F100L + 0x4F, 0xD3A5F100L + 0x49),
            // A save whose handout counter crossed a byte boundary (0x...FF -> 0x...100).
            Shape(0x0321A900L, 0x0321A900L + 0x201, 0x0321A900L + 0x1FF),
        )
        for (shape in shapes) {
            val bytes = fixture()
            put(bytes, 0x208, shape.lastId)
            put(bytes, 0x20C, shape.recordId)
            put(bytes, 0x234, shape.recordId)
            val record = WorldDsSaveReader().read(bytes).records.single()
            assertEquals(SaveContainer.PARTY, record.container)
            assertEquals("save-local:${shape.recordId}", record.nativeIdentity)
            assertEquals(RecordMembership.ACTIVE_RECORD, record.membership)
        }
    }

    @Test fun plausibleAllocatorWithoutMatchingGameBookkeepingIsNotIdentified() {
        val withoutReferences = fixture().also { put(it, 0x20C, 0) }
        val onlyDanglingReferences = fixture().also { put(it, 0x20C, 0); put(it, 0x20C + 4, 8) }
        listOf(withoutReferences, onlyDanglingReferences).forEach { bytes ->
            val error = assertThrows(SaveReadException::class.java) { WorldDsSaveReader().read(bytes) }
            assertEquals(SaveReadFailure.UNRECOGNIZED_LAYOUT, error.reason)
        }
    }

    @Test fun unstructuredNoiseAtStructuredSizesIsRejected() {
        val noise = ByteArray(0x10000).also { bytes ->
            var state = 0x2545F4914F6CDD1DL
            for (index in bytes.indices) {
                state = state * 6364136223846793005L + 1442695040888963407L
                bytes[index] = (state ushr 33).toByte()
            }
        }
        val error = assertThrows(SaveReadException::class.java) { WorldDsSaveReader().read(noise) }
        assertEquals(SaveReadFailure.UNRECOGNIZED_LAYOUT, error.reason)
    }

    @Test fun headerPrefixedAndFooterAnnotatedContainersAreUnwrappedBeforeReading() {
        val raw = fixture()
        val headerPrefixed = ByteArray(WorldDsSaveReader.HEADER_BYTES + raw.size) { 0x11 }
        raw.copyInto(headerPrefixed, WorldDsSaveReader.HEADER_BYTES)
        val viaRegistry = SaveReaderRegistry().read(ByteArrayInputStream(headerPrefixed))
        assertEquals(WorldDsSaveReader.GAME_ID, viaRegistry.gameId)
        assertEquals(1, viaRegistry.activeCount)
        assertEquals(revision(headerPrefixed), viaRegistry.contentRevision)
        val withFooter = annotateWithDesmumeFooter(raw)
        val footered = WorldDsSaveReader().read(withFooter)
        assertEquals("Test", footered.records.single().storedName)
        assertEquals(revision(withFooter), footered.contentRevision)
        val combined = annotateWithDesmumeFooter(headerPrefixed)
        assertEquals("Test", WorldDsSaveReader().read(combined).records.single().storedName)
    }

    @Test fun bareSaveRegionTinyHeadersAndBinaryTrailersKeepTheSameImage() {
        val raw = fixture()
        val bare = raw.copyOf(WorldDsSaveReader.SAVE_BYTES)
        assertEquals("Test", WorldDsSaveReader().read(bare).records.single().storedName)
        val tinyHeader = ByteArray(0x20) { 0x22 } + raw
        assertEquals("Test", WorldDsSaveReader().read(tinyHeader).records.single().storedName)
        val trailer = raw + binaryTrailer(raw.size)
        val trailered = WorldDsSaveReader().read(trailer)
        assertEquals("Test", trailered.records.single().storedName)
        assertEquals(revision(trailer), trailered.contentRevision)
    }

    private fun binaryTrailer(payloadBytes: Int): ByteArray {
        val cookie = "|-DESMUME SAVE-|".toByteArray(Charsets.US_ASCII)
        val out = ByteArray(24 + cookie.size)
        put(out, 4, payloadBytes.toLong())
        cookie.copyInto(out, 24)
        return out
    }

    private fun annotateWithDesmumeFooter(payload: ByteArray): ByteArray {
        val notice = "|<--Snip above here to create a raw sav by excluding this DeSmuME savedata footer:".toByteArray(Charsets.US_ASCII)
        val cookie = "|-DESMUME SAVE-|".toByteArray(Charsets.US_ASCII)
        val out = ByteArray(payload.size + notice.size + 24 + cookie.size)
        payload.copyInto(out)
        notice.copyInto(out, payload.size)
        put(out, payload.size + notice.size + 4, payload.size.toLong())
        cookie.copyInto(out, payload.size + notice.size + 24)
        return out
    }

    @Test fun unknownGlyphsRemainVisibleAndNativeBytesAreRetained() {
        val bytes = fixture()
        put(bytes, 0x234 + 8, 0x1234, 2)
        val record = WorldDsSaveReader().read(bytes).records.single()
        assertTrue(record.storedName.startsWith("\uFFFD"))
        assertTrue(RecordIssue.UNKNOWN_NAME_CODES in record.issues)
        assertEquals(RecordMembership.NEEDS_REVIEW, record.membership)
        assertNotNull(record.nativeRecordBase64)
    }

    @Test fun categoryExpAndNativeStatsChangesParticipateInComparisonAndFingerprint() {
        val bytes = fixture()
        val before = WorldDsSaveReader().read(bytes)
        put(bytes, 0x234 + 0x78, 100)
        val after = WorldDsSaveReader().read(bytes)
        assertTrue(ObservedField.PROGRESSION in IdentityEvidenceComparison.compare(before, after).candidates.single().changedFields)
        val a = NativeTransferOrigin("one", "snapshot", "revision", before.gameId, before.formatProfile, 1, before.records.single(), true)
        val b = a.copy(record = after.records.single())
        assertNotEquals(DestinationPreviewPlanner.preview(a, "destination", before, SaveContainer.PARTY).fingerprint,
            DestinationPreviewPlanner.preview(b, "destination", before, SaveContainer.PARTY).fingerprint)
    }

    @Test fun folderDiscoveryListsSizeMatchesUnderAnyNameAndNeverDecidesSupport() {
        assertTrue(SaveDiscoveryPolicy.isCandidate("Digimon World DS (USA).sav", 65536))
        assertTrue(SaveDiscoveryPolicy.isCandidate("my story.SAV", null))
        assertTrue(SaveDiscoveryPolicy.isCandidate("my story.dsv", null))
        assertTrue(SaveDiscoveryPolicy.isCandidate("anything at all", (WorldDsSaveReader.FILE_BYTES + WorldDsSaveReader.HEADER_BYTES).toLong()))
        assertTrue(SaveDiscoveryPolicy.isCandidate("anything at all", WorldDsSaveReader.SAVE_BYTES.toLong()))
        assertTrue(SaveDiscoveryPolicy.isCandidate("anything at all", (WorldDsSaveReader.SAVE_BYTES + WorldDsSaveReader.HEADER_BYTES).toLong()))
        assertTrue(SaveDiscoveryPolicy.isCandidate("my story.sav.bak", 65536))
        assertFalse(SaveDiscoveryPolicy.isCandidate("my story.sav", 1234))
        assertFalse(SaveDiscoveryPolicy.isCandidate("my story.dsv", 1234))
        assertFalse(SaveDiscoveryPolicy.isCandidate("my story.dss", 1234))
        assertFalse(SaveDiscoveryPolicy.isCandidate("anything at all", 1234))
        assertFalse(SaveDiscoveryPolicy.isCandidate("anything at all", 70000))
    }

    @Test fun finalRecordIsReadAndPartyOrderIsIndependentOfRecordTablePosition() {
        val bytes = fixture()
        bytes.copyOfRange(0x234, 0x3B4).copyInto(bytes, 0x234 + 39 * 0x180)
        bytes.fill(0, 0x234, 0x3B4)
        put(bytes, 0x20C, 0)
        put(bytes, 0x20C + 3 * 4, 7)
        val record = WorldDsSaveReader().read(bytes).records.single()
        assertEquals(4, record.position)
        assertTrue(record.recordKey.endsWith("record:39"))
        assertEquals(SaveContainer.PARTY, record.container)
        assertEquals("Test", record.storedName)
    }
}
