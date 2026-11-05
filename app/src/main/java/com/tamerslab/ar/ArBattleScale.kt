package com.tamerslab.ar

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mapping between the real world (metres) and the arena scene units used by the battle renderer.
 *
 * The radar arena is `2 * playableRadius` scene units wide (16 units for `Arena/Radar`). A
 * [ArBattleScalePlan] chooses how wide that arena should be in real metres; the resulting
 * [ArPlacement.metersPerSceneUnit] converts every arena scene unit to metres, so the same
 * fight can be a ~1 m diorama on a table or a life-size brawl on a lawn.
 *
 * Everything here is plain Kotlin so it can be unit-tested on the JVM without ARCore.
 */
enum class ArBattleScalePlan {
    /** Derive the size from the detected surface (75% of its smallest extent). */
    AUTO,

    /** Miniature diorama for a table (~0.9 m arena). */
    TABLETOP,

    /** Indoor floor scale (~2.5 m arena). */
    ROOM,

    /** Park / lawn scale (~8 m arena). */
    OPEN_SPACE,
}

/** Real-world footprint of the surface the battle will be placed on, in metres. */
data class ArPlaneSize(
    val widthMeters: Float,
    val depthMeters: Float,
) {
    init {
        require(widthMeters.isFinite() && depthMeters.isFinite()) {
            "plane extents must be finite: $widthMeters x $depthMeters"
        }
        require(widthMeters >= 0f && depthMeters >= 0f) {
            "plane extents must not be negative: $widthMeters x $depthMeters"
        }
    }

    /** Shortest side, which bounds the largest arena that still fits on the surface. */
    val minExtentMeters: Float get() = min(widthMeters, depthMeters)

    /** Longest side; kept for diagnostics and UI copy. */
    val maxExtentMeters: Float get() = if (widthMeters >= depthMeters) widthMeters else depthMeters
}

/**
 * Scale/placement maths for AR battles.
 *
 * Distances in this object are always metres; scene units only appear where the conversion is
 * explicit (`metersPerSceneUnit`, `arenaWidthUnits`, `sceneUnitsForBattleDiameter`).
 */
object ArBattleScale {

    /** Arena width of the radar battle (`2 * playableRadius` for `Arena/Radar`). */
    const val RADAR_ARENA_WIDTH_UNITS = 16f

    /** Arena diameter of a tabletop diorama. */
    const val TABLETOP_DIAMETER_METERS = 0.9f

    /** Arena diameter of a room-sized floor battle. */
    const val ROOM_DIAMETER_METERS = 2.5f

    /** Arena diameter of an open-space battle. */
    const val OPEN_SPACE_DIAMETER_METERS = 8f

    /** [ArBattleScalePlan.AUTO] uses this fraction of the surface's shortest side. */
    const val AUTO_DIAMETER_FRACTION = 0.75f

    /** Smallest arena we are willing to render (a 0.9 m table can be far away). */
    const val MIN_BATTLE_DIAMETER_METERS = 0.5f

    /** Largest arena we are willing to render without losing tracking range. */
    const val MAX_BATTLE_DIAMETER_METERS = 12f

    /** Smallest supported real-world size of one scene unit (2 cm). */
    const val MIN_METERS_PER_SCENE_UNIT = 0.02f

    /** Largest supported real-world size of one scene unit (1.5 m). */
    const val MAX_METERS_PER_SCENE_UNIT = 1.5f

    /** A surface whose shortest side is below this is treated as a table. */
    const val TABLETOP_MAX_EXTENT_METERS = 1.4f

    /** A surface whose shortest side is below this is treated as a room floor. */
    const val ROOM_MAX_EXTENT_METERS = 4f

    /** User pinching can shrink the arena to 60% of the plan value. */
    const val MIN_PLAN_SCALE_FACTOR = 0.6f

    /** User pinching can grow the arena to 160% of the plan value. */
    const val MAX_PLAN_SCALE_FACTOR = 1.6f

    /**
     * Plan suggested by the detected surface: tables are small, living-room floors medium and
     * anything bigger is treated as open space.
     */
    fun recommendedPlan(size: ArPlaneSize): ArBattleScalePlan = when {
        size.minExtentMeters < TABLETOP_MAX_EXTENT_METERS -> ArBattleScalePlan.TABLETOP
        size.minExtentMeters < ROOM_MAX_EXTENT_METERS -> ArBattleScalePlan.ROOM
        else -> ArBattleScalePlan.OPEN_SPACE
    }

    /**
     * Arena diameter in metres for [plan] on [planeSize].
     *
     * `AUTO` derives the diameter from the surface, every other plan is a fixed target; all
     * values are clamped to [MIN_BATTLE_DIAMETER_METERS]..[MAX_BATTLE_DIAMETER_METERS] so a
     * wrong plane estimate can never produce an unusable arena.
     */
    fun targetBattleDiameterMeters(plan: ArBattleScalePlan, planeSize: ArPlaneSize): Float {
        val target = when (plan) {
            ArBattleScalePlan.AUTO -> AUTO_DIAMETER_FRACTION * planeSize.minExtentMeters
            ArBattleScalePlan.TABLETOP -> TABLETOP_DIAMETER_METERS
            ArBattleScalePlan.ROOM -> ROOM_DIAMETER_METERS
            ArBattleScalePlan.OPEN_SPACE -> OPEN_SPACE_DIAMETER_METERS
        }
        return if (target.isFinite()) {
            target.coerceIn(MIN_BATTLE_DIAMETER_METERS, MAX_BATTLE_DIAMETER_METERS)
        } else {
            MIN_BATTLE_DIAMETER_METERS
        }
    }

    /**
     * Real-world size of one arena scene unit for [plan] on [planeSize].
     *
     * [arenaWidthUnits] is the arena width in scene units (`2 * manifest.playableRadius`, 16 for
     * the radar arena). The result is clamped to
     * [MIN_METERS_PER_SCENE_UNIT]..[MAX_METERS_PER_SCENE_UNIT].
     */
    fun metersPerSceneUnit(
        plan: ArBattleScalePlan,
        planeSize: ArPlaneSize,
        arenaWidthUnits: Float,
    ): Float {
        require(arenaWidthUnits.isFinite() && arenaWidthUnits > 0f) {
            "arenaWidthUnits must be finite and positive: $arenaWidthUnits"
        }
        return clampMetersPerSceneUnit(targetBattleDiameterMeters(plan, planeSize) / arenaWidthUnits)
    }

    /** Clamps a metres-per-scene-unit value into the supported range (also fixes NaN). */
    fun clampMetersPerSceneUnit(value: Float): Float = when {
        !value.isFinite() -> MIN_METERS_PER_SCENE_UNIT
        else -> value.coerceIn(MIN_METERS_PER_SCENE_UNIT, MAX_METERS_PER_SCENE_UNIT)
    }

    /** Clamps a pinch factor applied to the plan value (also fixes NaN). */
    fun clampPlanScaleFactor(factor: Float): Float = when {
        !factor.isFinite() -> 1f
        else -> factor.coerceIn(MIN_PLAN_SCALE_FACTOR, MAX_PLAN_SCALE_FACTOR)
    }

    /** Arena diameter in metres described by [metersPerSceneUnit] and [arenaWidthUnits]. */
    fun battleDiameterMeters(metersPerSceneUnit: Float, arenaWidthUnits: Float): Float {
        require(metersPerSceneUnit.isFinite() && metersPerSceneUnit > 0f) {
            "metersPerSceneUnit must be finite and positive: $metersPerSceneUnit"
        }
        require(arenaWidthUnits.isFinite() && arenaWidthUnits > 0f) {
            "arenaWidthUnits must be finite and positive: $arenaWidthUnits"
        }
        return metersPerSceneUnit * arenaWidthUnits
    }

    /** Inverse of [battleDiameterMeters]: scene units covered by a real-world diameter. */
    fun sceneUnitsForBattleDiameter(diameterMeters: Float, metersPerSceneUnit: Float): Float {
        require(metersPerSceneUnit.isFinite() && metersPerSceneUnit > 0f) {
            "metersPerSceneUnit must be finite and positive: $metersPerSceneUnit"
        }
        return if (!diameterMeters.isFinite()) 0f else diameterMeters / metersPerSceneUnit
    }

    /** Real-world footprint of a tracked ARCore plane. */
    fun planeSizeOf(plane: ArPlaneSnapshot): ArPlaneSize = ArPlaneSize(
        widthMeters = abs(plane.extentX),
        depthMeters = abs(plane.extentZ),
    )

    /**
     * Yaw (radians around the plane normal) that turns the stage's local `+Z` toward the camera.
     *
     * The camera position is projected onto the placement plane, so the result is stable for the
     * horizontal surfaces ARCore reports. Returns `0` when the camera sits exactly above the hit
     * point, where "toward the camera" is undefined.
     */
    fun yawTowardCamera(hitPose: ArPose, cameraPose: ArPose): Float {
        val up = hitPose.rotateVector(floatArrayOf(0f, 1f, 0f))
        val delta = floatArrayOf(
            cameraPose.translation[0] - hitPose.translation[0],
            cameraPose.translation[1] - hitPose.translation[1],
            cameraPose.translation[2] - hitPose.translation[2],
        )
        val alongUp = delta[0] * up[0] + delta[1] * up[1] + delta[2] * up[2]
        val planar = floatArrayOf(
            delta[0] - up[0] * alongUp,
            delta[1] - up[1] * alongUp,
            delta[2] - up[2] * alongUp,
        )
        val planarLength = sqrt(planar[0] * planar[0] + planar[1] * planar[1] + planar[2] * planar[2])
        if (planarLength < 1e-5f) return 0f
        val right = hitPose.rotateVector(floatArrayOf(1f, 0f, 0f))
        val forward = hitPose.rotateVector(floatArrayOf(0f, 0f, 1f))
        val sinPart = (planar[0] * right[0] + planar[1] * right[1] + planar[2] * right[2]) / planarLength
        val cosPart = (planar[0] * forward[0] + planar[1] * forward[1] + planar[2] * forward[2]) / planarLength
        return atan2(sinPart, cosPart)
    }

    /**
     * Builds the stage frame for a tap hit test.
     *
     * The anchor sits exactly on the hit point, `+Y` follows the hit pose's `+Y` (the surface
     * normal for horizontal planes) and `+Z` is yawed toward [cameraPose] so the arena faces the
     * user. [metersPerSceneUnit] is clamped to the supported range so a bad value can never
     * create an invalid [ArPlacement].
     */
    fun makePlacement(
        hitPose: ArPose,
        cameraPose: ArPose,
        metersPerSceneUnit: Float,
    ): ArPlacement {
        val yaw = yawTowardCamera(hitPose, cameraPose)
        val anchor = ArPose(
            hitPose.translation.copyOf(),
            hitPose.rotation.copyOf(),
        ).compose(ArPose.yaw(floatArrayOf(0f, 0f, 0f), yaw))
        return ArPlacement(anchor, clampMetersPerSceneUnit(metersPerSceneUnit))
    }

    /**
     * Stage frame for hits without a plane (ARCore depth / instant placement).
     *
     * Those hit poses are camera-oriented, so using them directly leaves the arena tilted or
     * sideways. ARCore's world `+Y` is gravity aligned, so the anchor is rebuilt with world up
     * and yawed so its `+Z` faces the viewer, which keeps the battle horizontal on the surface.
     */
    fun makeGravityAlignedPlacement(
        hitTranslation: FloatArray,
        cameraPose: ArPose,
        metersPerSceneUnit: Float,
    ): ArPlacement {
        val yaw = atan2(
            cameraPose.translation[0] - hitTranslation[0],
            cameraPose.translation[2] - hitTranslation[2],
        )
        return ArPlacement(
            anchor = ArPose.yaw(hitTranslation.copyOf(), yaw),
            metersPerSceneUnit = clampMetersPerSceneUnit(metersPerSceneUnit),
        )
    }

    /** Rotates an existing placement's anchor yaw (used by the drag-to-rotate gesture). */
    fun rotatePlacementYaw(placement: ArPlacement, yawRadians: Float): ArPlacement {        if (!yawRadians.isFinite() || yawRadians == 0f) return placement
        val rotated = placement.anchor.compose(
            ArPose.yaw(floatArrayOf(0f, 0f, 0f), yawRadians),
        )
        return ArPlacement(rotated, placement.metersPerSceneUnit)
    }

    /**
     * Rough surface footprint for a depth/instant hit where ARCore has not fitted a plane.
     *
     * The tap distance is the only size signal available: something tapped from ~0.8 m away is
     * table-sized, something two metres away is room-sized. The estimate only seeds the plan
     * recommendation — the user can always override it with the scale chips.
     */
    fun estimatedPlaneSize(hitDistanceMeters: Float): ArPlaneSize {
        val distance = if (hitDistanceMeters.isFinite()) hitDistanceMeters.coerceIn(0.3f, 12f) else 2.5f
        val extent = (distance * 1.6f).coerceIn(0.6f, 12f)
        return ArPlaneSize(widthMeters = extent, depthMeters = extent)
    }

    /**
     * Manual placement used when neither planes nor depth produce a hit: the stage is placed
     * [FRONT_DISTANCE_METERS] in front of the camera, on an estimated horizontal surface
     * ([TABLE_HEIGHT_DROP_METERS] below a tabletop camera, [FLOOR_HEIGHT_DROP_METERS] below a
     * standing camera). `+Y` is world up because ARCore's world `+Y` is gravity aligned.
     */
    fun manualPlacement(
        cameraPose: ArPose,
        plan: ArBattleScalePlan,
        arenaWidthUnits: Float,
    ): ArPlacement {
        val forward = cameraPose.rotateVector(floatArrayOf(0f, 0f, -1f))
        // Estimate the surface height below the camera: a phone pointed at a table is ~0.5 m above
        // it, one pointed at the floor while standing is ~1.3 m above it. AUTO assumes a table.
        val heightDrop = when (plan) {
            ArBattleScalePlan.TABLETOP -> TABLE_HEIGHT_DROP_METERS
            ArBattleScalePlan.AUTO -> TABLE_HEIGHT_DROP_METERS
            else -> FLOOR_HEIGHT_DROP_METERS
        }
        // Follow the camera ray down to that estimated surface, so the arena appears where the
        // player is actually pointing instead of at a fixed spot that may be under the phone.
        val aimingDown = forward[1] < -0.05f
        val rayDistance = if (aimingDown) {
            (heightDrop / -forward[1]).coerceIn(MIN_MANUAL_DISTANCE_METERS, MAX_MANUAL_DISTANCE_METERS)
        } else {
            when (plan) {
                ArBattleScalePlan.TABLETOP -> 1.0f
                ArBattleScalePlan.ROOM -> 2.4f
                ArBattleScalePlan.OPEN_SPACE -> 4.0f
                ArBattleScalePlan.AUTO -> 1.6f
            }
        }
        val horizontalScale = if (aimingDown) {
            sqrt(1f - forward[1] * forward[1]).coerceAtLeast(0.1f)
        } else {
            1f
        }
        val directionX = if (horizontalScale > 1e-5f) forward[0] / horizontalScale else 0f
        val directionZ = if (horizontalScale > 1e-5f) forward[2] / horizontalScale else -1f
        val x = cameraPose.translation[0] + directionX * rayDistance * horizontalScale
        val z = cameraPose.translation[2] + directionZ * rayDistance * horizontalScale
        val y = cameraPose.translation[1] - heightDrop
        val anchorPosition = floatArrayOf(x, y, z)
        val yaw = atan2(cameraPose.translation[0] - x, cameraPose.translation[2] - z)
        val planeSize = ArPlaneSize(widthMeters = rayDistance * 1.6f, depthMeters = rayDistance * 1.6f)
        return ArPlacement(
            anchor = ArPose.yaw(anchorPosition, yaw),
            metersPerSceneUnit = metersPerSceneUnit(plan, planeSize, arenaWidthUnits),
        )
    }

    private const val TABLE_HEIGHT_DROP_METERS = 0.5f
    private const val FLOOR_HEIGHT_DROP_METERS = 1.35f
    private const val MIN_MANUAL_DISTANCE_METERS = 0.7f
    private const val MAX_MANUAL_DISTANCE_METERS = 4.5f
}
