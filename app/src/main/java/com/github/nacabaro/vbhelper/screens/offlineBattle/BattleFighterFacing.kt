package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattlePosition
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantState

/** Retains travel heading between render frames so fixed-step snapshots cannot cause facing flicker. */
internal class BattleFighterFacing {
    private var previousPosition: BattlePosition? = null
    private var travel = BattlePosition(1f, 0f)
    private var lastTravelAt = Long.MIN_VALUE

    fun direction(position: BattlePosition, target: BattlePosition?, state: CombatantState, elapsedMillis: Long): BattlePosition {
        val previous = previousPosition
        val moving = state == CombatantState.MOVE_TO_TARGET || state == CombatantState.MOVE_AWAY || state == CombatantState.POSITIONING
        if (moving && previous != null && position.distanceTo(previous) > 0.0001f) {
            travel = BattlePosition(position.x - previous.x, position.z - previous.z)
            lastTravelAt = elapsedMillis
        }
        previousPosition = position
        if (moving && lastTravelAt != Long.MIN_VALUE && elapsedMillis - lastTravelAt in 0L..250L) return travel
        return target?.let { BattlePosition(it.x - position.x, it.z - position.z) } ?: travel
    }
}
