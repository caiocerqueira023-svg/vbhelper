package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionOrigin
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionRole
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionSide
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionParticipant

object DigimonScanPolicy {
    const val CONVERSION_PERCENTAGE = 100
    const val NORMAL_GAIN = 20

    fun perDefeat(debugBuild: Boolean, instantScan: Boolean): Int =
        if (debugBuild && instantScan) CONVERSION_PERCENTAGE else NORMAL_GAIN

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
