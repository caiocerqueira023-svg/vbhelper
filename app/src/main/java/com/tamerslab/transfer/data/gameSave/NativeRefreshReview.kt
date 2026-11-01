package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.ExternalIndividualBinding
import com.tamerslab.domain.transfer.NativeGameSnapshot
import com.tamerslab.gamesave.*

data class NativeRefreshItemReview(
    val binding: ExternalIndividualBinding,
    val previousSnapshot: NativeGameSnapshot,
    val previousRecord: ObservedDigimon,
    val incoming: ObservedDigimon,
    val appValues: RefreshAppValues,
    val incomingPersonality: String,
    val fields: RefreshFieldReview,
)
data class NativeRefreshReview(val draftId: String, val fingerprint: String, val items: List<NativeRefreshItemReview>) {
    val requiresIdentityConfirmation: Boolean get() = items.any { it.binding.identityConfidence != "VERIFIED" }
}
data class NativeRefreshAudit(
    val reviewFingerprint: String,
    val previousSnapshotId: Long,
    val reviewedSourceId: String,
    val choices: RefreshChoices,
    val beforeAppValues: RefreshAppValues,
    val afterAppValues: RefreshAppValues,
    val identityContinuityConfirmed: Boolean = false,
)
