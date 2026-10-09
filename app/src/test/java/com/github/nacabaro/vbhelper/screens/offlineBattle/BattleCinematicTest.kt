package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.*
import org.junit.Assert.*
import org.junit.Test

class BattleCinematicTest {
    private fun at(phase: BattleFinisherPhase, progress: Float = 0f) =
        BattleFinisherTimeline.snapshot(1, BattleFinisherKind.JOGRESS, "a", "p", "b", "Omnimon",
            elapsedMillis = BattleFinisherTimeline.phaseStartMillis(BattleFinisherKind.JOGRESS, phase) +
                (BattleFinisherTimeline.phaseDurationMillis(BattleFinisherKind.JOGRESS, phase) * progress).toLong())

    @Test fun originalsAreReplacedByExactlyOneResultThroughTheWholeAttack() {
        for (phase in BattleFinisherPhase.entries.filter { it in BattleFinisherPhase.REVEAL..BattleFinisherPhase.AFTERMATH }) {
            val frame = sampleCinematicActors(at(phase, 0.5f))
            assertFalse("Originals must be hidden during $phase", frame.sourceVisible)
            assertTrue("The fused result must remain during $phase", frame.resultVisible)
            assertEquals(0f, frame.sourceTransition, 0f)
        }
    }

    @Test fun sourcesMergeBeforeTheResultIsRevealedAndSplitAtTheEnd() {
        assertTrue(sampleCinematicActors(at(BattleFinisherPhase.FOCUS)).sourceVisible)
        val merging = sampleCinematicActors(at(BattleFinisherPhase.TRANSFORM, 0.55f))
        assertFalse(merging.sourceVisible)
        assertTrue(merging.sourceTransition > 0)
        assertFalse(merging.resultVisible)
        val restored = sampleCinematicActors(at(BattleFinisherPhase.RESTORE, 1f))
        assertTrue(restored.sourceVisible)
        assertFalse(restored.resultVisible)
        assertEquals(0f, restored.mergeProgress, 0f)
    }

    @Test fun removingMotionDoesNotBringBackThePartnerDuringFusion() {
        for (phase in BattleFinisherPhase.entries.filter { it in BattleFinisherPhase.REVEAL..BattleFinisherPhase.AFTERMATH }) {
            val frame = sampleCinematicActors(at(phase), allowMotion = false)
            assertFalse(frame.sourceVisible)
            assertTrue(frame.resultVisible)
        }
    }

    @Test fun actionCameraFitsTheAttackInPortraitInsteadOfUsingFixedDistance() {
        val saved = CinematicCameraPose(0.0, 0.0, 0.7, 0.0, 0.27, 19.0)
        val args = at(BattleFinisherPhase.RELEASE, 0.8f)
        val portrait = cinematicCameraPose(args, BattlePosition(-3f, 0f), BattlePosition(-3f, 3f),
            BattlePosition(3f, 0f), 1.65, 0.6, saved)
        val landscape = cinematicCameraPose(args, BattlePosition(-3f, 0f), BattlePosition(-3f, 3f),
            BattlePosition(3f, 0f), 1.65, 1.7, saved)
        assertTrue(portrait.distance > landscape.distance)
        assertTrue(portrait.distance.isFinite())
        assertTrue(portrait.pitch in 0.20..0.35)
    }

    @Test fun cameraReturnsExactlyToThePlayerComposition() {
        val saved = CinematicCameraPose(2.0, -1.0, 0.7, 1.7, 0.4, 15.0)
        assertEquals(saved, cinematicCameraPose(at(BattleFinisherPhase.RESTORE, 1f),
            BattlePosition(-3f, 0f), BattlePosition(-3f, 3f), BattlePosition(3f, 0f),
            1.65, 1.0, saved))
    }

    @Test fun cameraUsesTheCurrentSideOfTheAttackInsteadOfSpinningHalfATurn() {
        val saved = CinematicCameraPose(0.0, 0.0, 0.7, Math.PI, 0.27, 19.0)
        val shot = cinematicCameraPose(at(BattleFinisherPhase.CHARGE), BattlePosition(-2f, 0f),
            BattlePosition(-2f, 2f), BattlePosition(2f, 0f), 1.65, 1.0, saved)
        assertTrue(kotlin.math.abs(shot.yaw - saved.yaw) < Math.PI / 2)
    }

    @Test fun allSampledShotsAreFiniteAndRepeatableAtTheArenaEdges() {
        val saved = CinematicCameraPose(0.0, 0.0, 0.7, 0.0, 0.27, 19.0)
        for (phase in BattleFinisherPhase.entries) for (progress in listOf(0f, 0.5f, 1f)) {
            val snapshot = at(phase, progress)
            val first = cinematicCameraPose(snapshot, BattlePosition(7f, 0f), BattlePosition(7f, 0f),
                BattlePosition(7f, 0f), 1.65, 0.2, saved)
            val second = cinematicCameraPose(snapshot, BattlePosition(7f, 0f), BattlePosition(7f, 0f),
                BattlePosition(7f, 0f), 1.65, 0.2, saved)
            assertEquals(first, second)
            assertTrue(listOf(first.targetX, first.targetZ, first.targetY, first.yaw, first.pitch, first.distance).all(Double::isFinite))
        }
    }

    @Test fun impactHasAReal150MillisVisualHoldWithoutChangingTheCoreClock() {
        val start = at(BattleFinisherPhase.IMPACT)
        val early = at(BattleFinisherPhase.IMPACT, 0.1f)
        assertEquals(0f, cinematicVisualSnapshot(early).phaseProgress, 0f)
        assertEquals(start.elapsedMillis, cinematicVisualSnapshot(early).elapsedMillis)
        assertTrue(early.elapsedMillis > start.elapsedMillis)
        assertTrue(cinematicVisualSnapshot(at(BattleFinisherPhase.IMPACT, 0.5f)).phaseProgress > 0f)
        assertEquals(early, cinematicVisualSnapshot(early, allowMotion = false))
    }

    @Test fun fusionSourcesAreBothVisibleSideBySideEvenWhenTheyStartedInDepth() {
        val sources = cinematicFusionSources(BattlePosition(-3f, -2f), BattlePosition(-3f, 2f),
            BattlePosition(3f, 0f), 1.65f)
        assertTrue(sources.second.x - sources.first.x > 1.65f)
        assertEquals(sources.first.z, sources.second.z, 0.001f)
    }
}
