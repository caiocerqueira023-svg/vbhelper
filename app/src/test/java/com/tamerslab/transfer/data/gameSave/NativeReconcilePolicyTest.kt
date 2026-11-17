package com.tamerslab.transfer.data.gameSave

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A World DS archive keeps the record it imported, but the individual can be used elsewhere
 * afterwards — a watch round trip can evolve it. These checks decide when that archived record is
 * still allowed to dictate the app form.
 */
class NativeReconcilePolicyTest {
    private val snapshotImportedAt = 1_000L
    private val watchAfterSnapshot = 2_000L
    private val watchBeforeSnapshot = 500L

    @Test fun aDeviceObservationAfterTheArchivedRecordSupersedesIt() {
        assertTrue(NativeReconcilePolicy.watchObservationSupersedesSnapshot(snapshotImportedAt, watchAfterSnapshot))
    }

    @Test fun anOlderOrMissingDeviceObservationLeavesTheGameAuthoritative() {
        assertFalse(NativeReconcilePolicy.watchObservationSupersedesSnapshot(snapshotImportedAt, watchBeforeSnapshot))
        assertFalse(NativeReconcilePolicy.watchObservationSupersedesSnapshot(snapshotImportedAt, snapshotImportedAt))
        assertFalse(NativeReconcilePolicy.watchObservationSupersedesSnapshot(snapshotImportedAt, null))
    }

    @Test fun anUnchangedRecordMustNotRevertAFormGainedOnTheWatch() {
        // The reported regression: the save was re-read after the watch evolution, the record bytes
        // did not change, and the archived pre-evolution form replaced the evolved one.
        assertTrue(NativeReconcilePolicy.unchangedNativeRecordRevertsAppForm(
            previousSnapshotImportedAt = snapshotImportedAt,
            previousRecordJson = """{"species":1}""",
            currentRecordJson = """{"species":1}""",
            latestWatchCapturedAt = watchAfterSnapshot,
        ))
    }

    @Test fun aChangedGameRecordStaysAuthoritative() {
        assertFalse(NativeReconcilePolicy.unchangedNativeRecordRevertsAppForm(
            previousSnapshotImportedAt = snapshotImportedAt,
            previousRecordJson = """{"species":1}""",
            currentRecordJson = """{"species":2}""",
            latestWatchCapturedAt = watchAfterSnapshot,
        ))
    }

    @Test fun withoutAPreviousSnapshotOrADeviceObservationTheGameStillApplies() {
        assertFalse(NativeReconcilePolicy.unchangedNativeRecordRevertsAppForm(
            previousSnapshotImportedAt = null,
            previousRecordJson = """{"species":1}""",
            currentRecordJson = """{"species":1}""",
            latestWatchCapturedAt = watchAfterSnapshot,
        ))
        assertFalse(NativeReconcilePolicy.unchangedNativeRecordRevertsAppForm(
            previousSnapshotImportedAt = snapshotImportedAt,
            previousRecordJson = """{"species":1}""",
            currentRecordJson = """{"species":1}""",
            latestWatchCapturedAt = null,
        ))
        assertFalse(NativeReconcilePolicy.unchangedNativeRecordRevertsAppForm(
            previousSnapshotImportedAt = snapshotImportedAt,
            previousRecordJson = """{"species":1}""",
            currentRecordJson = """{"species":1}""",
            latestWatchCapturedAt = watchBeforeSnapshot,
        ))
    }
}
