package com.github.nacabaro.vbhelper.battle.offline.core

enum class BattleFinisherKind { POWER, FORM, JOGRESS, DUO }

enum class BattleFinisherPhase { FOCUS, TRANSFORM, REVEAL, CHARGE, RELEASE, IMPACT, AFTERMATH, RESTORE }

/** Immutable presentation contract. Its clock is independent of combat elapsed time. */
data class BattleFinisherSnapshot(
    val sequenceId: Long,
    val kind: BattleFinisherKind,
    val leadId: String,
    val partnerId: String? = null,
    val targetId: String? = null,
    val resultSpecies: String? = null,
    val specialName: String? = null,
    val elapsedMillis: Long,
    val durationMillis: Long,
    val phase: BattleFinisherPhase,
    val phaseProgress: Float,
    val impactCommitted: Boolean,
    /** Actual committed damage, available independently of the bounded event tail. */
    val damage: Int = 0,
) {
    val participantIds: List<String>
        get() = if (kind == BattleFinisherKind.JOGRESS || kind == BattleFinisherKind.DUO)
            listOfNotNull(leadId, partnerId).distinct() else listOf(leadId)

    /** RESTORE owns the transition back to the original actors in presentation. */
    val resultVisible: Boolean
        get() = phase in BattleFinisherPhase.REVEAL..BattleFinisherPhase.AFTERMATH
}

/** Authored beats, shared by simulation and rendering; never scaled to technique startup. */
object BattleFinisherTimeline {
    fun phaseDurationMillis(kind: BattleFinisherKind, phase: BattleFinisherPhase): Long = when (phase) {
        BattleFinisherPhase.FOCUS -> 250L
        BattleFinisherPhase.TRANSFORM -> when (kind) {
            BattleFinisherKind.FORM -> 1_100L
            BattleFinisherKind.JOGRESS -> 1_600L
            BattleFinisherKind.POWER -> 450L
            BattleFinisherKind.DUO -> 650L
        }
        BattleFinisherPhase.REVEAL -> when (kind) {
            BattleFinisherKind.POWER, BattleFinisherKind.DUO -> 300L
            BattleFinisherKind.FORM, BattleFinisherKind.JOGRESS -> 800L
        }
        BattleFinisherPhase.CHARGE -> 1_200L
        BattleFinisherPhase.RELEASE -> 600L
        BattleFinisherPhase.IMPACT -> 800L
        BattleFinisherPhase.AFTERMATH -> 1_100L
        BattleFinisherPhase.RESTORE -> 650L
    }

    fun durationMillis(kind: BattleFinisherKind): Long =
        BattleFinisherPhase.entries.sumOf { phaseDurationMillis(kind, it) }

    fun phaseStartMillis(kind: BattleFinisherKind, phase: BattleFinisherPhase): Long =
        BattleFinisherPhase.entries.take(phase.ordinal).sumOf { phaseDurationMillis(kind, it) }

    fun snapshot(
        sequenceId: Long,
        kind: BattleFinisherKind,
        leadId: String,
        partnerId: String? = null,
        targetId: String? = null,
        resultSpecies: String? = null,
        specialName: String? = null,
        elapsedMillis: Long = 0L,
        impactCommitted: Boolean = false
    ): BattleFinisherSnapshot = sample(BattleFinisherSnapshot(
        sequenceId, kind, leadId, partnerId, targetId, resultSpecies, specialName,
        0L, durationMillis(kind), BattleFinisherPhase.FOCUS, 0f, impactCommitted
    ), elapsedMillis)

    /** Sampling never commits damage; [BattleFinisherSnapshot.impactCommitted] is simulator-owned. */
    fun sample(snapshot: BattleFinisherSnapshot, elapsedMillis: Long = snapshot.elapsedMillis): BattleFinisherSnapshot {
        val duration = durationMillis(snapshot.kind)
        val elapsed = elapsedMillis.coerceIn(0L, duration)
        var phaseElapsed = elapsed
        for (phase in BattleFinisherPhase.entries) {
            val beatDuration = phaseDurationMillis(snapshot.kind, phase)
            if (phaseElapsed < beatDuration || phase == BattleFinisherPhase.RESTORE) {
                return snapshot.copy(elapsedMillis = elapsed, durationMillis = duration, phase = phase,
                    phaseProgress = (phaseElapsed.toFloat() / beatDuration).coerceIn(0f, 1f))
            }
            phaseElapsed -= beatDuration
        }
        error("The finisher timeline must contain RESTORE")
    }
}
