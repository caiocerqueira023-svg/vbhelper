package com.tamerslab.transfer.data.gameSave

/**
 * Decides whether an archived World DS record may still dictate the form of the app representation.
 *
 * The game archive keeps a permanent snapshot of every imported or moved record, but the individual
 * can move on without the game: a watch round trip is the common case, and it may evolve the Digimon
 * there. The archived record then describes an older state, so re-applying its species would revert
 * the form the individual gained on the device while leaving that evolved form registered in the Dex.
 *
 * The game stays the form authority only while it produced something newer than the last device
 * observation:
 *  - a retained watch baseline captured after the archive snapshot proves the individual was used on
 *    a device afterwards, which makes the archived native form historical;
 *  - re-reading an unchanged native record (a save refresh, a copied record) must never revert
 *    app/device progress.
 */
internal object NativeReconcilePolicy {
    /** True when the current representation was produced after this archived record was captured. */
    fun watchObservationSupersedesSnapshot(
        snapshotImportedAt: Long,
        latestWatchCapturedAt: Long?,
    ): Boolean = latestWatchCapturedAt != null && latestWatchCapturedAt > snapshotImportedAt

    /**
     * True when applying [currentRecordJson] would only re-assert an older native state over a newer
     * device observation: the record itself did not change since [previousRecordJson] was archived.
     * A genuinely changed game record stays authoritative.
     */
    fun unchangedNativeRecordRevertsAppForm(
        previousSnapshotImportedAt: Long?,
        previousRecordJson: String?,
        currentRecordJson: String,
        latestWatchCapturedAt: Long?,
    ): Boolean = previousSnapshotImportedAt != null && previousRecordJson == currentRecordJson &&
        watchObservationSupersedesSnapshot(previousSnapshotImportedAt, latestWatchCapturedAt)
}
