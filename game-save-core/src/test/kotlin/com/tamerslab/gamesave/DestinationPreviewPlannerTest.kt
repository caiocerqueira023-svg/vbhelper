package com.tamerslab.gamesave

import org.junit.Assert.*
import org.junit.Test

class DestinationPreviewPlannerTest {
    private val record = ObservedDigimon("main:2", SaveContainer.BANK, 1, 10, "Partner", 42, 1,
        123, RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private val native = NativeTransferOrigin("individual", "snapshot:1", "origin-revision", "game", "profile", 1, record, true)
    private val destination = SaveObservation("game", "profile", 1, "destination-revision", emptyList(), emptyList())

    @Test fun sameFormatIsNotPermissionToWriteOrClaimLosslessReturn() {
        val preview = DestinationPreviewPlanner.preview(native, "save", destination, SaveContainer.BANK)
        assertTrue(preview.sameNativeFormat)
        assertTrue(DestinationBlocker.WRITER_UNAVAILABLE in preview.blockers)
        assertTrue(DestinationBlocker.INSERT_UNSUPPORTED in preview.blockers)
        assertTrue(DestinationBlocker.IDENTITY_REVIEW_REQUIRED in preview.blockers)
        assertFalse(preview.canExecute)
        assertEquals(TransferFieldDisposition.RETAINED_IN_STORAGE, preview.fields.getValue(TransferField.NATIVE_RECORD))
        assertEquals(TransferFieldDisposition.UNVALIDATED, preview.fields.getValue(TransferField.PROGRESSION))
    }

    @Test fun claimedInsertCapabilityDoesNotEnableAnUnimplementedWriter() {
        val preview = DestinationPreviewPlanner.preview(native.copy(provisionalIdentity = false), "save",
            destination.copy(capabilities = SaveCapabilities(canInsert = true)), SaveContainer.PARTY)
        assertFalse(DestinationBlocker.INSERT_UNSUPPORTED in preview.blockers)
        assertTrue(DestinationBlocker.WRITER_UNAVAILABLE in preview.blockers)
        assertFalse(preview.canExecute)
    }

    @Test fun crossGameHasNoGuessedSpeciesPersonalityOrProgressConversion() {
        val preview = DestinationPreviewPlanner.preview(native, "save", destination.copy(gameId = "other"), SaveContainer.BANK)
        assertFalse(preview.sameNativeFormat)
        assertTrue(DestinationBlocker.CONVERSION_UNAVAILABLE in preview.blockers)
        for (field in listOf(TransferField.SPECIES, TransferField.PERSONALITY, TransferField.PROGRESSION)) {
            assertEquals(TransferFieldDisposition.UNMAPPED, preview.fields.getValue(field))
        }
    }

    @Test fun farmIsExplicitlyOutOfScopeWithoutBlockingBankPlanning() {
        val farm = DestinationPreviewPlanner.preview(native, "save", destination, SaveContainer.FARM)
        assertTrue(DestinationBlocker.FARM_OUT_OF_SCOPE in farm.blockers)
        assertFalse(DestinationBlocker.FARM_OUT_OF_SCOPE in
            DestinationPreviewPlanner.preview(native, "save", destination, SaveContainer.BANK).blockers)
    }

    @Test fun candidatePresenceIsReviewEvidenceEvenInReviewRowsAndNeverAnAutomaticMerge() {
        val preview = DestinationPreviewPlanner.preview(native, "save", destination.copy(records = listOf(
            record, record.copy(recordKey = "farm:1", container = SaveContainer.FARM, membership = RecordMembership.NEEDS_REVIEW))), SaveContainer.BANK)
        assertEquals(2, preview.possibleNativeMatches)
        assertTrue(DestinationBlocker.POSSIBLE_EXISTING_INDIVIDUAL in preview.blockers)
        assertFalse(preview.canExecute)
    }

    @Test fun crossGameCandidateCoincidenceDoesNotSuggestIdentity() {
        val preview = DestinationPreviewPlanner.preview(native, "save", destination.copy(gameId = "other", records = listOf(record)), SaveContainer.BANK)
        assertEquals(0, preview.possibleNativeMatches)
    }

    @Test fun missingNativeBytesAndUncertainSourceStayBlocked() {
        val preview = DestinationPreviewPlanner.preview(native.copy(record = record.copy(
            nativeRecordBase64 = null, membership = RecordMembership.NEEDS_REVIEW)), "save", destination, SaveContainer.BANK)
        assertTrue(DestinationBlocker.NATIVE_DATA_MISSING in preview.blockers)
        assertTrue(DestinationBlocker.SOURCE_RECORD_UNCERTAIN in preview.blockers)
    }

    @Test fun fingerprintDetectsDestinationSourcePolicyAndNativeChanges() {
        val first = DestinationPreviewPlanner.preview(native, "save", destination, SaveContainer.BANK)
        val changes = listOf(
            DestinationPreviewPlanner.preview(native, "other-save", destination, SaveContainer.BANK),
            DestinationPreviewPlanner.preview(native, "save", destination.copy(contentRevision = "changed"), SaveContainer.BANK),
            DestinationPreviewPlanner.preview(native, "save", destination.copy(codecVersion = 2), SaveContainer.BANK),
            DestinationPreviewPlanner.preview(native, "save", destination, SaveContainer.PARTY),
            DestinationPreviewPlanner.preview(native.copy(snapshotKey = "snapshot:2"), "save", destination, SaveContainer.BANK),
            DestinationPreviewPlanner.preview(native.copy(record = record.copy(level = 43)), "save", destination, SaveContainer.BANK),
            DestinationPreviewPlanner.preview(native.copy(provisionalIdentity = false), "save", destination, SaveContainer.BANK),
            DestinationPreviewPlanner.preview(native, "save", destination.copy(capabilities = SaveCapabilities(canInsert = true)), SaveContainer.BANK),
        )
        changes.forEach { assertNotEquals(first.fingerprint, it.fingerprint) }
        assertEquals(first.fingerprint, DestinationPreviewPlanner.preview(native, "save", destination, SaveContainer.BANK).fingerprint)
    }

    @Test fun opaqueIdentityIsSupportedWithoutFallingBackToNumericCandidate() {
        val opaque = record.copy(nativeIdentity = "compound-key", identityCandidate = 0)
        val preview = DestinationPreviewPlanner.preview(native.copy(record = opaque), "save",
            destination.copy(records = listOf(opaque, record)), SaveContainer.BANK)
        assertEquals(1, preview.possibleNativeMatches)
        val missing = record.copy(identityCandidate = 0)
        assertEquals(0, DestinationPreviewPlanner.preview(native.copy(record = missing), "save",
            destination.copy(records = listOf(missing)), SaveContainer.BANK).possibleNativeMatches)
    }

    @Test fun desktopProbeExcludesFarmAndDoesNotLeakNamesNativeValuesOrFingerprints() {
        val source = destination.copy(records = listOf(record, record.copy(recordKey = "farm:1", container = SaveContainer.FARM),
            record.copy(recordKey = "tail:1", membership = RecordMembership.NEEDS_REVIEW)))
        val lines = destinationPlanningSummary(source, destination.copy(records = listOf(record)))
        assertTrue(lines.contains("planning_only=true eligible_main=1 farm_excluded=1"))
        assertTrue(lines.contains("same_format=1 with_possible_matches=1 executable=0"))
        assertFalse(lines.joinToString().contains("Partner"))
        assertFalse(lines.joinToString().contains("AQID"))
        assertTrue(destinationPlanningSummary(destination, destination).contains("same_format=0 with_possible_matches=0 executable=0"))
    }
}
