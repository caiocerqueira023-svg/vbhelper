package com.tamerslab.rendering

/**
 * Axis-aligned volume of one floating voxel cube, in farm world units.
 *
 * The island's cubes are rigid boxes that bob on their own phases; this is all the collision
 * solver needs to know about them.
 *
 * The arrays are mutable on purpose: the renderer owns one instance per cube and rewrites the
 * [centre] components in place every frame instead of allocating a new volume (H17).
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
 *
 * Convenience form for tests and one-shot callers: it allocates the displacement list plus three
 * scratch vectors. A steady per-frame caller must use [separateCubeVolumesInto] with buffers it
 * owns instead, so the frame path allocates nothing (H17).
 */
internal fun separateCubeVolumes(
    volumes: List<CubeVolume>,
    iterations: Int = 4,
    padding: Float = 0.002f,
): List<FloatArray> {
    val displacements = List(volumes.size) { FloatArray(3) }
    separateCubeVolumesInto(
        volumes = volumes,
        displacements = displacements,
        scratchA = FloatArray(3),
        scratchB = FloatArray(3),
        scratchOverlap = FloatArray(3),
        iterations = iterations,
        padding = padding,
    )
    return displacements
}

/**
 * Allocation-free form of [separateCubeVolumes].
 *
 * Fills caller-owned [displacements] (one `FloatArray(3)` per volume, reset to zero on entry) and
 * reuses [scratchA], [scratchB] and [scratchOverlap] for the per-pair centres and overlaps, so a
 * steady frame allocates nothing. The buffers belong to the caller and must stay sized for the
 * current volume set; this function never reallocates them. Results are identical to
 * [separateCubeVolumes] for the same inputs.
 */
internal fun separateCubeVolumesInto(
    volumes: List<CubeVolume>,
    displacements: List<FloatArray>,
    scratchA: FloatArray,
    scratchB: FloatArray,
    scratchOverlap: FloatArray,
    iterations: Int = 4,
    padding: Float = 0.002f,
) {
    require(displacements.size >= volumes.size) {
        "displacements has ${displacements.size} slots for ${volumes.size} volumes"
    }
    require(scratchA.size >= 3 && scratchB.size >= 3 && scratchOverlap.size >= 3) {
        "solver scratch vectors must hold three axes"
    }
    val volumeCount = volumes.size
    var zeroed = 0
    while (zeroed < volumeCount) {
        val displacement = displacements[zeroed]
        displacement[0] = 0f
        displacement[1] = 0f
        displacement[2] = 0f
        zeroed++
    }
    if (volumeCount < 2) return
    repeat(iterations.coerceAtLeast(1)) {
        var first = 0
        while (first < volumeCount) {
            val a = volumes[first]
            val displacementA = displacements[first]
            var second = first + 1
            while (second < volumeCount) {
                val b = volumes[second]
                val displacementB = displacements[second]
                var axis = 0
                while (axis < 3) {
                    scratchA[axis] = a.centre[axis] + displacementA[axis]
                    scratchB[axis] = b.centre[axis] + displacementB[axis]
                    scratchOverlap[axis] = a.halfExtents[axis] + b.halfExtents[axis] -
                        kotlin.math.abs(scratchA[axis] - scratchB[axis])
                    axis++
                }
                if (scratchOverlap[0] <= 0f ||
                    scratchOverlap[1] <= 0f ||
                    scratchOverlap[2] <= 0f
                ) {
                    second++
                    continue
                }
                var leastAxis = 0
                if (scratchOverlap[1] < scratchOverlap[leastAxis]) leastAxis = 1
                if (scratchOverlap[2] < scratchOverlap[leastAxis]) leastAxis = 2
                val direction = if (scratchA[leastAxis] <= scratchB[leastAxis]) -1f else 1f
                val push = scratchOverlap[leastAxis] * 0.5f + padding * 0.5f
                displacementA[leastAxis] += direction * push
                displacementB[leastAxis] -= direction * push
                second++
            }
            first++
        }
    }
}
