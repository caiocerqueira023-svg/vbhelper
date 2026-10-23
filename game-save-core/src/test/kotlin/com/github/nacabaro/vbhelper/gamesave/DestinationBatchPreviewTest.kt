package com.github.nacabaro.vbhelper.gamesave

import org.junit.Assert.*
import org.junit.Test

class DestinationBatchPreviewTest {
    private fun record(candidate: Long) = ObservedDigimon("main:$candidate", SaveContainer.PARTY, 1, 20, "Partner", 3, 16,
        candidate, RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private fun origin(candidate: Long, snapshot: String, individual: String = "individual:$candidate",
        progress: TransferProgressContext? = null) = NativeTransferOrigin(individual, snapshot, "revision:$snapshot",
        "game", "profile", 1, record(candidate), true, progress)
    private val destination = SaveObservation("game", "profile", 1, "destination", emptyList(), emptyList())

    @Test fun batchRejectsDuplicateIndividualsAnchorsAndOversizedSelections() {
        assertThrows(IllegalArgumentException::class.java) {
            DestinationPreviewPlanner.batch(listOf(origin(1, "a"), origin(2, "b", individual = "individual:1")),
                "save", destination, SaveContainer.BANK)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DestinationPreviewPlanner.batch(listOf(origin(1, "a"), origin(2, "a")), "save", destination, SaveContainer.BANK)
        }
        val oversized = (1L..(DestinationPreviewPlanner.MAX_BATCH_ITEMS + 1)).map { origin(it, "snapshot:$it") }
        assertThrows(IllegalArgumentException::class.java) {
            DestinationPreviewPlanner.batch(oversized, "save", destination, SaveContainer.BANK)
        }
        assertEquals(DestinationPreviewPlanner.MAX_BATCH_ITEMS, DestinationPreviewPlanner.batch(
            oversized.take(DestinationPreviewPlanner.MAX_BATCH_ITEMS), "save", destination, SaveContainer.BANK).items.size)
        assertThrows(IllegalArgumentException::class.java) {
            DestinationPreviewPlanner.batch(emptyList(), "save", destination, SaveContainer.BANK)
        }
    }

    @Test fun sharedCandidateEvidenceIsFlaggedWithoutTouchingOtherItems() {
        val preview = DestinationPreviewPlanner.batch(listOf(origin(7, "a", "individual:one"),
            origin(7, "b", "individual:two"), origin(9, "c")), "save", destination, SaveContainer.BANK)
        assertTrue(DestinationBlocker.BATCH_IDENTITY_CONFLICT in preview.items[0].blockers)
        assertTrue(DestinationBlocker.BATCH_IDENTITY_CONFLICT in preview.items[1].blockers)
        assertFalse(DestinationBlocker.BATCH_IDENTITY_CONFLICT in preview.items[2].blockers)
        assertTrue(DestinationBlocker.WRITER_UNAVAILABLE in preview.commonBlockers)
        assertFalse(preview.canExecute)
        assertFalse(preview.items.any { it.canExecute })
    }

    @Test fun batchFingerprintIgnoresSelectionOrderButBindsEveryItem() {
        val a = origin(1, "a")
        val b = origin(2, "b")
        val forward = DestinationPreviewPlanner.batch(listOf(a, b), "save", destination, SaveContainer.BANK)
        assertEquals(forward.fingerprint,
            DestinationPreviewPlanner.batch(listOf(b, a), "save", destination, SaveContainer.BANK).fingerprint)
        assertEquals(forward.fingerprint,
            DestinationPreviewPlanner.batch(listOf(a, b), "save", destination, SaveContainer.BANK).fingerprint)
        val pending = TransferProgressContext(3, 4, 5, ProgressVector.of(), listOf(6), emptyList())
        assertNotEquals(forward.fingerprint, DestinationPreviewPlanner.batch(listOf(a, b.copy(progress = pending)),
            "save", destination, SaveContainer.BANK).fingerprint)
        assertNotEquals(forward.fingerprint, DestinationPreviewPlanner.batch(listOf(a), "save", destination,
            SaveContainer.BANK).fingerprint)
    }

    @Test fun perItemRestrictionsStayOnTheirOwnItemsWithoutGrants() {
        val settled = TransferProgressContext(0, 0, 0, ProgressVector.of(), emptyList(), emptyList())
        val pending = settled.copy(pendingNativeSnapshotIds = listOf(9))
        val preview = DestinationPreviewPlanner.batch(listOf(origin(1, "a", progress = settled), origin(2, "b", progress = pending)),
            "save", destination, SaveContainer.BANK)
        assertEquals(DestinationProgressStatus.NO_RECORDED_CHANGES, preview.items[0].progress.status)
        assertEquals(DestinationProgressStatus.NATIVE_CHANGES_UNRESOLVED, preview.items[1].progress.status)
        assertFalse(DestinationBlocker.NATIVE_PROGRESS_UNRESOLVED in preview.commonBlockers)
        assertFalse(DestinationBlocker.NATIVE_PROGRESS_UNRESOLVED in preview.items[0].blockers)
        assertTrue(DestinationBlocker.NATIVE_PROGRESS_UNRESOLVED in preview.items[1].blockers)
        assertEquals("destination", preview.items[0].destinationRevision)
        assertEquals("destination", preview.destinationRevision)
        assertFalse(DestinationBlocker.BATCH_IDENTITY_CONFLICT in preview.commonBlockers)
    }
}
