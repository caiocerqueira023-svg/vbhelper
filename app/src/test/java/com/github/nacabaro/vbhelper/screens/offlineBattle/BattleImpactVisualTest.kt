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
            assertTrue(placement.height < 1.0f)
            assertTrue(placement.y in 0.7f..1.1f)
        }
    }
}
