package com.tamerslab.ar

import kotlin.math.abs

/**
 * Shared AR contracts between the ARCore session layer, the battle scene and the placement UI.
 *
 * Everything here is plain Kotlin/JVM data so the maths can be unit-tested without an
 * Android device or the ARCore native runtime. ARCore `Pose`/`Frame` objects are converted at
 * the session boundary and never leak into scene code.
 */

enum class ArTrackingState { TRACKING, PAUSED, STOPPED }

enum class ArPlaneType { HORIZONTAL_UPWARD, HORIZONTAL_DOWNWARD, VERTICAL, UNKNOWN }

/** Why ARCore is not tracking (mirrors `TrackingFailureReason`, kept Android-free for the UI). */
enum class ArTrackingFailure {
    NONE,
    BAD_STATE,
    INSUFFICIENT_LIGHT,
    EXCESSIVE_MOTION,
    INSUFFICIENT_FEATURES,
    CAMERA_UNAVAILABLE,
    UNKNOWN,
}

enum class ArAvailability {
    /** ARCore is installed and the device can run an AR session. */
    SUPPORTED,

    /** Device is ARCore-capable but the ARCore APK must be installed/updated. */
    SUPPORTED_NOT_INSTALLED,

    /** Device cannot run ARCore at all. */
    UNSUPPORTED,

    /** Check has not completed yet, or it failed for a transient reason. */
    UNKNOWN,
}

/**
 * Rigid transform in AR world space: `translation` is a 3-float position and `rotation` is a
 * quaternion `(x, y, z, w)`, both expressed as plain arrays so poses stay cheap and copyable
 * across the GL thread and the Compose main thread.
 */
class ArPose(
    val translation: FloatArray,
    val rotation: FloatArray,
) {
    init {
        require(translation.size == 3) { "translation must have 3 components" }
        require(rotation.size == 4) { "rotation must have 4 components" }
    }

    /** Column-major 4x4 transform (Android/Filament matrix convention). */
    fun matrix16(): FloatArray {
        val x = rotation[0]
        val y = rotation[1]
        val z = rotation[2]
        val w = rotation[3]
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z
        return floatArrayOf(
            // column 0
            1f - 2f * (yy + zz), 2f * (xy + wz), 2f * (xz - wy), 0f,
            // column 1
            2f * (xy - wz), 1f - 2f * (xx + zz), 2f * (yz + wx), 0f,
            // column 2
            2f * (xz + wy), 2f * (yz - wx), 1f - 2f * (xx + yy), 0f,
            // column 3
            translation[0], translation[1], translation[2], 1f,
        )
    }

    fun rotateVector(v: FloatArray): FloatArray {
        require(v.size == 3)
        val x = rotation[0]
        val y = rotation[1]
        val z = rotation[2]
        val w = rotation[3]
        // t = 2 * cross(q.xyz, v)
        val tx = 2f * (y * v[2] - z * v[1])
        val ty = 2f * (z * v[0] - x * v[2])
        val tz = 2f * (x * v[1] - y * v[0])
        return floatArrayOf(
            v[0] + w * tx + (y * tz - z * ty),
            v[1] + w * ty + (z * tx - x * tz),
            v[2] + w * tz + (x * ty - y * tx),
        )
    }

    fun transformPoint(point: FloatArray): FloatArray {
        val r = rotateVector(point)
        return floatArrayOf(
            r[0] + translation[0],
            r[1] + translation[1],
            r[2] + translation[2],
        )
    }

    fun inverse(): ArPose {
        val conjugate = floatArrayOf(-rotation[0], -rotation[1], -rotation[2], rotation[3])
        val inverseRotation = ArPose(floatArrayOf(0f, 0f, 0f), conjugate)
        val rotated = inverseRotation.rotateVector(translation)
        return ArPose(
            floatArrayOf(-rotated[0], -rotated[1], -rotated[2]),
            conjugate,
        )
    }

    /** `this * other`: applies [other] first, then this. */
    fun compose(other: ArPose): ArPose {
        val ax = rotation[0]
        val ay = rotation[1]
        val az = rotation[2]
        val aw = rotation[3]
        val bx = other.rotation[0]
        val by = other.rotation[1]
        val bz = other.rotation[2]
        val bw = other.rotation[3]
        val rotated = rotateVector(other.translation)
        return ArPose(
            floatArrayOf(
                rotated[0] + translation[0],
                rotated[1] + translation[1],
                rotated[2] + translation[2],
            ),
            floatArrayOf(
                aw * bx + ax * bw + ay * bz - az * by,
                aw * by - ax * bz + ay * bw + az * bx,
                aw * bz + ax * by - ay * bx + az * bw,
                aw * bw - ax * bx - ay * by - az * bz,
            ),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArPose) return false
        return translation.contentEquals(other.translation) && rotation.contentEquals(other.rotation)
    }

    override fun hashCode(): Int = 31 * translation.contentHashCode() + rotation.contentHashCode()

    override fun toString(): String = "ArPose(t=${translation.toList()}, q=${rotation.toList()})"

    companion object {
        val IDENTITY: ArPose = ArPose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))

        fun translation(x: Float, y: Float, z: Float): ArPose =
            ArPose(floatArrayOf(x, y, z), floatArrayOf(0f, 0f, 0f, 1f))

        /** Yaw-only pose, +Z axis pointing along `(sin(yaw), 0, cos(yaw))`. */
        fun yaw(position: FloatArray, yawRadians: Float): ArPose = ArPose(
            position.copyOf(),
            floatArrayOf(0f, kotlin.math.sin(yawRadians / 2f), 0f, kotlin.math.cos(yawRadians / 2f)),
        )
    }
}

/** One ARCore camera frame, converted to immutable data for consumers on any thread. */
class ArCameraFrame(
    val timestampNanos: Long,
    val tracking: ArTrackingState,
    /** Camera pose in AR world space. */
    val pose: ArPose,
    /** World -> camera, column-major. */
    val viewMatrix: FloatArray,
    /** Camera projection, column-major, near/far used by ARCore when it was queried. */
    val projectionMatrix: FloatArray,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val displayRotation: Int,
) {
    override fun toString(): String =
        "ArCameraFrame(tracking=$tracking, ${viewportWidth}x$viewportHeight, rot=$displayRotation)"
}

/** Snapshot of a tracked ARCore plane; [polygon] holds plane-local `(x, z)` pairs. */
class ArPlaneSnapshot(
    val id: String,
    val type: ArPlaneType,
    val tracking: ArTrackingState,
    val centerPose: ArPose,
    val extentX: Float,
    val extentZ: Float,
    val polygon: FloatArray,
) {
    val isHorizontal: Boolean
        get() = type == ArPlaneType.HORIZONTAL_UPWARD || type == ArPlaneType.HORIZONTAL_DOWNWARD
}

/** A hit test result on a detected surface (plane, ARCore depth point or instant placement). */
class ArHitTestResult(
    val pose: ArPose,
    val distanceMeters: Float,
    val planeId: String?,
    val planeType: ArPlaneType,
    /** True for the surface-less instant-placement fallback; used to delay auto-confirmation. */
    val isInstant: Boolean = false,
)

/** Everything the AR consumers need from one ARCore update. */
class ArFrameSnapshot(
    val camera: ArCameraFrame?,
    val planes: List<ArPlaneSnapshot>,
    val tracking: ArTrackingState,
    val trackingFailure: String? = null,
    val trackingFailureKind: ArTrackingFailure = ArTrackingFailure.NONE,
)

/** Latest-frame access; implemented by the AR camera view, polled by scene/UI. */
interface ArCameraSource {
    fun latestFrame(): ArFrameSnapshot?
}

/** Screen-space hit test against detected planes. Callbacks are delivered on the main thread. */
fun interface ArHitTester {
    fun hitTest(xPx: Float, yPx: Float, callback: (ArHitTestResult?) -> Unit)
}

/**
 * Placement of the battle stage in AR world space.
 *
 * [anchor] is the stage frame: origin on the chosen surface, +Y along the surface normal and
 * +Z pointing toward the viewer at placement time. [metersPerSceneUnit] converts arena scene
 * units to real metres (tabletop ≈ 0.05, open space ≈ 0.5+).
 */
class ArPlacement(
    val anchor: ArPose,
    val metersPerSceneUnit: Float,
) {
    init {
        require(metersPerSceneUnit.isFinite() && metersPerSceneUnit > 0f) {
            "metersPerSceneUnit must be finite and positive"
        }
    }

    override fun toString(): String = "ArPlacement(anchor=$anchor, m/unit=$metersPerSceneUnit)"
}

internal fun approxEquals(a: Float, b: Float, epsilon: Float = 1e-4f): Boolean = abs(a - b) <= epsilon
