package com.tamerslab.gamesave

import java.io.ByteArrayInputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class WorldDsResearchInspectorTest {
    private fun put(bytes: ByteArray, offset: Int, value: Long, width: Int = 4) {
        repeat(width) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }
    private fun raw(): ByteArray = ByteArray(0x10000).also { bytes ->
        "DSDIGI\u0000\u0000".toByteArray().copyInto(bytes, 0x10)
        put(bytes, 0x20C, 15)
        val r = 0x234
        put(bytes, r, 15); put(bytes, r + 4, 23)
        put(bytes, r + 8, 0x1234, 2); put(bytes, r + 10, 0x5678, 2); put(bytes, r + 12, 0xFFFF, 2)
        put(bytes, r + 0x3C, 700); put(bytes, r + 0x40, 1000)
        put(bytes, r + 0x44, 200, 2); put(bytes, r + 0x46, 300, 2)
        put(bytes, r + 0x48, 80, 2); put(bytes, r + 0x4A, 70, 2)
        put(bytes, r + 0x4C, 60, 2); put(bytes, r + 0x4E, 50, 2)
        put(bytes, r + 0x52, 99, 2); put(bytes, r + 0x74, 42, 2)
        repeat(8) { put(bytes, r + 0x78 + it * 4, (it * 10).toLong()) }
        put(bytes, r + 0x114, 99, 2) // Unmapped ancestry remains in exact bytes.
    }
    private fun dsv(payload: ByteArray, version: Int = 0): ByteArray {
        val notice = "|<--Snip above here to create a raw sav by excluding this DeSmuME savedata footer:".toByteArray()
        val cookie = "|-DESMUME SAVE-|".toByteArray()
        return ByteArray(payload.size + notice.size + 24 + cookie.size).also { bytes ->
            payload.copyInto(bytes); notice.copyInto(bytes, payload.size)
            val meta = payload.size + notice.size
            put(bytes, meta, payload.size.toLong()); put(bytes, meta + 4, payload.size.toLong())
            put(bytes, meta + 8, 3); put(bytes, meta + 12, 2); put(bytes, meta + 16, payload.size.toLong())
            put(bytes, meta + 20, version.toLong()); cookie.copyInto(bytes, bytes.size - cookie.size)
        }
    }

    @Test fun decodesNativeFactsWithoutInventingPersonalityUnicodeOrUnifiedExp() {
        val source = raw()
        val original = source.copyOf()
        val result = WorldDsResearchInspector.inspect(source)
        assertEquals(DsResearchContainer.RAW_CANDIDATE, result.container)
        val record = result.blocks.single().records.single()
        assertEquals(15L, record.localId)
        assertEquals(23L, record.nativeSpeciesId)
        assertEquals(listOf(0x1234, 0x5678), record.nameCodes)
        assertEquals(42, record.level)
        assertEquals(99, record.aptitude)
        assertEquals(listOf(0L, 10L, 20L, 30L, 40L, 50L, 60L, 70L), record.expCounters)
        assertEquals(DsResearchLocation.PARTY_REFERENCE, record.location)
        assertEquals(DsNativeStats(700, 1000, 200, 300, 80, 70, 60, 50), record.stats)
        assertArrayEquals(source.copyOfRange(0x234, 0x234 + 0x180), Base64.getDecoder().decode(record.nativeRecordBase64))
        assertArrayEquals(original, source)
    }

    @Test fun dsvAndRawSharePayloadRevisionButRetainDifferentContainerRevision() {
        val bytes = raw()
        val unwrapped = WorldDsResearchInspector.inspect(bytes)
        val wrapped = WorldDsResearchInspector.inspect(dsv(bytes))
        assertEquals(DsResearchContainer.DESMUME_DSV_V0, wrapped.container)
        assertEquals(unwrapped.payloadRevision, wrapped.payloadRevision)
        assertNotEquals(unwrapped.contentRevision, wrapped.contentRevision)
        assertEquals(unwrapped.blocks, wrapped.blocks)
    }

    @Test fun allCandidateBlocksRemainSeparateWithoutSelectingNewest() {
        val bytes = raw()
        bytes.copyOfRange(0, 0x7DC0).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
        put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x74, 43, 2)
        val blocks = WorldDsResearchInspector.inspect(bytes).blocks
        assertEquals(listOf(0, WorldDsSaveReader.COPY_STRIDE), blocks.map { it.offset })
        assertEquals(listOf(42, 43), blocks.map { it.records.single().level })
    }

    @Test fun farmReferencesAreInspectionOnlyAndDoNotBecomeBankRecords() {
        val bytes = raw()
        put(bytes, 0x20C, 0)
        put(bytes, 0x3E40, 100)
        put(bytes, 0x3E40 + 0x44, 15)
        assertEquals(DsResearchLocation.FARM_REFERENCE, WorldDsResearchInspector.inspect(bytes).blocks.single().records.single().location)
        put(bytes, 0x3E40, 0)
        assertEquals(DsResearchLocation.UNASSIGNED, WorldDsResearchInspector.inspect(bytes).blocks.single().records.single().location)
    }

    @Test fun contradictoryMembershipDuplicateIdsAndDanglingReferencesStayVisible() {
        val bytes = raw()
        bytes.copyOfRange(0x234, 0x234 + 0x180).copyInto(bytes, 0x234 + 0x180)
        put(bytes, 0x3E40, 100); put(bytes, 0x3E40 + 0x44, 15)
        put(bytes, 0x210, 999)
        val block = WorldDsResearchInspector.inspect(bytes).blocks.single()
        assertEquals(1, block.danglingReferences)
        block.records.forEach {
            assertEquals(DsResearchLocation.CONFLICT, it.location)
            assertTrue(DsResearchIssue.DUPLICATE_LOCAL_ID in it.issues)
            assertTrue(DsResearchIssue.CONFLICTING_LOCATION in it.issues)
        }
    }

    @Test fun guestLentEggAndExchangeFlagsAreNotOrdinaryOwnedImports() {
        val bytes = raw()
        put(bytes, 0x234 + 0x108, 0x36, 2)
        val issues = WorldDsResearchInspector.inspect(bytes).blocks.single().records.single().issues
        assertTrue(issues.containsAll(listOf(DsResearchIssue.GUEST, DsResearchIssue.LENT,
            DsResearchIssue.EGG, DsResearchIssue.OTHER_DS_RECORD)))
    }

    @Test fun wrongSignatureOrIncompleteBlockCannotProduceAResearchRoster() {
        for (bytes in listOf(ByteArray(0x10000), raw().copyOf(0x7DBF), raw().also { it[0x10] = 0 })) {
            assertThrows(SaveReadException::class.java) { WorldDsResearchInspector.inspect(bytes) }
        }
    }

    @Test fun malformedDsvFooterDoesNotFallBackToRawSignatureScan() {
        for (bytes in listOf(dsv(raw(), 1), dsv(raw()).also { it[it.size - 15 - 24 + 4] = 1 },
            dsv(raw()).also { it[it.lastIndex] = 0 })) {
            val error = assertThrows(SaveReadException::class.java) { WorldDsResearchInspector.inspect(bytes) }
            assertEquals(SaveReadFailure.INVALID_CONTAINER, error.reason)
        }
    }

    @Test fun damagedSnipNoticeOrBinaryOnlyTrailerStillExposeTheSamePayload() {
        val bytes = raw()
        val unwrapped = WorldDsResearchInspector.inspect(bytes)
        val damagedNotice = dsv(bytes).also { it[0x10000] = 0 }
        val fromDamaged = WorldDsResearchInspector.inspect(damagedNotice)
        assertEquals(DsResearchContainer.DESMUME_DSV_V0, fromDamaged.container)
        assertEquals(unwrapped.payloadRevision, fromDamaged.payloadRevision)
        assertEquals(unwrapped.blocks, fromDamaged.blocks)
        val cookie = "|-DESMUME SAVE-|".toByteArray()
        val trailer = ByteArray(24 + cookie.size).also {
            put(it, 4, bytes.size.toLong()); cookie.copyInto(it, 24)
        }
        val fromBinary = WorldDsResearchInspector.inspect(bytes + trailer)
        assertEquals(DsResearchContainer.DESMUME_DSV_V0, fromBinary.container)
        assertEquals(unwrapped.payloadRevision, fromBinary.payloadRevision)
        assertEquals(unwrapped.blocks, fromBinary.blocks)
    }

    @Test fun excessiveCandidatesAreRejectedRatherThanAllocatingUnboundedRecords() {
        val bytes = ByteArray(WorldDsSaveReader.COPY_STRIDE * 33)
        repeat(33) { raw().copyOfRange(0, 0x7DC0).copyInto(bytes, it * WorldDsSaveReader.COPY_STRIDE) }
        val error = assertThrows(SaveReadException::class.java) { WorldDsResearchInspector.inspect(bytes) }
        assertEquals(SaveReadFailure.AMBIGUOUS_FORMAT, error.reason)
    }

    @Test fun boundedStreamAndByteArrayInputsRejectOversizedData() {
        val bytes = ByteArray(WorldDsResearchInspector.MAX_INPUT_BYTES + 1)
        assertThrows(SaveReadException::class.java) { WorldDsResearchInspector.inspect(bytes) }
        assertThrows(SaveReadException::class.java) { WorldDsResearchInspector.inspect(ByteArrayInputStream(bytes)) }
        assertEquals(WorldDsResearchInspector.inspect(raw()), WorldDsResearchInspector.inspect(ByteArrayInputStream(raw())))
    }

    @Test fun aggregateSummaryDoesNotPrintNativeIdsNamesStatsOrRecordBytes() {
        val summary = WorldDsResearchInspector.inspect(raw()).summary().joinToString("\n")
        assertTrue(summary.contains("blocks=1"))
        assertTrue(summary.contains("party_references=1"))
        assertTrue(summary.contains("identity=unverified region=unverified writes=disabled"))
        assertFalse(summary.contains("1234"))
        assertFalse(summary.contains("700"))
    }

    @Test fun signatureOnlyResearchFixtureDoesNotMatchTheObservedDrasticProfile() {
        val error = assertThrows(SaveReadException::class.java) {
            SaveReaderRegistry().read(ByteArrayInputStream(raw()))
        }
        assertEquals(SaveReadFailure.UNRECOGNIZED_LAYOUT, error.reason)
    }

    @Test fun unsupportedNocashWrapperCannotBeScannedAsARawSave() {
        val bytes = raw()
        "NocashGbaBackupMediaSavDataFile".toByteArray().copyInto(bytes)
        "DSDIGI\u0000\u0000".toByteArray().copyInto(bytes, 0x110)
        assertThrows(SaveReadException::class.java) { WorldDsResearchInspector.inspect(bytes) }
    }
}
