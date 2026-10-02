package com.github.nacabaro.vbhelper.screens.offlineBattle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFighterScaleTest {
    @Test
    fun babyStagesRemainVisiblySmallerThanChildAndThanEachOther() {
        val babyI = battleFighterScaleMultiplier(stage = 0)
        val babyII = battleFighterScaleMultiplier(stage = 1)
        val child = battleFighterScaleMultiplier(stage = 2)

        assertEquals(0.58f, babyI, 0.0001f)
        assertEquals(0.72f, babyII, 0.0001f)
        assertEquals(1f, child, 0.0001f)
        assertTrue(babyI < babyII)
        assertTrue(babyII < child)
    }

    @Test
    fun laterAndUnknownStagesKeepTheArenaBaselineScale() {
        assertEquals(1f, battleFighterScaleMultiplier(stage = 3), 0.0001f)
        assertEquals(1f, battleFighterScaleMultiplier(stage = 6), 0.0001f)
        assertEquals(1f, battleFighterScaleMultiplier(stage = -1), 0.0001f)
    }
}
