package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.*
import org.junit.Assert.*
import org.junit.Test

/** Paired seeds measure mechanical side bias independently of new encounter/loadout difficulty. */
class Dw1BattleBalanceMatrixTest {
    @Test fun mirroredFightersRemainSideNeutralAcrossStagesAndFinishWithinTheDurationBudget() {
        for (stage in 0..5) {
            val durations = mutableListOf<Long>()
            var firstWins = 0
            var secondWins = 0
            var draws = 0
            var timeouts = 0
            var firstUnswappedWins = 0
            var firstSwappedWins = 0
            for (seed in 1L..1_000L) {
                for (swapped in listOf(false, true)) {
                    fun input(id: String) = TrainingParticipantInput(id, displayName = id, stage = stage,
                        maxHealth = 1, attack = 1, stableRngKey = id)
                    val sim = TrainingBattleFactory.create(listOf(input(if (swapped) "second" else "first")),
                        listOf(input(if (swapped) "first" else "second")),
                        BattleConfiguration(randomSeed = seed, maxDurationMillis = 120_000))
                    var state = sim.snapshot()
                    while (state.result == null) {
                        sim.advance(250)
                        state = sim.snapshot()
                    }
                    val result = state.result
                    durations += result.elapsedMillis
                    val firstWon = result.outcome == if (swapped) BattleOutcome.OPPOSING_VICTORY else BattleOutcome.ALLIED_VICTORY
                    when {
                        result.elapsedMillis >= 120_000 -> timeouts++
                        result.outcome == BattleOutcome.DRAW -> draws++
                        firstWon -> {
                            firstWins++
                            if (swapped) firstSwappedWins++ else firstUnswappedWins++
                        }
                        else -> secondWins++
                    }
                    (state.alliedMembers + state.opposingMembers).forEach { fighter ->
                        assertTrue("Unbounded incapacitation", fighter.debug.incapacitatedMillis < result.elapsedMillis * 0.30)
                    }
                }
            }
            durations.sort()
            val rate = firstWins.toFloat() / (firstWins + secondWins).coerceAtLeast(1)
            val sideDifference = kotlin.math.abs(firstUnswappedWins - firstSwappedWins) / 1_000f
            println("DW1_MATRIX stage=$stage first=$firstWins second=$secondWins draws=$draws timeouts=$timeouts " +
                "rate=$rate sideDifference=$sideDifference p50=${durations[999]} p95=${durations[1899]}")
            assertTrue("Mirror win rate at stage $stage: $rate", rate in 0.47f..0.53f)
            assertTrue("Side bias at stage $stage: $sideDifference", sideDifference <= 0.03f)
            assertEquals("Mirror timeouts at stage $stage", 0, timeouts)
            assertTrue("Mirror p95 at stage $stage", durations[1899] < 120_000)
        }
    }
}
