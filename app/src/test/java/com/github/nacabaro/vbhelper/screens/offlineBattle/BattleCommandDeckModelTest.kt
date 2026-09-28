package com.github.nacabaro.vbhelper.screens.offlineBattle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCommandDeckModelTest {
    @Test
    fun commandDeckAlwaysKeepsTheDecodeInspiredThreeByThreeOrder() {
        val deck = battleCommandDeck(
            BattleCommandAvailability(
                partnerReady = false,
                targetReady = false,
                supportReady = false,
                specialReady = false
            )
        )

        assertEquals(
            listOf(
                BattleCommandSlot.TECHNIQUES,
                BattleCommandSlot.ITEMS,
                BattleCommandSlot.DEFEND,
                BattleCommandSlot.SUPPORT,
                BattleCommandSlot.SPECIAL,
                BattleCommandSlot.FOCUS,
                BattleCommandSlot.STRATEGY,
                BattleCommandSlot.MOVE_CLOSER,
                BattleCommandSlot.KEEP_DISTANCE
            ),
            deck.map { it.slot }
        )
        assertEquals(9, deck.size)
        assertTrue(deck.none { it.enabled })
    }

    @Test
    fun commandAvailabilityDoesNotRemoveOrReorderDisabledActions() {
        val deck = battleCommandDeck(
            BattleCommandAvailability(
                partnerReady = true,
                targetReady = false,
                supportReady = true,
                specialReady = false
            )
        ).associateBy { it.slot }

        assertTrue(deck.getValue(BattleCommandSlot.TECHNIQUES).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.ITEMS).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.DEFEND).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.SUPPORT).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.STRATEGY).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.MOVE_CLOSER).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.KEEP_DISTANCE).enabled)
        assertFalse(deck.getValue(BattleCommandSlot.SPECIAL).enabled)
        assertFalse(deck.getValue(BattleCommandSlot.FOCUS).enabled)
    }

    @Test
    fun targetDependentActionsBecomeAvailableOnlyWithALiveTarget() {
        val deck = battleCommandDeck(
            BattleCommandAvailability(
                partnerReady = true,
                targetReady = true,
                supportReady = false,
                specialReady = true
            )
        ).associateBy { it.slot }

        assertTrue(deck.getValue(BattleCommandSlot.FOCUS).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.SPECIAL).enabled)
        assertFalse(deck.getValue(BattleCommandSlot.SUPPORT).enabled)
    }
}
