package com.tamerslab.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Regression for the real Digifarm cube layout: the fourteen floating cubes with the exact groups,
 * bob amplitudes, speeds and phases the renderer uses. The authored rest boxes do not overlap, but
 * the independent bobs push stacked pairs up to ~26mm into each other - which is the clipping the
 * farm showed. This runs the same solver the renderer runs and requires that no pair ever intersects.
 */
class FloatingCubeLayoutTest {
    /** name, rest world centre, half extent, group, bob amplitude, bob speed, bob phase. */
    private data class Cube(
        val rest: FloatArray,
        val half: Float,
        val group: Int,
        val amplitude: Float,
        val speed: Float,
        val phase: Float,
    )

    private val cubes = listOf(
        Cube(floatArrayOf(-0.9100f, -0.0900f, -0.8000f), 0.0750f, 0, 0.0180f, 1.70f, 0.00f),
        Cube(floatArrayOf(-0.9100f, 0.0700f, -0.8000f), 0.0500f, 0, 0.0215f, 1.83f, 1.37f),
        Cube(floatArrayOf(-0.7300f, 0.0100f, -0.9100f), 0.0450f, 0, 0.0250f, 1.96f, 2.74f),
        Cube(floatArrayOf(0.8700f, -0.0900f, -0.8000f), 0.0750f, 1, 0.0285f, 2.09f, 4.11f),
        Cube(floatArrayOf(0.8700f, 0.0700f, -0.8000f), 0.0500f, 1, 0.0320f, 2.22f, 5.48f),
        Cube(floatArrayOf(1.0200f, 0.0400f, -0.7200f), 0.0400f, 1, 0.0180f, 1.70f, 6.85f),
        Cube(floatArrayOf(-1.1200f, -0.0400f, -0.1800f), 0.0600f, 2, 0.0215f, 1.83f, 8.22f),
        Cube(floatArrayOf(-1.1200f, 0.0900f, -0.1800f), 0.0400f, 2, 0.0250f, 1.96f, 9.59f),
        Cube(floatArrayOf(1.1200f, -0.0400f, -0.1200f), 0.0600f, 3, 0.0285f, 2.09f, 10.96f),
        Cube(floatArrayOf(1.1200f, 0.0900f, -0.1200f), 0.0400f, 3, 0.0320f, 2.22f, 12.33f),
        Cube(floatArrayOf(-0.8700f, -0.1000f, 0.7800f), 0.0550f, 4, 0.0180f, 1.70f, 13.70f),
        Cube(floatArrayOf(-1.0000f, 0.0200f, 0.7300f), 0.0400f, 4, 0.0215f, 1.83f, 15.07f),
        Cube(floatArrayOf(0.8600f, -0.1000f, 0.7900f), 0.0550f, 5, 0.0250f, 1.96f, 16.44f),
        Cube(floatArrayOf(0.9900f, 0.0200f, 0.7300f), 0.0400f, 5, 0.0285f, 2.09f, 17.81f),
    )
    private val groupAmplitude = (0 until 6).map { 0.052f + it * 0.003f }
    private val groupSpeed = (0 until 6).map { 1.25f + it * 0.11f }
    private val groupPhase = (0 until 6).map { it * 1.19f }

    private fun frame(time: Float): List<CubeVolume> = cubes.map { cube ->
        val groupBob = groupAmplitude[cube.group] *
            sin(time * groupSpeed[cube.group] + groupPhase[cube.group])
        val ownBob = cube.amplitude * sin(time * cube.speed + cube.phase)
        CubeVolume(
            floatArrayOf(cube.rest[0], cube.rest[1] + groupBob + ownBob, cube.rest[2]),
            floatArrayOf(cube.half, cube.half, cube.half),
        )
    }

    private fun penetration(frame: List<CubeVolume>, displacements: List<FloatArray>): Float {
        var worst = 0f
        for (first in frame.indices) for (second in first + 1 until frame.size) {
            val overlaps = FloatArray(3) { axis ->
                frame[first].halfExtents[axis] + frame[second].halfExtents[axis] - kotlin.math.abs(
                    frame[first].centre[axis] + displacements[first][axis] -
                        frame[second].centre[axis] - displacements[second][axis],
                )
            }
            if (overlaps.all { it > 0f }) worst = maxOf(worst, overlaps.min())
        }
        return worst
    }

    @Test
    fun theAuthoredBobDoesOverlapWithoutTheSolver() {
        var worst = 0f
        for (step in 0..1200) worst = maxOf(worst, penetration(frame(step / 60f), List(cubes.size) { FloatArray(3) }))
        assertTrue("the layout no longer produces overlaps; the regression case is stale", worst > 0.005f)
    }

    @Test
    fun solvedLayoutNeverIntersects() {
        for (step in 0..1200) {
            val sampled = frame(step / 60f)
            val displacements = separateCubeVolumes(sampled)
            // Corrections are applied on top of the authored bob every frame, exactly like the renderer.
            assertEquals("cubes intersect at frame $step (%.4f)".format(penetration(sampled, displacements)),
                0f, penetration(sampled, displacements), 1e-5f)
        }
    }
}
