package com.tamerslab.ar

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Filament camera derived from one ARCore camera frame, expressed in arena scene units.
 *
 * The model matrix is a rigid transform (rotation + translation) in the placement frame:
 * rotations are copied verbatim from the AR camera pose, while translations are converted from
 * metres into arena scene units by dividing by [ArPlacement.metersPerSceneUnit]. Filament cameras
 * look down their local `-Z` axis, exactly like ARCore cameras, so no extra rotation is needed.
 */
data class ArSceneCamera(
    /** Column-major rigid transform of the camera in arena scene units. */
    val modelMatrix: FloatArray,
    /** Full vertical field of view in degrees, taken from the ARCore projection. */
    val fovYDegrees: Float,
    /** Viewport aspect ratio (width / height) of the ARCore camera image. */
    val aspect: Float,
    /** Near plane distance in arena scene units. */
    val nearSceneUnits: Float,
    /** Far plane distance in arena scene units. */
    val farSceneUnits: Float,
)

/**
 * Converts an ARCore camera pose into the battle arena's scene units.
 *
 * `sceneCamera = inverse(anchor) * cameraPose`, then the translation component is divided by the
 * placement scale. Rotations stay identical, so the device orientation maps one-to-one onto the
 * Filament camera orientation.
 */
fun arSceneCamera(frame: ArCameraFrame, placement: ArPlacement): ArSceneCamera {
    val relative = placement.anchor.inverse().compose(frame.pose)
    val scale = 1f / placement.metersPerSceneUnit
    val sceneTranslation = floatArrayOf(
        relative.translation[0] * scale,
        relative.translation[1] * scale,
        relative.translation[2] * scale,
    )
    val modelMatrix = ArPose(sceneTranslation, relative.rotation).matrix16()
    val fovYDegrees = ArMath.fovYFromProjection(frame.projectionMatrix)
    val aspect = ArMath.aspectRatio(frame.viewportWidth, frame.viewportHeight)
    val (nearSceneUnits, farSceneUnits) = ArMath.sceneClipPlanes(placement.metersPerSceneUnit)
    return ArSceneCamera(
        modelMatrix = modelMatrix,
        fovYDegrees = fovYDegrees,
        aspect = aspect,
        nearSceneUnits = nearSceneUnits,
        farSceneUnits = farSceneUnits,
    )
}

/**
 * Yaw of the AR scene camera in radians, in the same convention as the battle camera:
 * `0` means the camera sits behind the stage origin looking toward `-Z` (the placement frame's
 * +Z points at the viewer, so the camera is on the +Z side); positive angles move the camera
 * toward +X.
 *
 * The yaw is taken from the camera's backward axis (the third column of the model matrix), which
 * is the direction pointing from the look target toward the eye. That matches the arena's
 * `cameraYaw` (`atan2(eyeX - targetX, eyeZ - targetZ)`) and keeps billboarded fighters and
 * effects facing the physical device camera.
 */
fun arSceneCameraYaw(camera: ArSceneCamera): Float {
    val m = camera.modelMatrix
    return atan2(m[8], m[10])
}

/**
 * Pitch of the AR scene camera in radians, in the same convention as the battle camera:
 * positive when the camera looks down on the stage (the device is above the anchor).
 */
fun arSceneCameraPitch(camera: ArSceneCamera): Float {
    val m = camera.modelMatrix
    return atan2(m[9], hypot(m[8], m[10]))
}

/** Camera eye position in arena scene units (translation of the model matrix). */
fun arSceneCameraEye(camera: ArSceneCamera): FloatArray {
    val m = camera.modelMatrix
    return floatArrayOf(m[12], m[13], m[14])
}
