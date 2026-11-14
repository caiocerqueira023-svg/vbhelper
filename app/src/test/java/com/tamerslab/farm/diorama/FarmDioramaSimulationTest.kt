package com.tamerslab.farm.diorama

import com.tamerslab.farm.map.FarmWorldBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.hypot

/**
 * Pins the probability-driven pacing contract:
 *
 *  - the opening tableau and every later beat keep the cast separated and on the island;
 *  - the decisions are individual rolls, so most beats are standing/walking and the special
 *    behaviours stay occasional — with no cap anywhere that would stop several residents doing the
 *    same thing;
 *  - nobody teleports, and a sleeping resident is never displaced;
 *  - social beats still happen and the pair genuinely faces each other;
 *  - the same seed always produces the same show.
 */
class FarmDioramaSimulationTest {

    private val bounds = FarmWorldBounds(
        min = listOf(-0.68f, -0.43f),
        max = listOf(0.68f, 0.43f),
    )

    private fun seeds(count: Int = 12, radius: Float = 0.065f): List<FarmDioramaSeed> =
        (1..count).map { FarmDioramaSeed(id = "farm-$it", bodyRadius = radius) }

    private fun simulation(
        count: Int = 12,
        seed: Long = 7L,
        radius: Float = 0.065f,
    ) = FarmDioramaSimulation(seeds = seeds(count, radius), bounds = bounds, seed = seed)

    private fun FarmDioramaFrame.pose(id: String): FarmDioramaPose =
        poses.first { it.id == id }

    private fun assertSeparated(frame: FarmDioramaFrame, tolerance: Float = 0.004f) {
        val poses = frame.poses
        for (index in poses.indices) {
            for (otherIndex in index + 1 until poses.size) {
                val a = poses[index]
                val b = poses[otherIndex]
                // Radii are not part of the frame; the seeds use one radius for this suite.
                val gap = hypot(a.x - b.x, a.z - b.z)
                assertTrue(
                    "${a.id} and ${b.id} overlap ($gap)",
                    gap >= 2f * SEED_RADIUS - tolerance,
                )
            }
        }
    }

    private fun assertOnIsland(frame: FarmDioramaFrame) {
        frame.poses.forEach { pose ->
            val margin = SEED_RADIUS + 0.001f
            assertTrue("${pose.id} left the island on x", pose.x >= bounds.minX + margin)
            assertTrue("${pose.id} left the island on x", pose.x <= bounds.maxX - margin)
            assertTrue("${pose.id} left the island on z", pose.z >= bounds.minZ + margin)
            assertTrue("${pose.id} left the island on z", pose.z <= bounds.maxZ - margin)
        }
    }

    @Test
    fun initialTableauIsSeparatedAndOnTheIsland() {
        val frame = simulation().initialFrame()

        assertEquals(12, frame.poses.size)
        assertSeparated(frame)
        assertOnIsland(frame)
        frame.poses.forEach {
            assertEquals(FarmDioramaActivity.IDLE, it.activity)
            assertEquals(1_100L, it.stepMillis)
        }
        assertTrue(frame.poses.none { it.activity == FarmDioramaActivity.WALK })
    }

    @Test
    fun noResidentEverOverlapsOrLeavesTheIsland() {
        val simulation = simulation()
        var frame = simulation.initialFrame()
        repeat(6_000) {
            frame = simulation.step(100L)
            assertSeparated(frame)
            assertOnIsland(frame)
        }
    }

    /**
     * The pacing contract the owner asked for: most of the time residents just stand or walk, and
     * everything else is an occasional roll rather than a scheduled show.
     */
    @Test
    fun mostBeatsAreWalkingOrStanding() {
        val simulation = simulation()
        var frame = simulation.initialFrame()
        var idleSteps = 0
        var walkSteps = 0
        var specialSteps = 0
        var totalActorSteps = 0
        repeat(6_000) {
            frame = simulation.step(100L)
            frame.poses.forEach { pose ->
                totalActorSteps++
                when (pose.activity) {
                    FarmDioramaActivity.IDLE -> idleSteps++
                    FarmDioramaActivity.WALK -> walkSteps++
                    else -> specialSteps++
                }
            }
        }
        val everyday = (idleSteps + walkSteps).toDouble() / totalActorSteps
        val special = specialSteps.toDouble() / totalActorSteps
        assertTrue("everyday share $everyday", everyday >= 0.75)
        assertTrue("special share $special", special <= 0.25)
        assertTrue("nobody ever walks", walkSteps > totalActorSteps / 10)
        assertTrue("nobody ever stands", idleSteps > totalActorSteps / 4)
    }

    /** No director caps how many may move at once: several walkers must be possible at the same time. */
    @Test
    fun severalResidentsCanMoveAtTheSameTime() {
        val simulation = simulation(seed = 11L)
        var frame = simulation.initialFrame()
        var mostMovers = 0
        repeat(6_000) {
            frame = simulation.step(100L)
            val movers = frame.poses.count {
                it.activity == FarmDioramaActivity.WALK || it.activity == FarmDioramaActivity.RUN
            }
            mostMovers = maxOf(mostMovers, movers)
        }
        assertTrue("never saw more than $mostMovers movers", mostMovers >= 4)
    }

    @Test
    fun residentsNeverTeleportAndSleepersStayPlanted() {
        val simulation = simulation()
        var frame = simulation.initialFrame()
        repeat(6_000) {
            val previous = frame.poses.associateBy { it.id }
            frame = simulation.step(100L)
            frame.poses.forEach { pose ->
                val before = previous.getValue(pose.id)
                val moved = hypot(pose.x - before.x, pose.z - before.z)
                // A stride burst is one step of up to 0.32 units (run) plus a possible collision push,
                // so 0.55 still rules out any teleport across the island while allowing the restored
                // old step rhythm.
                assertTrue("${pose.id} jumped $moved", moved <= 0.55f)
                if (before.activity == FarmDioramaActivity.SLEEP &&
                    pose.activity == FarmDioramaActivity.SLEEP
                ) {
                    assertEquals("${pose.id} drifted while asleep", 0f, moved, 1e-4f)
                }
            }
        }
    }

    /** The ids of two residents holding the happy pose while pointing at each other. */
    private fun mutualHappyPair(frame: FarmDioramaFrame): Pair<String, String>? {
        val happy = frame.poses.filter { it.activity == FarmDioramaActivity.HAPPY }
        for (first in happy) {
            val partner = happy.firstOrNull { other ->
                other.id != first.id &&
                    first.lookAtX != null && first.lookAtZ != null &&
                    other.lookAtX != null && other.lookAtZ != null &&
                    kotlin.math.abs(first.lookAtX - other.x) < 1e-4f &&
                    kotlin.math.abs(first.lookAtZ - other.z) < 1e-4f &&
                    kotlin.math.abs(other.lookAtX - first.x) < 1e-4f &&
                    kotlin.math.abs(other.lookAtZ - first.z) < 1e-4f
            }
            if (partner != null) return first.id to partner.id
        }
        return null
    }

    @Test
    fun socialBeatHappensAndLingers() {
        val simulation = simulation(seed = 11L)
        var frame = simulation.initialFrame()
        repeat(6_000) {
            frame = simulation.step(100L)
            val pair = mutualHappyPair(frame) ?: return@repeat
            // Greetings are rare, so they are held: the face-to-face celebration alone is 5.5-8.5 s.
            // Detection happens on its first step, so the remaining celebration must be well over 40
            // simulation beats.
            var celebrationSteps = 0
            var next = frame
            repeat(400) {
                next = simulation.step(100L)
                if (mutualHappyPair(next) == pair) {
                    celebrationSteps++
                } else {
                    return@repeat
                }
            }
            assertTrue(
                "greeting lasted only $celebrationSteps beats",
                celebrationSteps >= 45,
            )
            return
        }
        fail("no social beat in ten minutes")
    }

    @Test
    fun sleepIsOccasionalAndNeverTheDefault() {
        val simulation = simulation(seed = 3L)
        var frame = simulation.initialFrame()
        var sleeperSteps = 0
        var totalActorSteps = 0
        repeat(6_000) {
            frame = simulation.step(100L)
            totalActorSteps += frame.poses.size
            sleeperSteps += frame.poses.count { it.activity == FarmDioramaActivity.SLEEP }
        }
        assertTrue("nobody ever sleeps over ten minutes", sleeperSteps > 0)
        // Sleep is 1% per roll with long holds, so it must stay a small slice of the island's life.
        val share = sleeperSteps.toDouble() / totalActorSteps
        assertTrue("cast spends too much time asleep ($share)", share < 0.15)
    }

    @Test
    fun sameSeedReplaysTheSameShow() {
        val first = simulation(seed = 42L)
        val second = simulation(seed = 42L)
        var firstFrame = first.initialFrame()
        var secondFrame = second.initialFrame()
        assertEquals(firstFrame, secondFrame)
        repeat(800) {
            firstFrame = first.step(100L)
            secondFrame = second.step(100L)
            assertEquals(firstFrame, secondFrame)
        }
    }

    /**
     * The production host seeds every cast with fresh entropy, so two runs must never play the same
     * script. (The fixed-seed path above is only the test contract.)
     */
    @Test
    fun differentSeedsDoNotReplayTheSameShow() {
        val shows = listOf(1L, 2L, 3L).map { seed ->
            val simulation = simulation(seed = seed)
            var frame = simulation.initialFrame()
            repeat(600) { frame = simulation.step(100L) }
            frame
        }
        assertTrue("three seeds produced one identical show", shows.toSet().size >= 2)
    }

    @Test
    fun currentFrameDoesNotAdvanceTheClockAndMatchesTheLastStep() {
        val simulation = simulation()
        val opening = simulation.initialFrame()

        assertEquals(opening, simulation.currentFrame())
        assertEquals(opening, simulation.step(0L))

        val afterStep = simulation.step(100L)
        assertEquals(afterStep, simulation.currentFrame())
    }

    private companion object {
        const val SEED_RADIUS = 0.065f
    }
}
