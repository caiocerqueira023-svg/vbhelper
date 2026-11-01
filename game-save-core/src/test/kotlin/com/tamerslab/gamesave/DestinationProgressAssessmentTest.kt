package com.tamerslab.gamesave

import org.junit.Assert.*
import org.junit.Test

class DestinationProgressAssessmentTest {
    private val record = ObservedDigimon("main:1", SaveContainer.PARTY, 1, 20, "Test", 3, 16, 7,
        RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private val anchor = RetainedNativeAnchor("game", "profile", 1, "1", "original", true, true)
    private val progress = TransferProgressContext(0, 0, 0, ProgressVector.of(), emptyList(), listOf(anchor))
    private val origin = NativeTransferOrigin("individual", "snapshot:1", "original", "game", "profile", 1, record, true, progress)
    private val destination = SaveObservation("game", "profile", 1, "destination", emptyList(), emptyList())

    private fun preview(source: NativeTransferOrigin = origin, target: SaveObservation = destination) =
        DestinationPreviewPlanner.preview(source, "target", target, SaveContainer.PARTY)

    @Test fun absentContextIsUnknownRatherThanZeroProgress() {
        val result = preview(origin.copy(progress = null))
        assertEquals(DestinationProgressStatus.CONTEXT_UNAVAILABLE, result.progress.status)
        assertTrue(DestinationBlocker.PROGRESS_CONTEXT_UNAVAILABLE in result.blockers)
        assertNull(result.progress.recordedChanges)
        assertFalse(result.canExecute)
    }

    @Test fun retainedAnchorAndEmptyRecordedLedgerDoNotGrantAWriterOrReturnGuarantee() {
        val result = preview()
        assertEquals(DestinationProgressStatus.NO_RECORDED_CHANGES, result.progress.status)
        assertEquals(anchor, result.progress.targetAnchor)
        assertTrue(result.progress.recordedChanges!!.isZero)
        assertFalse(result.canExecute)
        assertTrue(DestinationBlocker.WRITER_UNAVAILABLE in result.blockers)
    }

    @Test fun appChangesStayInTheirOwnUnitsAndRequireMapping() {
        val result = preview(origin.copy(progress = progress.copy(latestAppDeltaId = 2,
            recordedChanges = ProgressVector.of(mapOf("training.hp" to 10, "battles.won" to 1)))))
        assertEquals(DestinationProgressStatus.RECORDED_CHANGES_UNMAPPED, result.progress.status)
        assertEquals(10L, result.progress.recordedChanges!!.units["training.hp"])
        assertTrue(DestinationBlocker.PROGRESS_MAPPING_UNAVAILABLE in result.blockers)
        assertFalse(result.canExecute)
    }

    @Test fun nativeQueueAndFreshDestinationDifferencesPreventAnEmptyLedgerClaim() {
        val pending = preview(origin.copy(progress = progress.copy(pendingNativeSnapshotIds = listOf(3))))
        assertEquals(DestinationProgressStatus.NATIVE_CHANGES_UNRESOLVED, pending.progress.status)
        assertEquals(1, pending.progress.pendingNativeCount)
        assertTrue(DestinationBlocker.NATIVE_PROGRESS_UNRESOLVED in pending.blockers)
        val changed = preview(target = destination.copy(records = listOf(record.copy(nativeRecordBase64 = "BAUG"))))
        assertEquals(1, changed.progress.differingDestinationRecords)
        assertEquals(DestinationProgressStatus.NATIVE_CHANGES_UNRESOLVED, changed.progress.status)
        assertTrue(DestinationBlocker.DESTINATION_RECORD_CHANGED in changed.blockers)
    }

    @Test fun fingerprintBindsEvenNetZeroEventsAndPendingIdentityOrAnchorChanges() {
        val initial = preview().fingerprint
        val variants = listOf(progress.copy(accountRevision = 1), progress.copy(latestAppDeltaId = 2),
            progress.copy(pendingNativeSnapshotIds = listOf(3)),
            progress.copy(anchors = listOf(anchor.copy(originalDocumentRetained = false))),
            progress.copy(anchors = listOf(anchor.copy(snapshotKey = "2"))))
        variants.forEach { assertNotEquals(initial, preview(origin.copy(progress = it)).fingerprint) }
        val pendingA = preview(origin.copy(progress = progress.copy(pendingNativeSnapshotIds = listOf(3, 4)))).fingerprint
        val pendingB = preview(origin.copy(progress = progress.copy(pendingNativeSnapshotIds = listOf(4, 3)))).fingerprint
        assertEquals(pendingA, pendingB)
    }

    @Test fun anotherGamesAnchorIsNotPresentedAsTheDestinationsOriginal() {
        val result = preview(target = destination.copy(gameId = "other"))
        assertNull(result.progress.targetAnchor)
        assertTrue(DestinationBlocker.NATIVE_ANCHOR_UNAVAILABLE in result.blockers)
        assertFalse(result.canExecute)
    }
}
