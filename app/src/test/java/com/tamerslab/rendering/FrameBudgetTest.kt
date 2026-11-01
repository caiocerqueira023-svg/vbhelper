package com.tamerslab.rendering

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameBudgetTest {
    @Test fun thermalStatusReducesQualityConservatively() {
        assertEquals(SceneQuality.HIGH, SceneFrameBudget.qualityForThermalStatus(0))
        assertEquals(SceneQuality.MEDIUM, SceneFrameBudget.qualityForThermalStatus(2))
        assertEquals(SceneQuality.LOW, SceneFrameBudget.qualityForThermalStatus(3))
        assertEquals(SceneQuality.LOW, SceneFrameBudget.qualityForThermalStatus(4))
    }

    @Test fun qualityTierCapsActiveFrameRate() {
        val active = SceneFrameInputs(interacting = true)
        assertEquals(SceneFrameTier.Active, SceneFrameBudget.tier(active, SceneQuality.HIGH))
        assertEquals(SceneFrameTier.Reduced, SceneFrameBudget.tier(active, SceneQuality.MEDIUM))
        assertEquals(SceneFrameTier.Idle, SceneFrameBudget.tier(active, SceneQuality.LOW))
        assertEquals(SceneFrameTier.Paused, SceneFrameBudget.tier(active.copy(worldActive = false), SceneQuality.LOW))
    }
}
