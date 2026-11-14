package com.tamerslab.battle.ui.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the lower battle command surface: a fixed 3×3 deck.
 *
 * The first row holds the partner's three equipped techniques, the second row
 * Items / Special / Strategy, and the third row Focus / Defend / Move. Slots are
 * never removed or reordered, so unavailable actions stay in place (inert) and
 * muscle memory survives every combat state.
 */
class BattleCommandDeckModelTest {
    private fun availability(
        partnerReady: Boolean = false,
        targetReady: Boolean = false,
        techniqueReady: List<Boolean> = listOf(false, false, false),
        specialReady: Boolean = false,
        moveArmed: Boolean = false
    ) = BattleCommandAvailability(
        partnerReady = partnerReady,
        targetReady = targetReady,
        techniqueReady = techniqueReady,
        specialReady = specialReady,
        moveArmed = moveArmed
    )

    @Test
    fun keepsTheDecodeInspiredThreeByThreeCommandOrder() {
        val deck = battleCommandDeck(availability())

        assertEquals(
            listOf(
                BattleCommandSlot.TECHNIQUE_1,
                BattleCommandSlot.TECHNIQUE_2,
                BattleCommandSlot.TECHNIQUE_3,
                BattleCommandSlot.ITEMS,
                BattleCommandSlot.SPECIAL,
                BattleCommandSlot.STRATEGY,
                BattleCommandSlot.FOCUS,
                BattleCommandSlot.DEFEND,
                BattleCommandSlot.MOVE
            ),
            deck.map { it.slot }
        )
        assertEquals("The deck always exposes nine positions", 9, deck.size)
        assertTrue("Nothing is available before the partner is ready", deck.none { it.enabled })
    }

    @Test
    fun enablesTechniqueSlotsIndependentlyFromTheSharedPartnerGate() {
        val deck = battleCommandDeck(
            availability(partnerReady = true, techniqueReady = listOf(true, false, true))
        ).associateBy { it.slot }

        assertTrue(deck.getValue(BattleCommandSlot.TECHNIQUE_1).enabled)
        assertFalse(deck.getValue(BattleCommandSlot.TECHNIQUE_2).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.TECHNIQUE_3).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.ITEMS).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.DEFEND).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.MOVE).enabled)
    }

    @Test
    fun aShortTechniqueListKeepsTheThirdSlotInertInsteadOfRemovingIt() {
        val deck = battleCommandDeck(
            availability(partnerReady = true, techniqueReady = listOf(true))
        )

        assertEquals(9, deck.size)
        val bySlot = deck.associateBy { it.slot }
        assertTrue(bySlot.getValue(BattleCommandSlot.TECHNIQUE_1).enabled)
        assertFalse("Missing second technique stays inert", bySlot.getValue(BattleCommandSlot.TECHNIQUE_2).enabled)
        assertFalse("Missing third technique stays inert", bySlot.getValue(BattleCommandSlot.TECHNIQUE_3).enabled)
    }

    @Test
    fun anEmptyTechniqueListNeverDropsTechniquePositions() {
        val deck = battleCommandDeck(
            availability(partnerReady = true, techniqueReady = emptyList())
        )

        assertEquals(9, deck.size)
        listOf(
            BattleCommandSlot.TECHNIQUE_1,
            BattleCommandSlot.TECHNIQUE_2,
            BattleCommandSlot.TECHNIQUE_3
        ).forEach { slot ->
            assertFalse("$slot must stay present but inert", deck.getValue(slot).enabled)
        }
    }

    @Test
    fun targetAndSpecialReadinessGateOnlyTheirOwnKeys() {
        val deck = battleCommandDeck(
            availability(
                partnerReady = true,
                targetReady = false,
                techniqueReady = listOf(true, true, true),
                specialReady = true
            )
        ).associateBy { it.slot }

        assertTrue(deck.getValue(BattleCommandSlot.SPECIAL).enabled)
        assertFalse("Focus also needs a live target", deck.getValue(BattleCommandSlot.FOCUS).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.STRATEGY).enabled)
        assertTrue(deck.getValue(BattleCommandSlot.ITEMS).enabled)
    }

    @Test
    fun moveArmedHighlightsWithoutRemovingOrReorderingAnySlot() {
        val plain = battleCommandDeck(availability(partnerReady = true))
        val armed = battleCommandDeck(
            availability(partnerReady = true, moveArmed = true)
        )

        assertEquals(plain.map { it.slot }, armed.map { it.slot })
        assertEquals(plain.map { it.enabled }, armed.map { it.enabled })
        assertEquals(9, armed.size)
    }
}

private fun List<BattleCommandDeckItem>.getValue(slot: BattleCommandSlot): BattleCommandDeckItem =
    first { it.slot == slot }
