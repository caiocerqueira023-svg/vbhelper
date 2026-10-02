package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.world.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class WorldInteractionPolicyTest {
    @Test fun `terminal events cannot restart or award a second outcome`() {
        listOf(InteractionState.ENDED, InteractionState.CANCELLED, InteractionState.INTERRUPTED).forEach { state ->
            assertTrue(state.terminal)
            InteractionState.entries.forEach { next -> assertFalse(WorldInteractionPolicy.canTransition(state, next)) }
        }
    }

    @Test fun `reservation is a distinct ownership step rather than a joinability phase`() {
        assertTrue(WorldInteractionPolicy.canTransition(InteractionState.ACTIVE, InteractionState.RESERVED))
        assertTrue(WorldInteractionPolicy.canTransition(InteractionState.RESERVED, InteractionState.PLAYER_CONTROLLED))
        assertTrue(WorldInteractionPolicy.canTransition(InteractionState.RESERVED, InteractionState.ACTIVE))
        assertFalse(WorldInteractionPolicy.canTransition(InteractionState.PROPOSED, InteractionState.PLAYER_CONTROLLED))
        assertFalse(WorldInteractionPolicy.canTransition(InteractionState.PLAYER_CONTROLLED, InteractionState.ACTIVE))
    }

    @Test fun `NPC outcomes never award owned battle records or remove encounters`() {
        BattleOutcome.entries.forEach { outcome ->
            val effects = WorldInteractionPolicy.battleEffects(InteractionOrigin.AUTONOMOUS, outcome)
            assertNull(effects.ownedWon)
            assertFalse(effects.removeOpposingWilds)
        }
    }

    @Test fun `joined result effects preserve allied wilds and only record decisive owned results`() {
        assertEquals(InteractionBattleEffects(true, true), WorldInteractionPolicy.battleEffects(InteractionOrigin.DIRECT_PLAYER, BattleOutcome.ALLIED_VICTORY))
        assertEquals(InteractionBattleEffects(false, false), WorldInteractionPolicy.battleEffects(InteractionOrigin.DIRECT_PLAYER, BattleOutcome.OPPOSING_VICTORY))
        listOf(BattleOutcome.DRAW, BattleOutcome.ABANDONED).forEach {
            assertEquals(InteractionBattleEffects(null, false), WorldInteractionPolicy.battleEffects(InteractionOrigin.DIRECT_PLAYER, it))
        }
    }

    @Test fun `physical battle fix freshness is independent of projection and clock rollback`() {
        val fix = WorldPlayerFix(GeoPoint(0.0, 0.0), capturedAt = 100_000L)
        assertTrue(fix.isFresh(130_000L))
        assertFalse(fix.isFresh(130_001L))
        assertFalse(fix.isFresh(99_999L))
    }
}
