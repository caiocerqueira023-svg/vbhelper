package com.github.nacabaro.vbhelper.rendering

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSceneProfileTest {
    @Test
    fun profilesAvoidCostlyInvisiblePassesAndKeepPixelArtCrisp() {
        val battle = hybridSceneProfile(HybridSceneKind.BATTLE)
        val farm = hybridSceneProfile(HybridSceneKind.DIGIFARM)

        assertFalse(battle.ambientOcclusionEnabled)
        assertFalse(farm.ambientOcclusionEnabled)
        assertFalse(battle.temporalAntiAliasingEnabled)
        assertFalse(farm.temporalAntiAliasingEnabled)
        assertTrue(battle.bloomStrength < farm.bloomStrength)
        assertTrue(farm.bloomStrength >= 0.45f)
    }
}
