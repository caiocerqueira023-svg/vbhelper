package com.github.nacabaro.vbhelper.rendering

import kotlin.math.exp

/** Frame-rate-independent exponential approach for camera and scene motion. */
internal fun approachSceneAxis(
    current: Double,
    target: Double,
    deltaSeconds: Double,
    responsiveness: Double = 5.0,
): Double {
    if (!current.isFinite() || !target.isFinite()) return target
    val dt = deltaSeconds.coerceIn(0.0, 0.1)
    val alpha = 1.0 - exp(-responsiveness.coerceAtLeast(0.0) * dt)
    return current + (target - current) * alpha
}

