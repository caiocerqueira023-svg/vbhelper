package com.tamerslab.rendering

/**
 * Axis-aligned volume of one floating voxel cube, in farm world units.
 *
 * The island's cubes are rigid boxes that bob on their own phases; this is all the collision
 * solver needs to know about them.
 */
internal class CubeVolume(
    val centre: FloatArray,
    val halfExtents: FloatArray,
)

/**
 * Pushes overlapping floating cubes apart.
 *
 * Returns one displacement per volume, in the same order: each overlapping pair is separated along
 * its axis of least penetration, half to each cube, plus a small [padding] so they never settle
 * exactly face to face. [iterations] passes settle chains (a cube squeezed by two neighbours) and
 * stacked cubes. Volumes that do not overlap keep a zero displacement.
 */
internal fun separateCubeVolumes(
    volumes: List<CubeVolume>,
    iterations: Int = 4,
    padding: Float = 0.002f,
): List<FloatArray> {
    val displacements = List(volumes.size) { FloatArray(3) }
    if (volumes.size < 2) return displacements
    repeat(iterations.coerceAtLeast(1)) {
        for (first in volumes.indices) {
            val a = volumes[first]
            for (second in first + 1 until volumes.size) {
                val b = volumes[second]
                val aCentre = FloatArray(3) { a.centre[it] + displacements[first][it] }
                val bCentre = FloatArray(3) { b.centre[it] + displacements[second][it] }
                val overlaps = FloatArray(3) {
                    a.halfExtents[it] + b.halfExtents[it] - kotlin.math.abs(aCentre[it] - bCentre[it])
                }
                if (overlaps.any { it <= 0f }) continue
                var axis = 0
                for (candidate in 1..2) if (overlaps[candidate] < overlaps[axis]) axis = candidate
                val direction = if (aCentre[axis] <= bCentre[axis]) -1f else 1f
                val push = overlaps[axis] * 0.5f + padding * 0.5f
                displacements[first][axis] += direction * push
                displacements[second][axis] -= direction * push
            }
        }
    }
    return displacements
}
