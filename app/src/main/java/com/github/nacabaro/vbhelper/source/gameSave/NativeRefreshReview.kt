package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.domain.transfer.ExternalIndividualBinding
import com.github.nacabaro.vbhelper.domain.transfer.NativeGameSnapshot
import com.github.nacabaro.vbhelper.gamesave.*

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
