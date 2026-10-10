package com.github.nacabaro.vbhelper.battle.offline.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object BattleMovementRules {
    const val LEGACY_VERSION = 1
    const val CURRENT_VERSION = 2
}

internal data class BattleMovementObstacle(val position: BattlePosition, val collisionRadius: Float)

/** Local steering around occupied lanes and arena edges; the technique still owns its valid range. */
internal fun steerBattlePosition(
    position: BattlePosition,
    destination: BattlePosition,
    stride: Float,
    collisionRadius: Float,
    arenaRadius: Float,
    obstacles: List<BattleMovementObstacle>,
    side: Float = 1f,
): BattlePosition {
    val dx = destination.x - position.x
    val dz = destination.z - position.z
    val distance = hypot(dx, dz)
    if (distance < 0.0001f || stride <= 0f) return position
    val step = minOf(stride, distance)
    val nx = dx / distance
    val nz = dz / distance
    val limit = (arenaRadius - collisionRadius).coerceAtLeast(0f)
    fun bounded(point: BattlePosition): BattlePosition {
        val length = hypot(point.x, point.z)
        return if (length <= limit || length == 0f) point else BattlePosition(point.x * limit / length, point.z * limit / length)
    }
    fun clear(point: BattlePosition): Boolean = obstacles.all { obstacle ->
        val radius = (collisionRadius + obstacle.collisionRadius - 0.001f).coerceAtLeast(0f)
        val before = position.distanceTo(obstacle.position)
        if (before < radius) point.distanceTo(obstacle.position) > before + 0.0001f
        else {
            val sx = point.x - position.x
            val sz = point.z - position.z
            val squared = sx * sx + sz * sz
            val t = if (squared > 0f) (((obstacle.position.x - position.x) * sx +
                (obstacle.position.z - position.z) * sz) / squared).coerceIn(0f, 1f) else 0f
            hypot(position.x + sx * t - obstacle.position.x, position.z + sz * t - obstacle.position.z) >= radius
        }
    }
    val direct = bounded(BattlePosition(position.x + nx * step, position.z + nz * step))
    if (clear(direct) && position.distanceTo(direct) >= step * 0.85f) return direct
    val direction = if (side >= 0f) 1f else -1f
    var best = position
    var bestScore = Double.NEGATIVE_INFINITY
    for (degrees in listOf(30f, -30f, 60f, -60f, 90f, -90f, 120f, -120f, 150f, -150f)) {
        val angle = Math.toRadians((degrees * direction).toDouble())
        val rx = nx * cos(angle) - nz * sin(angle)
        val rz = nx * sin(angle) + nz * cos(angle)
        val candidate = bounded(BattlePosition(position.x + (rx * step).toFloat(), position.z + (rz * step).toFloat()))
        val travelled = position.distanceTo(candidate)
        if (travelled < step * 0.25f || !clear(candidate)) continue
        val gain = (distance - candidate.distanceTo(destination)) / step
        val clearance = obstacles.minOfOrNull { candidate.distanceTo(it.position) - collisionRadius - it.collisionRadius }
            ?.coerceIn(0f, 1f) ?: 1f
        val score = gain + travelled / step * 0.18 + clearance * 0.04
        if (score > bestScore + 0.00001) { best = candidate; bestScore = score }
    }
    return best
}
