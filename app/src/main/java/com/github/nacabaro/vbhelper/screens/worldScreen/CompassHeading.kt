package com.github.nacabaro.vbhelper.screens.worldScreen

import kotlin.math.atan2

internal fun compassDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

internal fun compassDelta(from: Float, to: Float): Float = compassDegrees(to - from + 180f) - 180f

/** Horizontal bearing of the display's top edge, or its back when held upright. */
internal fun compassBearing(matrix: FloatArray, displayRotation: Int = 0): Float {
    val (east, north) = when (displayRotation) {
        1 -> -matrix[0] to -matrix[3]
        2 -> -matrix[1] to -matrix[4]
        3 -> matrix[0] to matrix[3]
        else -> matrix[1] to matrix[4]
    }
    val backEast = -matrix[2]
    val backNorth = -matrix[5]
    val angle = if (east * east + north * north >= backEast * backEast + backNorth * backNorth) {
        atan2(east.toDouble(), north.toDouble())
    } else {
        atan2(backEast.toDouble(), backNorth.toDouble())
    }
    return compassDegrees(Math.toDegrees(angle).toFloat())
}

/** Quality must come from the magnetometer itself, not the rotation-vector accuracy. */
internal fun trustworthyMagneticField(accuracy: Int, strengthUt: Float, expectedUt: Float?): Boolean {
    if (accuracy < 2 || !strengthUt.isFinite()) return false
    return if (expectedUt != null) {
        val tolerance = maxOf(8f, expectedUt * 0.3f)
        kotlin.math.abs(strengthUt - expectedUt) <= tolerance
    } else {
        strengthUt in 15f..80f
    }
}

/**
 * Shortest-path smoothing with private filter memory. A rejected magnetic sample
 * leaves the last valid bearing unchanged; it must never be interpreted as zero.
 */
internal class CompassHeading {
    private var filtered: Float? = null

    fun update(magneticDegrees: Float?, declination: Float): Float? {
        if (magneticDegrees == null || !magneticDegrees.isFinite() || !declination.isFinite()) {
            return filtered
        }
        val target = compassDegrees(magneticDegrees + declination)
        val previous = filtered
        filtered = if (previous == null) target else {
            compassDegrees(previous + compassDelta(previous, target) * 0.35f)
        }
        return filtered
    }
}
