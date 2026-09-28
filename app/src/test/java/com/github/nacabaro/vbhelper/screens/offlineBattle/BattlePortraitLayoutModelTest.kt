package com.github.nacabaro.vbhelper.screens.offlineBattle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePortraitLayoutModelTest {
    @Test
    fun normalPhoneHeightIsSharedEvenlyByArenaAndCommandDeck() {
        val panels = calculateBattlePortraitPanels(availableHeightDp = 640f)

        assertEquals(315f, panels.arenaHeightDp, 0.01f)
        assertEquals(315f, panels.deckHeightDp, 0.01f)
        assertEquals(10f, panels.gapHeightDp, 0.01f)
        assertEquals(640f, panels.arenaHeightDp + panels.gapHeightDp + panels.deckHeightDp, 0.01f)
    }

    @Test
    fun compactPhoneProtectsTheNineCommandDeckWithoutLeavingUnusedHeight() {
        val panels = calculateBattlePortraitPanels(availableHeightDp = 430f)

        assertTrue(panels.deckHeightDp >= 248f)
        assertTrue(panels.gapHeightDp >= 8f)
        assertEquals(430f, panels.arenaHeightDp + panels.gapHeightDp + panels.deckHeightDp, 0.01f)
    }

    @Test
    fun veryCompactPhoneKeepsAllCommandsVisibleBeforeProtectingArenaHeight() {
        val panels = calculateBattlePortraitPanels(availableHeightDp = 400f)

        assertEquals(248f, panels.deckHeightDp, 0.01f)
        assertEquals(142f, panels.arenaHeightDp, 0.01f)
        assertEquals(10f, panels.gapHeightDp, 0.01f)
    }
}
