package com.github.nacabaro.vbhelper.gamesave

/**
 * Section 11 commit/recovery groundwork for the per-title writer ladder (risk-register rung 1).
 *
 * A byte-preserving rewrite is the weakest intent: the candidate output must equal the planned
 * source bytes exactly. Passing it validates journal order, backup/preflight checks, readback
 * verification and recovery decisions without claiming any game can load a modified save, so no
 * production writer capability is enabled by this file.
 */
enum class WriteIntentKind { NOOP_REWRITE }

enum class TransferJournalState { PREPARED, BACKED_UP, CANDIDATE_READY, WRITTEN, VERIFIED, ACKNOWLEDGED, RECOVERY_REQUIRED }

data class PlannedDocumentChange(val description: String, val candidateRevision: String, val byteCount: Int) {
    init {
        require(description.isNotBlank() && description.length <= 120)
        require(candidateRevision.isNotBlank() && byteCount in 0..(64 * 1024 * 1024))
    }
}

/** Candidate output hashes and affected-file descriptions, bound to one source revision. */
data class TransferCommitPlan(
    val operationId: String,
    val intent: WriteIntentKind,
    val sourceRevision: String,
    val affectedDocuments: List<PlannedDocumentChange>,
) {
    init {
        require(operationId.isNotBlank() && operationId.length <= 200 && sourceRevision.isNotBlank())
        require(affectedDocuments.isNotEmpty() && affectedDocuments.size <= 16)
    }
}

object TransferCommitPlanBuilder {
    /** Rung 1: the candidate is the source bytes unchanged; planning rejects mismatched bytes. */
    fun bytePreservingRewrite(operationId: String, sourceBytes: ByteArray, observedRevision: String): TransferCommitPlan {
        val calculated = revision(sourceBytes)
        require(calculated == observedRevision) { "Source bytes do not match the planned revision; a new plan is required" }
        return TransferCommitPlan(operationId, WriteIntentKind.NOOP_REWRITE, calculated,
            listOf(PlannedDocumentChange("source document", calculated, sourceBytes.size)))
    }
}

data class TransferJournalRecord(
    val plan: TransferCommitPlan,
    val state: TransferJournalState = TransferJournalState.PREPARED,
    val backupRevision: String? = null,
    val detail: String? = null,
) {
    val candidateRevision: String get() = plan.affectedDocuments.first().candidateRevision

    /** Ordered transitions only; a backup revision is mandatory before any candidate is trusted. */
    fun advance(next: TransferJournalState, backupRevision: String? = this.backupRevision,
        detail: String? = null): TransferJournalRecord {
        val allowed = when (state) {
            TransferJournalState.PREPARED -> next == TransferJournalState.BACKED_UP && !backupRevision.isNullOrBlank()
            TransferJournalState.BACKED_UP -> next == TransferJournalState.CANDIDATE_READY
            TransferJournalState.CANDIDATE_READY -> next == TransferJournalState.WRITTEN || next == TransferJournalState.RECOVERY_REQUIRED
            TransferJournalState.WRITTEN -> next == TransferJournalState.VERIFIED || next == TransferJournalState.RECOVERY_REQUIRED
            TransferJournalState.VERIFIED -> next == TransferJournalState.ACKNOWLEDGED
            TransferJournalState.ACKNOWLEDGED, TransferJournalState.RECOVERY_REQUIRED -> false
        }
        check(allowed) { "Illegal transfer journal transition ${state.name}->${next.name}" }
        return copy(state = next, backupRevision = backupRevision, detail = detail)
    }

    /** Release of the source representation is allowed only after verified destination persistence. */
    val sourceReleaseAllowed: Boolean get() = state == TransferJournalState.ACKNOWLEDGED
}

enum class TransferRecoveryDecision { RESUME_WRITE, COMPLETE_VERIFICATION, MANUAL_RECONCILIATION }

object TransferRecovery {
    /**
     * A destination that changed after this operation wrote it is never overwritten blindly;
     * only an unwritten destination may resume, and an already-written candidate is re-verified.
     * The source check deliberately precedes the candidate check because a rung-1 candidate
     * equals its source, where resuming the no-op write is the conservative outcome.
     */
    fun decide(record: TransferJournalRecord, sourceRevision: String, candidateRevision: String,
        destinationRevision: String?): TransferRecoveryDecision {
        require(record.state != TransferJournalState.ACKNOWLEDGED) { "Acknowledged operations are complete" }
        return when {
            destinationRevision == null -> TransferRecoveryDecision.MANUAL_RECONCILIATION
            record.state == TransferJournalState.WRITTEN ->
                if (destinationRevision == candidateRevision) TransferRecoveryDecision.COMPLETE_VERIFICATION
                else TransferRecoveryDecision.MANUAL_RECONCILIATION
            destinationRevision == sourceRevision -> TransferRecoveryDecision.RESUME_WRITE
            destinationRevision == candidateRevision -> TransferRecoveryDecision.COMPLETE_VERIFICATION
            else -> TransferRecoveryDecision.MANUAL_RECONCILIATION
        }
    }
}
