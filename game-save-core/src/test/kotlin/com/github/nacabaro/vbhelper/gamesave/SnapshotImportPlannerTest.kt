package com.github.nacabaro.vbhelper.gamesave

import org.junit.Assert.*
import org.junit.Test

class SnapshotImportPlannerTest {
    private val record = ObservedDigimon("main:1", SaveContainer.PARTY, 1, 10, "Nickname", 42, 16,
        123, RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private val observation = SaveObservation("game", "format", 1, "a".repeat(64), listOf(record),
        emptyList(), SaveCapabilities(canImportSnapshot = true))

    @Test fun currentTimeStrangerResearchGatesBlockAcquisitionButKeepNativeValues() {
        val source = observation.copy(capabilities = SaveCapabilities(), notices = listOf(
            ObservationNotice.OWNERSHIP_RESEARCH_PENDING, ObservationNotice.IDENTITY_RESEARCH_PENDING,
            ObservationNotice.SPECIES_MAPPING_PENDING))
        val preview = SnapshotImportPlanner.preview(source, listOf(record.recordKey))
        assertFalse(preview.canCommit)
        assertEquals(record, preview.items.single().record)
        assertTrue(ImportBlocker.ADAPTER_VIEW_ONLY in preview.items.single().blockers)
        assertTrue(ImportBlocker.OWNERSHIP_UNVERIFIED in preview.items.single().blockers)
        assertTrue(ImportBlocker.IDENTITY_UNVERIFIED in preview.items.single().blockers)
        assertTrue(preview.mappingPending)
    }

    @Test fun confirmedOwnedUnmappedRecordsCanBePlannedAsInactiveArchives() {
        val preview = SnapshotImportPlanner.preview(observation.copy(notices = listOf(ObservationNotice.SPECIES_MAPPING_PENDING)), listOf(record.recordKey))
        assertTrue(preview.canCommit)
        assertTrue(preview.mappingPending)
        assertEquals(ImportItemAction.NEW_SNAPSHOT, preview.items.single().action)
        assertFalse(preview.removesFromGame)
    }

    @Test fun duplicateCandidateInAReviewTailBlocksAnOtherwiseActiveSelection() {
        val tail = record.copy(recordKey = "tail", membership = RecordMembership.NEEDS_REVIEW)
        val preview = SnapshotImportPlanner.preview(observation.copy(records = listOf(record, tail)), listOf(record.recordKey))
        assertFalse(preview.canCommit)
        assertTrue(ImportBlocker.AMBIGUOUS_IDENTITY in preview.items.single().blockers)
    }

    @Test fun reviewOnlyAndMissingSnapshotRecordsCannotBeImported() {
        val source = observation.copy(records = listOf(record.copy(membership = RecordMembership.NEEDS_REVIEW, nativeRecordBase64 = null)))
        val item = SnapshotImportPlanner.preview(source, listOf(record.recordKey)).items.single()
        assertTrue(ImportBlocker.RECORD_NEEDS_REVIEW in item.blockers)
        assertTrue(ImportBlocker.NATIVE_DATA_MISSING in item.blockers)
    }

    @Test fun exactItemReceiptsPreventRepeatedAcquisitionButDoNotBlockOtherSelections() {
        val other = record.copy(recordKey = "main:2", identityCandidate = 456)
        val preview = SnapshotImportPlanner.preview(observation.copy(records = listOf(record, other)),
            listOf(record.recordKey, other.recordKey), setOf(record.recordKey))
        assertEquals(ImportItemAction.ALREADY_IMPORTED, preview.items.first().action)
        assertEquals(ImportItemAction.NEW_SNAPSHOT, preview.items.last().action)
        assertEquals(1, preview.newItemCount)
        assertFalse(SnapshotImportPlanner.preview(observation, listOf(record.recordKey), setOf(record.recordKey)).canCommit)
        assertNotEquals(SnapshotImportPlanner.preview(observation, listOf(record.recordKey)).fingerprint,
            SnapshotImportPlanner.preview(observation, listOf(record.recordKey), setOf(record.recordKey)).fingerprint)
    }

    @Test fun emptyDuplicateOrMissingSelectionsAreRejected() {
        for (keys in listOf(emptyList(), listOf("missing"), listOf(record.recordKey, record.recordKey))) {
            assertThrows(IllegalArgumentException::class.java) { SnapshotImportPlanner.preview(observation, keys) }
        }
    }

    @Test fun previewFingerprintBindsProfileRevisionCodecAndSelectionButNotSelectionOrder() {
        val other = record.copy(recordKey = "main:2", identityCandidate = 456)
        val source = observation.copy(records = listOf(record, other))
        val a = SnapshotImportPlanner.preview(source, listOf(record.recordKey, other.recordKey))
        assertEquals(a.fingerprint, SnapshotImportPlanner.preview(source, listOf(other.recordKey, record.recordKey)).fingerprint)
        for (changed in listOf(source.copy(contentRevision = "b".repeat(64)), source.copy(codecVersion = 2), source.copy(formatProfile = "other"))) {
            assertNotEquals(a.fingerprint, SnapshotImportPlanner.preview(changed, listOf(record.recordKey, other.recordKey)).fingerprint)
        }
        assertNotEquals(a.fingerprint, SnapshotImportPlanner.preview(source, listOf(record.recordKey)).fingerprint)
    }

    @Test fun provisionalCopyScopesCandidateMatchingToEligibleRowsWithoutPromotingFarmReviewData() {
        val farm = record.copy(recordKey = "farm:1", container = SaveContainer.FARM, nativeSpeciesId = 99,
            membership = RecordMembership.NEEDS_REVIEW, issues = listOf(RecordIssue.FARM_OWNERSHIP_UNVERIFIED))
        val source = observation.copy(records = listOf(record, farm),
            notices = listOf(ObservationNotice.IDENTITY_RESEARCH_PENDING, ObservationNotice.FARM_RESEARCH_PENDING),
            capabilities = SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true))
        val main = SnapshotImportPlanner.preview(source, listOf(record.recordKey))
        assertTrue(main.canCommit)
        assertTrue(main.requiresIdentityReview)
        assertFalse(main.removesFromGame)
        assertFalse(SnapshotImportPlanner.preview(source, listOf(farm.recordKey)).canCommit)
        assertFalse(SnapshotImportPlanner.preview(source.copy(capabilities = SaveCapabilities(canImportSnapshot = true)), listOf(record.recordKey)).canCommit)
    }

    @Test fun provisionalModeStillRejectsMissingOrDuplicateEligibleIdentityAndUnverifiedOwnership() {
        val source = observation.copy(capabilities = SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true))
        assertFalse(SnapshotImportPlanner.preview(source.copy(records = listOf(record.copy(identityCandidate = 0))), listOf(record.recordKey)).canCommit)
        assertFalse(SnapshotImportPlanner.preview(source.copy(records = listOf(record, record.copy(recordKey = "main:2"))), listOf(record.recordKey)).canCommit)
        assertFalse(SnapshotImportPlanner.preview(source.copy(notices = listOf(ObservationNotice.OWNERSHIP_RESEARCH_PENDING)), listOf(record.recordKey)).canCommit)
    }
}
