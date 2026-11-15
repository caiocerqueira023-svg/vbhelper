package com.tamerslab.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CubeCollisionTest {
    private fun cube(x: Float, y: Float, z: Float, half: Float = 0.05f) =
        CubeVolume(floatArrayOf(x, y, z), floatArrayOf(half, half, half))

    private fun centres(volumes: List<CubeVolume>, displacements: List<FloatArray>) =
        volumes.indices.map { index -> FloatArray(3) { volumes[index].centre[it] + displacements[index][it] } }

    private fun distance(a: FloatArray, b: FloatArray) = FloatArray(3) { a[it] - b[it] }

    @Test
    fun nonOverlappingCubesAreLeftAlone() {
        val volumes = listOf(cube(0f, 0f, 0f), cube(1f, 0f, 0f), cube(0f, 1f, 0f))
        val displacements = separateCubeVolumes(volumes)
        for (displacement in displacements) {
            assertEquals(0f, abs(displacement[0]) + abs(displacement[1]) + abs(displacement[2]), 0f)
        }
    }

    @Test
    fun overlappingCubesSeparateAlongTheLeastPenetrationAxis() {
        // Deep overlap on x, shallow on y: they must part on y.
        val a = CubeVolume(floatArrayOf(0f, 0f, 0f), floatArrayOf(0.05f, 0.05f, 0.05f))
        val b = CubeVolume(floatArrayOf(0.02f, 0.09f, 0f), floatArrayOf(0.05f, 0.05f, 0.05f))
        val displacements = separateCubeVolumes(listOf(a, b))
        assertEquals(0f, displacements[0][0], 1e-6f)
        assertEquals(0f, displacements[1][0], 1e-6f)
        assertTrue("lower cube should be pushed down", displacements[0][1] < 0f)
        assertTrue("upper cube should be pushed up", displacements[1][1] > 0f)
    }

    @Test
    fun stackedCubesEndUpTouchingButNotIntersecting() {
        val half = 0.05f
        val volumes = listOf(cube(0f, 0f, 0f, half), cube(0f, 0.04f, 0f, half), cube(0f, 0.08f, 0f, half))
        val settled = centres(volumes, separateCubeVolumes(volumes))
        for (index in volumes.indices) for (other in index + 1 until volumes.size) {
            val gap = distance(settled[index], settled[other])
            val overlap = half * 2f - abs(gap[1])
            assertTrue("stacked cubes still intersect: $overlap", overlap <= 1e-4f)
        }
    }

    @Test
    fun identicalCubesGetSeparatedDeterministically() {
        val volumes = listOf(cube(0f, 0f, 0f), cube(0f, 0f, 0f))
        val first = separateCubeVolumes(volumes)
        val second = separateCubeVolumes(volumes)
        for (index in 0..1) {
            assertEquals(first[index][0], second[index][0], 0f)
            assertEquals(first[index][1], second[index][1], 0f)
            assertEquals(first[index][2], second[index][2], 0f)
        }
        val settled = centres(volumes, first)
        val separated = (0..2).any { axis -> abs(settled[0][axis] - settled[1][axis]) >= 0.1f }
        assertTrue("identical cubes are still on top of each other", separated)
    }
}
