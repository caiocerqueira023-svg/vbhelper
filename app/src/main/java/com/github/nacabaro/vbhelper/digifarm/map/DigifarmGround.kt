package com.github.nacabaro.vbhelper.digifarm.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Continuous feet positions on the single 3D island, stored in legacy map coordinates. */
object DigifarmGround {
    private const val CENTER_X = 256f
    private const val CENTER_Y = 368f
    private const val RADIUS_X = 225f
    private const val RADIUS_Y = 300f
    private const val WORLD_PER_X = 1.36f / Digifarm3dMap.legacyWidth
    private const val WORLD_PER_Y = 0.86f / Digifarm3dMap.legacyHeight
    private const val WORLD_RADIUS_X = RADIUS_X * WORLD_PER_X
    private const val WORLD_RADIUS_Z = RADIUS_Y * WORLD_PER_Y
    const val WALK_SPEED_WORLD_PER_SECOND = 0.14f
    /** Minimum centre spacing, close to the widest normal resident silhouette. */
    const val RESIDENT_COLLISION_DISTANCE = 0.19f
    val movingActivities = setOf("EXPLORE", "SOCIALIZE")

    val safeSpawns: List<MapPoint> = listOf(110f, 205f, 307f, 402f).flatMap { x ->
        listOf(150f, 368f, 586f).map { y -> MapPoint(x, y) }
    }

    val activityPoints: Map<String, List<MapPoint>> = mapOf(
        "REST" to listOf(MapPoint(170f, 225f), MapPoint(345f, 390f), MapPoint(225f, 530f)),
        "EAT" to listOf(MapPoint(220f, 185f), MapPoint(350f, 330f), MapPoint(290f, 555f)),
        "PLAY" to listOf(MapPoint(150f, 340f), MapPoint(385f, 385f), MapPoint(270f, 570f)),
        "TRAIN" to listOf(MapPoint(285f, 195f), MapPoint(315f, 405f), MapPoint(175f, 510f)),
        "SOCIALIZE" to listOf(MapPoint(205f, 280f), MapPoint(335f, 365f), MapPoint(250f, 510f)),
    )

    fun isWalkable(point: MapPoint): Boolean {
        val x = (point.x - CENTER_X) / RADIUS_X
        val y = (point.y - CENTER_Y) / RADIUS_Y
        return x * x + y * y <= 1f
    }

    fun clamp(point: MapPoint): MapPoint {
        val x = (point.x - CENTER_X) / RADIUS_X
        val y = (point.y - CENTER_Y) / RADIUS_Y
        val radius = sqrt(x * x + y * y)
        if (radius <= 0.98f) return point
        val scale = 0.98f / radius
        return MapPoint(CENTER_X + x * scale * RADIUS_X, CENTER_Y + y * scale * RADIUS_Y)
    }

    fun clampWorld(x: Float, z: Float): Pair<Float, Float> {
        val normalizedX = x / WORLD_RADIUS_X
        val normalizedZ = z / WORLD_RADIUS_Z
        val radius = sqrt(normalizedX * normalizedX + normalizedZ * normalizedZ)
        if (radius <= 0.98f) return x to z
        val scale = 0.98f / radius
        return x * scale to z * scale
    }

    fun randomPoint(random: Random): MapPoint {
        val angle = random.nextDouble() * 2.0 * PI
        val radius = sqrt(random.nextFloat()) * 0.9f
        return MapPoint(
            CENTER_X + (cos(angle) * radius * RADIUS_X).toFloat(),
            CENTER_Y + (sin(angle) * radius * RADIUS_Y).toFloat(),
        )
    }

    fun worldDistance(a: MapPoint, b: MapPoint): Float = hypot(
        ((a.x - b.x) * WORLD_PER_X).toDouble(),
        ((a.y - b.y) * WORLD_PER_Y).toDouble(),
    ).toFloat()

    fun advance(current: MapPoint, target: MapPoint, elapsedSeconds: Float): MapPoint {
        val distance = worldDistance(current, target)
        if (distance <= 0f) return current
        val fraction = (WALK_SPEED_WORLD_PER_SECOND * elapsedSeconds / distance).coerceIn(0f, 1f)
        return clamp(MapPoint(
            current.x + (target.x - current.x) * fraction,
            current.y + (target.y - current.y) * fraction,
        ))
    }

    /** Tries a full step, then short sidesteps around residents without jumping or pushing them. */
    fun advanceAvoidingResidents(
        current: MapPoint,
        target: MapPoint,
        elapsedSeconds: Float,
        obstacles: Collection<MapPoint>,
        preferClockwise: Boolean,
    ): MapPoint {
        val direct = advance(current, target, elapsedSeconds)
        val stepX = (direct.x - current.x) * WORLD_PER_X
        val stepZ = (direct.y - current.y) * WORLD_PER_Y
        if (stepX * stepX + stepZ * stepZ < 0.000001f) return current
        val turnAngles = if (preferClockwise) {
            intArrayOf(0, -35, 35, -70, 70, -105, 105)
        } else {
            intArrayOf(0, 35, -35, 70, -70, 105, -105)
        }
        val currentPenetration = obstacles.sumOf { obstacle ->
            (RESIDENT_COLLISION_DISTANCE - worldDistance(current, obstacle)).coerceAtLeast(0f).toDouble()
        }
        var bestRecovery: MapPoint? = null
        var bestPenetration = currentPenetration
        for (turnDegrees in turnAngles) {
            val radians = turnDegrees * PI / 180.0
            val c = cos(radians).toFloat()
            val s = sin(radians).toFloat()
            val rotatedX = stepX * c - stepZ * s
            val rotatedZ = stepX * s + stepZ * c
            val candidate = clamp(MapPoint(
                current.x + rotatedX / WORLD_PER_X,
                current.y + rotatedZ / WORLD_PER_Y,
            ))
            if (worldDistance(current, candidate) < 0.002f) continue
            var penetration = 0.0
            var enteredNewCollision = false
            obstacles.forEach { obstacle ->
                val separation = worldDistance(candidate, obstacle)
                val previousSeparation = worldDistance(current, obstacle)
                if (previousSeparation >= RESIDENT_COLLISION_DISTANCE &&
                    separation < RESIDENT_COLLISION_DISTANCE) {
                    enteredNewCollision = true
                }
                penetration += (RESIDENT_COLLISION_DISTANCE - separation).coerceAtLeast(0f)
            }
            if (penetration == 0.0) return candidate
            if (!enteredNewCollision && penetration < bestPenetration - 0.001) {
                bestRecovery = candidate
                bestPenetration = penetration
            }
        }
        return bestRecovery ?: current
    }
}
