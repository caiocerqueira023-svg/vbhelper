package com.github.nacabaro.vbhelper.screens.offlineBattle

import kotlin.math.hypot

internal data class ArenaCameraPoint(val x: Double, val z: Double)

/** Keeps the camera eye inside the clear circle in front of the arena seating. */
internal fun constrainArenaCameraEye(
    eyeX: Double,
    eyeZ: Double,
    collisionRadius: Double
): ArenaCameraPoint {
    require(eyeX.isFinite() && eyeZ.isFinite())
    require(collisionRadius.isFinite() && collisionRadius > 0.0)
    val distance = hypot(eyeX, eyeZ)
    if (distance <= collisionRadius || distance == 0.0) return ArenaCameraPoint(eyeX, eyeZ)
    val scale = collisionRadius / distance
    return ArenaCameraPoint(eyeX * scale, eyeZ * scale)
}
