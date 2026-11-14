package com.tamerslab.rendering.sprite3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision table for [ResidentPoseAnimation]. The renderer trusts this object blindly (it binds the
 * returned pose without re-checking the GLB), so every branch and every fallback is pinned here.
 */
class ResidentPoseAnimationTest {
    private val fullSet = setOf(
        "idle", "idle2",
        "walk", "walk2",
        "run", "run2",
        "train", "train2",
        "happy", "sleep",
    )

    @Test
    fun runningWalkPicksTheRunPairAtRunCadence() {
        val choice = ResidentPoseAnimation.choice("IDLE", walking = true, running = true, fullSet)

        assertEquals(ResidentAnimationChoice("run", "run2", 220L, ResidentGesture.NONE), choice)
    }

    @Test
    fun runningFallsBackToWalkWhenTheArtworkHasNoRunFrames() {
        val choice = ResidentPoseAnimation.choice(
            activity = "IDLE",
            walking = true,
            running = true,
            availablePoses = setOf("idle", "walk", "walk2"),
        )

        assertEquals(ResidentAnimationChoice("walk", "walk2", 250L, ResidentGesture.NONE), choice)
    }

    @Test
    fun plainWalkingUsesTheWalkPair() {
        val choice = ResidentPoseAnimation.choice("WALK", walking = true, running = false, fullSet)

        assertEquals(ResidentAnimationChoice("walk", "walk2", 250L, ResidentGesture.NONE), choice)
    }

    @Test
    fun sleepingHoldsTheSleepPoseAndBreathes() {
        val choice = ResidentPoseAnimation.choice("SLEEP", walking = false, running = false, fullSet)

        assertEquals(ResidentAnimationChoice("sleep", null, 0L, ResidentGesture.BREATHE), choice)
    }

    @Test
    fun legacyRestActivityAlsoSleeps() {
        val choice = ResidentPoseAnimation.choice("REST", walking = false, running = false, fullSet)

        assertEquals(ResidentAnimationChoice("sleep", null, 0L, ResidentGesture.BREATHE), choice)
    }

    @Test
    fun trainingCyclesTheTrainPair() {
        val choice = ResidentPoseAnimation.choice("TRAIN", walking = false, running = false, fullSet)

        assertEquals(ResidentAnimationChoice("train", "train2", 360L, ResidentGesture.NONE), choice)
    }

    @Test
    fun trainingWithoutASecondFrameReturnsNoAlternate() {
        val choice = ResidentPoseAnimation.choice(
            activity = "TRAIN",
            walking = false,
            running = false,
            availablePoses = setOf("idle", "train"),
        )

        assertEquals(ResidentAnimationChoice("train", null, 360L, ResidentGesture.NONE), choice)
    }

    @Test
    fun happyAndItsLegacyActivitiesHoldAndHop() {
        val expected = ResidentAnimationChoice("happy", null, 0L, ResidentGesture.HOP)
        listOf("HAPPY", "PLAY", "SOCIAL", "SOCIALIZE", "EAT").forEach { activity ->
            assertEquals(activity, expected,
                ResidentPoseAnimation.choice(activity, walking = false, running = false, fullSet))
        }
    }

    @Test
    fun unknownActivityFallsBackToTheIdlePair() {
        val choice = ResidentPoseAnimation.choice("PROCESSING", walking = false, running = false, fullSet)

        assertEquals(ResidentAnimationChoice("idle", "idle2", 900L, ResidentGesture.NONE), choice)
    }

    @Test
    fun sparseArtworkFallsBackToHeldIdleWithoutAlternates() {
        val sparse = setOf("idle", "walk", "walk2")

        assertEquals(
            ResidentAnimationChoice("idle", null, 900L, ResidentGesture.NONE),
            ResidentPoseAnimation.choice("TRAIN", walking = false, running = false, sparse),
        )
        assertEquals(
            ResidentAnimationChoice("idle", null, 0L, ResidentGesture.BREATHE),
            ResidentPoseAnimation.choice("SLEEP", walking = false, running = false, sparse),
        )
        assertEquals(
            ResidentAnimationChoice("idle", null, 0L, ResidentGesture.HOP),
            ResidentPoseAnimation.choice("HAPPY", walking = false, running = false, sparse),
        )
        assertEquals(
            ResidentAnimationChoice("walk", "walk2", 250L, ResidentGesture.NONE),
            ResidentPoseAnimation.choice("IDLE", walking = true, running = true, sparse),
        )
    }

    @Test
    fun alternateIsOnlyReturnedWhenItIsActuallyAvailable() {
        val choice = ResidentPoseAnimation.choice("IDLE", walking = false, running = false, setOf("idle"))

        assertEquals("idle", choice.pose)
        assertNull(choice.alternate)
    }

    @Test
    fun emptyPoseSetReturnsTheNominalFamilyAndNeverThrows() {
        val running = ResidentPoseAnimation.choice("IDLE", true, true, emptySet())
        val walking = ResidentPoseAnimation.choice("IDLE", true, false, emptySet())
        val sleeping = ResidentPoseAnimation.choice("SLEEP", false, false, emptySet())
        val idle = ResidentPoseAnimation.choice("IDLE", false, false, emptySet())

        assertEquals(ResidentAnimationChoice("run", null, 220L, ResidentGesture.NONE), running)
        assertEquals(ResidentAnimationChoice("walk", null, 250L, ResidentGesture.NONE), walking)
        assertEquals(ResidentAnimationChoice("sleep", null, 0L, ResidentGesture.BREATHE), sleeping)
        assertEquals(ResidentAnimationChoice("idle", null, 900L, ResidentGesture.NONE), idle)
    }

    @Test
    fun anUnexpectedPoseSetStillReturnsAPoseItContains() {
        val odd = setOf("train")
        listOf("SLEEP", "TRAIN", "HAPPY", "IDLE", "UNKNOWN").forEach { activity ->
            val choice = ResidentPoseAnimation.choice(activity, walking = false, running = false, odd)
            assertTrue(
                "$activity returned ${choice.pose} which is not in $odd",
                choice.pose in odd,
            )
            assertNull(choice.alternate)
        }
    }

    @Test
    fun activityMatchingIsTrimmedAndCaseInsensitive() {
        val choice = ResidentPoseAnimation.choice("  sLeEp  ", walking = false, running = false, fullSet)

        assertEquals(ResidentGesture.BREATHE, choice.gesture)
        assertEquals("sleep", choice.pose)
    }

}
