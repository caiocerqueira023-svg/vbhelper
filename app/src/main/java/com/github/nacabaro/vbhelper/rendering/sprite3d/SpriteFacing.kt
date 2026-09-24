package com.github.nacabaro.vbhelper.rendering.sprite3d

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Keeps an extruded sprite readable without making it rigidly follow the camera.
 * The sprite flips directly to its equivalent face after crossing a profile view,
 * while never exposing more than the configured amount of its thin side.
 */
fun cameraAssistedSpriteYaw(
    worldHeadingYaw: Float,
    cameraYaw: Float,
    maxRelativeYaw: Float = MAX_CAMERA_ASSISTED_RELATIVE_YAW,
): Float {
    require(maxRelativeYaw in 0f..MAX_PROFILE_YAW)
    val relativeHeading = worldHeadingYaw - cameraYaw
    val nearestPlaneHeading = atan2(
        sin(relativeHeading * 2f),
        cos(relativeHeading * 2f),
    ) / 2f
    return cameraYaw + nearestPlaneHeading.coerceIn(-maxRelativeYaw, maxRelativeYaw)
}

const val MAX_CAMERA_ASSISTED_RELATIVE_YAW = 0.6981317f
private const val MAX_PROFILE_YAW = 1.5707964f
