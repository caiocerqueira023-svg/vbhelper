package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattleImpactSnapshot
import org.junit.Assert.*
import org.junit.Test

class BattleImpactVisualTest {
    private fun hit(id: Long = 1, target: String = "rear", remaining: Long = 700) =
        BattleImpactSnapshot(id, target, 10, false, remaining)

    @Test fun impactFlashEndsQuicklyWhileTheDamageIndicatorCanRemainReadable() {
        assertEquals(listOf(hit()), visibleBattleImpactSprites(listOf(hit())))
        assertEquals(listOf(hit(remaining = 521)), visibleBattleImpactSprites(listOf(hit(remaining = 521))))
        assertTrue(visibleBattleImpactSprites(listOf(hit(remaining = 520))).isEmpty())
        assertTrue(visibleBattleImpactSprites(listOf(hit(remaining = 1))).isEmpty())
    }

    @Test fun multiHitAttacksProduceOneFlashPerVictimWithoutStacking() {
        val latest = hit(3)
        val other = hit(4, "front")
        assertEquals(listOf(latest, other), visibleBattleImpactSprites(listOf(hit(1), hit(2), latest, other)))
    }

    @Test fun pausedSimulationDoesNotLeaveTheHitFlashStuckOnScreen() {
        assertEquals(1f, battleImpactFlashOpacity(700, 0), 0f)
        assertTrue(battleImpactFlashOpacity(700, 140) in 0f..0.5f)
        assertEquals(0f, battleImpactFlashOpacity(700, 180), 0f)
        assertEquals(0f, battleImpactFlashOpacity(700, 5_000), 0f)
    }

    @Test fun impactPlacementStaysNearItsVictimAtDifferentCameraAngles() {
        for (yaw in listOf(0.0, Math.PI / 2, Math.PI, -Math.PI / 2)) {
            val placement = battleImpactPlacement(2f, -3f, fighterScale = 1.65f, cameraYaw = yaw, cameraPitch = 0.3)
            val distance = kotlin.math.hypot(placement.x - 2f, placement.z + 3f)
            assertTrue("The effect must stay at the victim's depth", distance in 0f..0.18f)
            assertTrue("A hit burst should cover its victim, not a quarter of its body", placement.height >= 1.65f)
            assertTrue(placement.y in 0.7f..1.1f)
        }
    }

    @Test fun hitExpansionHasMoreWeightForSpecialsAndCriticalsButAlwaysEnds() {
        assertTrue(battleImpactSizeMultiplier(700, 60, false, false) > 1f)
        assertTrue(battleImpactSizeMultiplier(700, 60, true, false) > battleImpactSizeMultiplier(700, 60, false, false))
        assertTrue(battleImpactSizeMultiplier(700, 60, false, true) > battleImpactSizeMultiplier(700, 60, false, false))
        assertEquals(0f, battleImpactSizeMultiplier(700, 5000, true, true), 0f)
    }

    @Test fun foregroundAnchorClearsEveryCornerOfTurnedAndMirroredModels() {
        val scale = 1.65f
        for (turn in listOf(0.0, 0.7, -0.7)) for (yaw in listOf(0.0, 1.2, 3.1)) for (pitch in listOf(0.2, 0.7)) {
            val c = kotlin.math.cos(turn).toFloat()
            val s = kotlin.math.sin(turn).toFloat()
            val matrix = floatArrayOf(-scale*c, 0f, scale*s, 0f, 0f, scale, 0f, 0f,
                scale*s, 0f, scale*c, 0f, 2f, 0.025f, -3f, 1f)
            val anchor = battleFrontEffectAnchor(matrix, floatArrayOf(0f, 0.5f, 0f),
                floatArrayOf(0.8f, 0.5f, 0.13f), scale, yaw, pitch)
            val nx = kotlin.math.sin(yaw) * kotlin.math.cos(pitch)
            val ny = kotlin.math.sin(pitch)
            val nz = kotlin.math.cos(yaw) * kotlin.math.cos(pitch)
            for (x in listOf(-0.8f, 0.8f)) for (y in listOf(0f, 1f)) for (z in listOf(-0.13f, 0.13f)) {
                val wx = matrix[0]*x + matrix[4]*y + matrix[8]*z + matrix[12]
                val wy = matrix[1]*x + matrix[5]*y + matrix[9]*z + matrix[13]
                val wz = matrix[2]*x + matrix[6]*y + matrix[10]*z + matrix[14]
                assertTrue("The hit plane intersects the fighter at yaw=$yaw pitch=$pitch turn=$turn",
                    (anchor.x-wx)*nx + (anchor.y-wy)*ny + (anchor.z-wz)*nz > 0.02)
            }
        }
    }

    @Test fun largeForegroundImpactsStayAboveTheFloorWithoutCrossingBackIntoTheBody() {
        val anchor = BattleEffectAnchor(2f, 0.8f, -2.7f)
        val placement = battleImpactPlacementFromAnchor(anchor, 1.65f, 0.0, 0.3, 2f)
        assertTrue(placement.y - placement.height * 0.5f * kotlin.math.cos(0.3) >= 0.04f)
        val cameraDepthChange = (placement.y-anchor.y)*kotlin.math.sin(0.3) +
            (placement.z-anchor.z)*kotlin.math.cos(0.3)
        assertEquals(0.0, cameraDepthChange, 0.0001)
    }
}
