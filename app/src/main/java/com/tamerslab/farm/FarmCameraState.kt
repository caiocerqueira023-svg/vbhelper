package com.tamerslab.farm

/** Vertical look-at bounds shared by every framing preset of the farm renderer. */
const val FARM_PITCH_MIN = 0.18f
const val FARM_PITCH_MAX = 1.22f

/** Orbit distance bounds shared by every framing preset of the farm renderer. */
const val FARM_DISTANCE_MIN = 1.2f
const val FARM_DISTANCE_MAX = 4.8f

/** Playable island extent, used to reject a saved camera that belongs to another scene. */
private const val FARM_TARGET_LIMIT = 8f

/**
 * Persisted orbit camera of a farm, independent of the Filament view so it can be stored, restored
 * and (later) unit-tested without a GL context (PRESENTATION_REDESIGN_PLAN §7).
 */
data class FarmCameraState(
    val yaw: Float,
    val pitch: Float,
    val distance: Float,
    val targetX: Float,
    val targetZ: Float,
) {
    /**
     * A farm row that never saved a 3D camera keeps the legacy 2D defaults (yaw/pitch 35°, distance
     * 3.4f) *or* zeroes; those are indistinguishable from garbage, so `applyCameraState` ignores
     * anything outside the legal envelope instead of clamping a wrong value into a plausible one.
     */
    fun isUsable(): Boolean =
        yaw.isFinite() && pitch.isFinite() && distance.isFinite() &&
            targetX.isFinite() && targetZ.isFinite() &&
            distance in FARM_DISTANCE_MIN..FARM_DISTANCE_MAX &&
            pitch in FARM_PITCH_MIN..FARM_PITCH_MAX &&
            kotlin.math.abs(targetX) <= FARM_TARGET_LIMIT &&
            kotlin.math.abs(targetZ) <= FARM_TARGET_LIMIT

    fun clamped(): FarmCameraState = copy(
        pitch = pitch.coerceIn(FARM_PITCH_MIN, FARM_PITCH_MAX),
        distance = distance.coerceIn(FARM_DISTANCE_MIN, FARM_DISTANCE_MAX),
        targetX = targetX.coerceIn(-FARM_TARGET_LIMIT, FARM_TARGET_LIMIT),
        targetZ = targetZ.coerceIn(-FARM_TARGET_LIMIT, FARM_TARGET_LIMIT),
    )
}
