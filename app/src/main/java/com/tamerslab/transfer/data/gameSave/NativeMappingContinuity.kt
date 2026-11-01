package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.NativeCanonicalMapping

/**
 * A user review names the species of one native identity, not of one revision of it. When a
 * refresh re-archives the same individual with the same native species, the review carries to
 * the refreshed snapshot; a changed species (an evolution) requires a fresh review.
 */
internal object NativeMappingContinuity {
    fun carried(previous: NativeCanonicalMapping?, incomingNativeSpeciesId: Long,
        refreshedSnapshotId: Long): NativeCanonicalMapping? {
        if (previous == null || previous.authority != "USER_CONFIRMED") return null
        if (previous.nativeSpeciesId != incomingNativeSpeciesId) return null
        return previous.copy(snapshotId = refreshedSnapshotId)
    }
}
