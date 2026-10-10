package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import org.junit.Assert.*
import org.junit.Test

class BattleAttackMotionTest {
    @Test fun leftwardSpriteFlipsHorizontallyInsteadOfRollingUpsideDown() {
        val right = screenAttackFacing(10f, 2f)
        val left = screenAttackFacing(-10f, 2f)
        assertEquals(1f, right.scaleX, 0f)
        assertEquals(-1f, left.scaleX, 0f)
        assertTrue(kotlin.math.abs(right.rotationDegrees) < 20f)
        assertTrue(kotlin.math.abs(left.rotationDegrees) < 20f)
    }

    @Test fun facingIsDerivedFreshFromTheTravelVectorOnEveryUse() {
        val original = worldAttackFacing(5f, 0f, 0.0, 0.25)
        worldAttackFacing(5f, 0f, Math.PI, 0.25)
        assertEquals(original, worldAttackFacing(5f, 0f, 0.0, 0.25))
        val opposite = worldAttackFacing(-5f, 0f, 0.0, 0.25)
        assertEquals(-original.scaleX, opposite.scaleX, 0f)
    }

    @Test fun leftAuthoredLargeArtIsCorrectedExactlyOnceForItsCurrentTravelDirection() {
        assertEquals(-1f, screenAttackFacing(5f, 0f, authoredFacingLeft = true).scaleX, 0f)
        assertEquals(1f, screenAttackFacing(-5f, 0f, authoredFacingLeft = true).scaleX, 0f)
        assertEquals(0f, screenAttackFacing(-5f, 0f, authoredFacingLeft = true).rotationDegrees, 0f)
    }

    @Test fun standardSmallAttackArtUsesItsLeftAuthoredFacingInBothDirections() {
        val right = screenAttackFacing(5f, 0f, authoredFacingLeft = BATTLE_ATTACK_ART_FACES_LEFT)
        val left = screenAttackFacing(-5f, 0f, authoredFacingLeft = BATTLE_ATTACK_ART_FACES_LEFT)
        assertEquals(-1f, right.scaleX, 0f)
        assertEquals(1f, left.scaleX, 0f)
        assertEquals(0f, right.rotationDegrees, 0f)
        assertEquals(0f, left.rotationDegrees, 0f)
        val world = worldAttackFacing(5f, 0f, 0.0, 0.25, BATTLE_ATTACK_ART_FACES_LEFT)
        assertEquals(right.scaleX, world.scaleX, 0f)
        assertEquals(right.rotationDegrees, world.rotationDegrees, 0.0001f)
    }

    @Test fun trackingFrameIncludesTheVictimsWholeBodyNotJustItsCenter() {
        val saved = CinematicCameraPose(0.0, 0.0, 0.7, 0.0, 0.27, 19.0)
        val base = BattleFinisherTimeline.snapshot(1, BattleFinisherKind.POWER, "a")
        for (aspect in listOf(0.6, 1.0, 1.7)) for (progress in listOf(0.3f, 0.6f, 0.9f)) {
            val shot = cinematicCameraPose(base.copy(phase = BattleFinisherPhase.RELEASE, phaseProgress = progress),
                BattlePosition(-3f, 0f), null, BattlePosition(3f, 0f), 1.65, aspect, saved, attackSide = 1)
            val halfFrame = shot.distance * (24.0 / 56.0) * aspect
            assertTrue(kotlin.math.abs(3.0 - shot.targetX) + 1.65 * 0.8 <= halfFrame * 0.85)
        }
    }

    @Test fun launchAcceleratesIntoContactInsteadOfDeceleratingAtTheVictim() {
        assertEquals(0f, cinematicLaunchProgress(0f), 0f)
        assertEquals(1f, cinematicLaunchProgress(1f), 0f)
        val earlyDistance = cinematicLaunchProgress(0.4f) - cinematicLaunchProgress(0.3f)
        val lateDistance = cinematicLaunchProgress(0.9f) - cinematicLaunchProgress(0.8f)
        assertTrue(lateDistance > earlyDistance * 2)
        assertTrue(cinematicLaunchProgress(0.4f, 1) < cinematicLaunchProgress(0.4f))
        assertTrue(BattleFinisherTimeline.phaseDurationMillis(BattleFinisherKind.FORM, BattleFinisherPhase.RELEASE) <= 650L)
    }

    @Test fun cameraMovesWithTheProjectileAndIsContinuousIntoTheHitStop() {
        val saved = CinematicCameraPose(0.0, 0.0, 0.7, 0.0, 0.27, 19.0)
        val from = BattlePosition(-3f, 0f)
        val to = BattlePosition(3f, 0f)
        val base = BattleFinisherTimeline.snapshot(1, BattleFinisherKind.POWER, "a", targetId = "b")
        fun shot(phase: BattleFinisherPhase, progress: Float) = cinematicCameraPose(
            base.copy(phase = phase, phaseProgress = progress), from, null, to, 1.65, 1.0, saved,
            attackSide = 1)
        val early = shot(BattleFinisherPhase.RELEASE, 0.3f)
        val late = shot(BattleFinisherPhase.RELEASE, 0.95f)
        assertTrue(late.targetX > early.targetX + 1.0)
        assertEquals(shot(BattleFinisherPhase.RELEASE, 1f), shot(BattleFinisherPhase.IMPACT, 0f))
    }

    @Test fun repeatedFinishersKeepTheirEstablishedScreenSideEvenAfterTheBattleCameraOrbits() {
        val movie = BattleFinisherTimeline.snapshot(1, BattleFinisherKind.POWER, "a")
            .copy(phase = BattleFinisherPhase.CHARGE)
        fun shot(yaw: Double) = cinematicCameraPose(movie, BattlePosition(-3f, 0f), null,
            BattlePosition(3f, 0f), 1.65, 1.0, CinematicCameraPose(0.0, 0.0, 0.7, yaw, 0.27, 19.0), attackSide = 1)
        assertEquals(shot(0.0), shot(Math.PI))
    }

    @Test fun transparentPaddingDoesNotShrinkTheVisibleImpactAndSourcePixelsStayIntact() {
        val pixels = IntArray(100)
        pixels[44] = 0xFFFFFFFF.toInt()
        pixels[45] = 0xFFFF0000.toInt()
        pixels[54] = 0xFF00FF00.toInt()
        val original = pixels.copyOf()
        val cropped = cropBattleImpactFrame(ResidentFrameImage(pixels, 10, 10))
        assertEquals(4, cropped.width)
        assertEquals(4, cropped.height)
        assertArrayEquals(original, pixels)
        assertEquals(3, cropped.argb.count { it ushr 24 != 0 })
    }
}
