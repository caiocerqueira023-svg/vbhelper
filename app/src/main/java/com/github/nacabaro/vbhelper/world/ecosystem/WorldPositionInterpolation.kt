package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry

/** Display-only interpolation: range/claims always use the committed [EcosystemIndividual.position]. */
fun EcosystemIndividual.displayPosition(frameNanos: Long, motion: Boolean): GeoPoint {
    val previous = previousPosition ?: return position
    if (!motion || motionFrameNanos == 0L) return position
    val alpha = ((frameNanos - motionFrameNanos).toDouble() / (WorldEcosystemClock.TICK_MILLIS * 1_000_000L)).coerceIn(0.0, 1.0)
    val offset = RadarWorldGeometry.relative(previous, position)
    return RadarWorldGeometry.offset(previous, offset.northMeters * alpha, offset.eastMeters * alpha)
}
