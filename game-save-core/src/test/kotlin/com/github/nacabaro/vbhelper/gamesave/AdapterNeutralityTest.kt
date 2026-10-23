package com.github.nacabaro.vbhelper.gamesave

import org.junit.Assert.*
import org.junit.Test

class AdapterNeutralityTest {
    private val observation = SaveObservation("other-game", "raw-v1", 1, revision(ByteArray(16)), emptyList(), emptyList())
    private fun reader(success: Boolean) = object : GameSaveReader {
        override val gameId = "other-game"
        override val maxInputBytes = 16
        override fun read(bytes: ByteArray) = if (success) observation else throw SaveReadException(SaveReadFailure.INVALID_CONTAINER)
    }
    @Test fun equalLengthFormatsAreRecognizedByContentInsteadOfArbitrarySelection() {
        assertEquals(observation, SaveReaderRegistry(listOf(reader(false), reader(true))).read(ByteArray(16).inputStream()))
        assertEquals(SaveReadFailure.AMBIGUOUS_FORMAT, assertThrows(SaveReadException::class.java) {
            SaveReaderRegistry(listOf(reader(true), reader(true))).read(ByteArray(16).inputStream())
        }.reason)
    }
    @Test fun opaqueNativeKeysDoNotRequireATimeStrangerNumericHash() {
        val record = ObservedDigimon("party:1", SaveContainer.PARTY, 1, 1, "Name", 2, 1, 0,
            RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID", nativeIdentity = "created:abcdef:compound-key")
        val source = observation.copy(records = listOf(record), capabilities = SaveCapabilities(canImportSnapshot = true))
        assertTrue(SnapshotImportPlanner.preview(source, listOf(record.recordKey)).canCommit)
        assertEquals(1, IdentityEvidenceComparison.compare(source, source).candidates.size)
        assertEquals(0, IdentityEvidenceComparison.compare(source, source).missingCandidatesBefore)
    }
}
