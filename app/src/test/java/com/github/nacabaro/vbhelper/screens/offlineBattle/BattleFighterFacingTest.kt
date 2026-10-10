package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.*
import org.junit.Assert.*
import org.junit.Test

class BattleFighterFacingTest {
    @Test fun retreatFacesTravelInsteadOfBeingVisuallyTetheredToTheOpponent() {
        val facing = BattleFighterFacing()
        facing.direction(BattlePosition(0f, 0f), BattlePosition(3f, 0f), CombatantState.MOVE_AWAY, 0)
        val direction = facing.direction(BattlePosition(-0.1f, 0f), BattlePosition(3f, 0f), CombatantState.MOVE_AWAY, 34)
        assertTrue(direction.x < 0f)
        val sameFrame = facing.direction(BattlePosition(-0.1f, 0f), BattlePosition(3f, 0f), CombatantState.MOVE_AWAY, 34)
        assertEquals(direction, sameFrame)
        val windup = facing.direction(BattlePosition(-0.1f, 0f), BattlePosition(3f, 0f), CombatantState.ATTACK_STARTUP, 68)
        assertTrue(windup.x > 0f)
    }

    @Test fun lateralPositioningUsesItsPathWhileKnockbackKeepsCombatFacing() {
        val facing = BattleFighterFacing()
        facing.direction(BattlePosition(0f, 0f), BattlePosition(3f, 0f), CombatantState.POSITIONING, 0)
        assertTrue(facing.direction(BattlePosition(0f, 0.1f), BattlePosition(3f, 0f), CombatantState.POSITIONING, 34).z > 0f)
        assertTrue(facing.direction(BattlePosition(-0.1f, 0.1f), BattlePosition(3f, 0f), CombatantState.KNOCKBACK, 68).x > 0f)
    }
}
