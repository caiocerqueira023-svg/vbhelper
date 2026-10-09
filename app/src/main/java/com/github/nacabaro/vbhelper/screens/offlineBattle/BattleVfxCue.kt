package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattleEvent
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot

internal data class BattleMissCue(
    val eventId: Long,
    val anchorCombatantId: String,
    val isSpecial: Boolean = false
)

/** Finds the newest failed attack and the fighter position where its visual should resolve. */
internal fun latestBattleMissCue(snapshot: BattleSnapshot): BattleMissCue? {
    val events = snapshot.recentEvents
    val missIndex = events.indexOfLast {
        it is BattleEvent.TechniqueHit || it is BattleEvent.TechniqueMissed ||
            it is BattleEvent.ProjectileMissed || it is BattleEvent.SpecialResolved
    }
    if (missIndex < 0) return null
    val eventId = snapshot.eventCount - (events.lastIndex - missIndex)
    if (eventId <= snapshot.lastFinisherEventCount) return null
    return when (val miss = events[missIndex]) {
        is BattleEvent.TechniqueMissed -> {
            val start = events.subList(0, missIndex).asReversed()
                .filterIsInstance<BattleEvent.TechniqueStarted>()
                .firstOrNull { it.combatantId == miss.combatantId && it.techniqueId == miss.techniqueId }
            BattleMissCue(eventId, start?.targetId ?: miss.combatantId)
        }
        is BattleEvent.ProjectileMissed -> {
            val launch = events.subList(0, missIndex).asReversed()
                .filterIsInstance<BattleEvent.ProjectileLaunched>()
                .firstOrNull { it.projectileId == miss.projectileId }
            val start = launch?.let { launched ->
                events.subList(0, missIndex).asReversed()
                    .filterIsInstance<BattleEvent.TechniqueStarted>()
                    .firstOrNull {
                        it.combatantId == launched.ownerId && it.techniqueId == launched.techniqueId
                    }
            }
            BattleMissCue(eventId, start?.targetId ?: launch?.ownerId ?: return null)
        }
        is BattleEvent.SpecialResolved -> if (miss.success) null else {
            BattleMissCue(eventId, miss.targetId ?: miss.combatantId, isSpecial = true)
        }
        is BattleEvent.TechniqueHit -> null
        else -> null
    }
}
