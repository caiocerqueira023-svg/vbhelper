package com.tamerslab.gamesave

import org.junit.Assert.*
import org.junit.Test

class TransferCommitTest {
    private val source = "synthetic-save-container".toByteArray()
    private val plan = TransferCommitPlanBuilder.bytePreservingRewrite("operation:1", source, revision(source))

    @Test fun planBuilderRejectsBytesThatNoLongerMatchThePlannedRevision() {
        val tampered = source.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            TransferCommitPlanBuilder.bytePreservingRewrite("operation:1", tampered, revision(source))
        }
    }

    @Test fun planBindsCandidateHashAndDocumentDescription() {
        assertEquals(WriteIntentKind.NOOP_REWRITE, plan.intent)
        assertEquals(revision(source), plan.sourceRevision)
        assertEquals(1, plan.affectedDocuments.size)
        plan.affectedDocuments.single().let {
            assertEquals(revision(source), it.candidateRevision)
            assertEquals(source.size, it.byteCount)
        }
    }

    @Test fun journalRejectsOutOfOrderAndTerminalTransitions() {
        val prepared = TransferJournalRecord(plan)
        assertThrows(IllegalStateException::class.java) { prepared.advance(TransferJournalState.CANDIDATE_READY) }
        assertThrows(IllegalStateException::class.java) { prepared.advance(TransferJournalState.WRITTEN) }
        val backedUp = prepared.advance(TransferJournalState.BACKED_UP, backupRevision = revision(source))
        assertThrows(IllegalStateException::class.java) { backedUp.advance(TransferJournalState.WRITTEN) }
        val written = backedUp.advance(TransferJournalState.CANDIDATE_READY).advance(TransferJournalState.WRITTEN)
        assertThrows(IllegalStateException::class.java) { written.advance(TransferJournalState.BACKED_UP) }
        val acknowledged = written.advance(TransferJournalState.VERIFIED).advance(TransferJournalState.ACKNOWLEDGED)
        assertThrows(IllegalStateException::class.java) { acknowledged.advance(TransferJournalState.WRITTEN) }
        val recovery = backedUp.advance(TransferJournalState.CANDIDATE_READY).advance(TransferJournalState.RECOVERY_REQUIRED)
        assertThrows(IllegalStateException::class.java) { recovery.advance(TransferJournalState.WRITTEN) }
    }

    @Test fun backupRevisionIsMandatoryBeforeCandidateReadiness() {
        assertThrows(IllegalStateException::class.java) { TransferJournalRecord(plan).advance(TransferJournalState.BACKED_UP) }
        assertThrows(IllegalStateException::class.java) { TransferJournalRecord(plan).advance(TransferJournalState.BACKED_UP, backupRevision = " ") }
    }

    @Test fun sourceReleaseRequiresVerifiedAcknowledgement() {
        var record = TransferJournalRecord(plan)
        assertFalse(record.sourceReleaseAllowed)
        record = record.advance(TransferJournalState.BACKED_UP, backupRevision = revision(source))
        assertFalse(record.sourceReleaseAllowed)
        record = record.advance(TransferJournalState.CANDIDATE_READY)
        assertFalse(record.sourceReleaseAllowed)
        record = record.advance(TransferJournalState.WRITTEN)
        assertFalse(record.sourceReleaseAllowed)
        record = record.advance(TransferJournalState.VERIFIED)
        assertFalse(record.sourceReleaseAllowed)
        record = record.advance(TransferJournalState.ACKNOWLEDGED)
        assertTrue(record.sourceReleaseAllowed)
        assertEquals(revision(source), record.backupRevision)
    }

    @Test fun recoveryResumesOnlyAnUnwrittenDestination() {
        val preparing = TransferJournalRecord(plan).advance(TransferJournalState.BACKED_UP, backupRevision = "backup")
            .advance(TransferJournalState.CANDIDATE_READY)
        assertEquals(TransferRecoveryDecision.RESUME_WRITE, TransferRecovery.decide(preparing,
            plan.sourceRevision, plan.affectedDocuments.first().candidateRevision, plan.sourceRevision))
    }

    @Test fun recoveryVerifiesAnAlreadyWrittenCandidateInsteadOfRewriting() {
        val preparing = TransferJournalRecord(plan).advance(TransferJournalState.BACKED_UP, backupRevision = "backup")
            .advance(TransferJournalState.CANDIDATE_READY)
        val sourceRevision = "source-revision"
        val candidateRevision = "candidate-revision"
        assertEquals(TransferRecoveryDecision.COMPLETE_VERIFICATION, TransferRecovery.decide(preparing,
            sourceRevision, candidateRevision, candidateRevision))
        assertEquals(TransferRecoveryDecision.COMPLETE_VERIFICATION, TransferRecovery.decide(
            preparing.advance(TransferJournalState.WRITTEN), sourceRevision, candidateRevision, candidateRevision))
    }

    @Test fun recoveryNeverOverwritesADestinationChangedAfterWriting() {
        val written = TransferJournalRecord(plan).advance(TransferJournalState.BACKED_UP, backupRevision = "backup")
            .advance(TransferJournalState.CANDIDATE_READY).advance(TransferJournalState.WRITTEN)
        assertEquals(TransferRecoveryDecision.MANUAL_RECONCILIATION, TransferRecovery.decide(written,
            "source-revision", "candidate-revision", "external-change-revision"))
    }

    @Test fun recoveryTreatsUnreadableDestinationsAsManualAndRejectsAcknowledgedOperations() {
        val prepared = TransferJournalRecord(plan)
        assertEquals(TransferRecoveryDecision.MANUAL_RECONCILIATION, TransferRecovery.decide(prepared,
            plan.sourceRevision, plan.affectedDocuments.first().candidateRevision, null))
        assertEquals(TransferRecoveryDecision.MANUAL_RECONCILIATION, TransferRecovery.decide(prepared,
            plan.sourceRevision, plan.affectedDocuments.first().candidateRevision, revision("unrelated".toByteArray())))
        val acknowledged = prepared.advance(TransferJournalState.BACKED_UP, backupRevision = "backup")
            .advance(TransferJournalState.CANDIDATE_READY).advance(TransferJournalState.WRITTEN)
            .advance(TransferJournalState.VERIFIED).advance(TransferJournalState.ACKNOWLEDGED)
        assertThrows(IllegalArgumentException::class.java) {
            TransferRecovery.decide(acknowledged, plan.sourceRevision, plan.affectedDocuments.first().candidateRevision, null)
        }
    }
}
