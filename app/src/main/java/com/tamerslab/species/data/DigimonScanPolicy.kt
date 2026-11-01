package com.tamerslab.species.data

import com.tamerslab.battle.offline.core.BattleOutcome
import com.tamerslab.world.ecosystem.InteractionOrigin
import com.tamerslab.world.ecosystem.InteractionRole
import com.tamerslab.world.ecosystem.InteractionSide
import com.tamerslab.world.ecosystem.WorldInteractionParticipant

object DigimonScanPolicy {
    const val CONVERSION_PERCENTAGE = 100
    const val NORMAL_GAIN = 20

    fun perDefeat(debugEnabled: Boolean, instantScan: Boolean): Int =
        if (debugEnabled && instantScan) CONVERSION_PERCENTAGE else NORMAL_GAIN

    fun awards(origin: InteractionOrigin, outcome: BattleOutcome, friendly: Boolean,
               participants: List<WorldInteractionParticipant>, percentagePerDefeat: Int = NORMAL_GAIN): Map<Long, Int> {
        if (origin == InteractionOrigin.AUTONOMOUS || outcome != BattleOutcome.ALLIED_VICTORY || friendly ||
            participants.none { it.role == InteractionRole.OWNED && it.side == InteractionSide.ALLIED }) return emptyMap()
        require(percentagePerDefeat in 1..CONVERSION_PERCENTAGE)
        return participants.asSequence()
            .filter { it.role == InteractionRole.WILD && it.side == InteractionSide.OPPOSING && it.cardCharacterId != null }
            .distinctBy { it.individualId }
            .groupingBy { requireNotNull(it.cardCharacterId) }.eachCount()
            .mapValues { (_, count) -> (count.toLong() * percentagePerDefeat).coerceAtMost(CONVERSION_PERCENTAGE.toLong()).toInt() }
    }

    fun afterReward(before: Int, gain: Int): Int {
        require(before in 0..CONVERSION_PERCENTAGE && gain >= 0)
        return (before.toLong() + gain).coerceAtMost(CONVERSION_PERCENTAGE.toLong()).toInt()
    }
}
