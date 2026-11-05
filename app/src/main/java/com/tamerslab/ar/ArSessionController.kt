package com.tamerslab.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import timber.log.Timber

/**
 * Owns the single ARCore [Session] used by the whole AR encounter.
 *
 * Session creation/configuration/resume/pause happen on the caller's (main) thread, exactly like
 * the ARCore samples. Frame updates run on the GL thread inside [ArBackgroundRenderer]'s owner;
 * this controller only guards the session and its display geometry.
 */
internal class ArSessionController(private val context: Context) {

    @Volatile
    var session: Session? = null
        private set

    /** Last known availability, updated by the background view as checks/session creation finish. */
    var availability: ArAvailability = ArAvailability.UNKNOWN
        private set

    /** Last diagnostic failure message (English; UI owns localized copy). */
    var failureMessage: String? = null
        private set

    private var lastDisplayRotation = -1
    private var lastDisplayWidth = -1
    private var lastDisplayHeight = -1

    fun hasCameraPermission(): Boolean = hasCameraPermission(context)

    fun requestInstall(activity: Activity, userRequestedInstall: Boolean = true): Boolean =
        requestArInstall(activity, userRequestedInstall)

    /** Records the latest availability/failure state and forwards it to the UI listener. */
    fun updateAvailability(availability: ArAvailability, failureMessage: String? = null) {
        this.availability = availability
        this.failureMessage = failureMessage
    }

    /**
     * Creates (or returns the existing) ARCore session. Returns `null` and records an
     * [ArAvailability] when the device cannot start a session. Never throws.
     */
    fun createSession(): Session? {
        session?.let { return it }
        if (!hasCameraPermission()) {
            updateAvailability(ArAvailability.UNKNOWN, "Camera permission not granted")
            Timber.tag(TAG).w("ARCore session not created: CAMERA permission not granted")
            return null
        }
        val created = try {
            Session(context)
        } catch (error: UnavailableArcoreNotInstalledException) {
            return fail(ArAvailability.SUPPORTED_NOT_INSTALLED, "ARCore is not installed", error)
        } catch (error: UnavailableApkTooOldException) {
            return fail(ArAvailability.SUPPORTED_NOT_INSTALLED, "ARCore is out of date", error)
        } catch (error: UnavailableSdkTooOldException) {
            return fail(ArAvailability.UNSUPPORTED, "The app is too old for this ARCore version", error)
        } catch (error: UnavailableDeviceNotCompatibleException) {
            return fail(ArAvailability.UNSUPPORTED, "This device does not support ARCore", error)
        } catch (error: Exception) {
            return fail(ArAvailability.UNKNOWN, "Could not start the ARCore session", error)
        }
        session = created
        lastDisplayRotation = -1
        lastDisplayWidth = -1
        lastDisplayHeight = -1
        updateAvailability(ArAvailability.SUPPORTED, null)
        Timber.tag(TAG).i("ARCore session created")
        return created
    }

    /** Applies the battle-mode configuration; safe to call once right after [createSession]. */
    fun configureSession(target: Session? = session) {
        val session = target ?: return
        val config = Config(session).apply {
            setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL)
            setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY)
            setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE)
            setFocusMode(Config.FocusMode.AUTO)
        }
        // Depth gives us surface hits even when no plane has been fitted yet (low-texture tables,
        // dark floors, glass, ...). Without it, placement would be blocked until ARCore snaps a
        // plane, which is the most common reason "no surface detected" happens in real rooms.
        if (runCatching { session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) }.getOrDefault(false)) {
            runCatching { config.setDepthMode(Config.DepthMode.AUTOMATIC) }
                .onSuccess { Timber.tag(TAG).i("ARCore depth mode enabled") }
                .onFailure { Timber.tag(TAG).w(it, "Could not enable ARCore depth mode") }
        } else {
            // Visible in logcat even without a Timber tree (debug activities plant one).
            android.util.Log.i(TAG, "ARCore depth mode not supported on this device")
        }
        // Last-resort placement ray in front of the camera while depth/planes are warming up.
        runCatching { config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP) }
            .onFailure { Timber.tag(TAG).w(it, "Could not enable ARCore instant placement") }
        runCatching { session.configure(config) }
            .onSuccess { Timber.tag(TAG).i("ARCore session configured") }
            .onFailure {
                Timber.tag(TAG).e(it, "Could not configure the ARCore session")
                failureMessage = "Could not configure ARCore: ${it.message.orEmpty()}"
            }
    }

    /** Resumes the camera; returns `false` (and records a failure) when the camera is unavailable. */
    fun resumeSession(): Boolean {
        val target = session ?: return false
        return try {
            target.resume()
            Timber.tag(TAG).i("ARCore session resumed")
            failureMessage = null
            true
        } catch (error: CameraNotAvailableException) {
            Timber.tag(TAG).e(error, "Camera not available for the ARCore session")
            failureMessage = "Camera unavailable: ${error.message.orEmpty()}"
            false
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Could not resume the ARCore session")
            failureMessage = "Could not resume ARCore: ${error.message.orEmpty()}"
            false
        }
    }

    fun pauseSession() {
        val target = session ?: return
        runCatching { target.pause() }
            .onFailure { Timber.tag(TAG).w(it, "Could not pause the ARCore session") }
    }

    /** Idempotent teardown: pauses and closes the session. */
    fun closeSession() {
        val target = session ?: return
        session = null
        runCatching { target.pause() }
            .onFailure { Timber.tag(TAG).w(it, "Could not pause the ARCore session before closing") }
        runCatching { target.close() }
            .onFailure { Timber.tag(TAG).w(it, "Could not close the ARCore session") }
        lastDisplayRotation = -1
        lastDisplayWidth = -1
        lastDisplayHeight = -1
        Timber.tag(TAG).i("ARCore session closed")
    }

    /**
     * Tells ARCore about the current display geometry. Must run before [update][Session.update]
     * whenever the surface size or the display rotation changes; cheap when nothing changed.
     * Called from the GL thread.
     */
    fun updateDisplayGeometry(displayRotation: Int, width: Int, height: Int) {
        val target = session ?: return
        if (width <= 0 || height <= 0) return
        if (displayRotation == lastDisplayRotation && width == lastDisplayWidth && height == lastDisplayHeight) {
            return
        }
        lastDisplayRotation = displayRotation
        lastDisplayWidth = width
        lastDisplayHeight = height
        runCatching { target.setDisplayGeometry(displayRotation, width, height) }
            .onFailure { Timber.tag(TAG).w(it, "Could not update the ARCore display geometry") }
    }

    private fun fail(availability: ArAvailability, message: String, error: Throwable): Session? {
        updateAvailability(availability, message)
        Timber.tag(TAG).e(error, "%s", message)
        return null
    }

    private companion object {
        const val TAG = "ArSession"
    }
}

internal fun TrackingState.toArTrackingState(): ArTrackingState = when (this) {
    TrackingState.TRACKING -> ArTrackingState.TRACKING
    TrackingState.PAUSED -> ArTrackingState.PAUSED
    TrackingState.STOPPED -> ArTrackingState.STOPPED
}

internal fun Pose.toArPose(): ArPose = ArPose(getTranslation(), getRotationQuaternion())

internal fun Plane.Type.toArPlaneType(): ArPlaneType = when (this) {
    Plane.Type.HORIZONTAL_UPWARD_FACING -> ArPlaneType.HORIZONTAL_UPWARD
    Plane.Type.HORIZONTAL_DOWNWARD_FACING -> ArPlaneType.HORIZONTAL_DOWNWARD
    Plane.Type.VERTICAL -> ArPlaneType.VERTICAL
}

/** Snapshot conversion; the plane polygon keeps its plane-local `(x, z)` pairs. */
internal fun Plane.toArPlaneSnapshot(): ArPlaneSnapshot {
    val polygonBuffer = getPolygon()
    val polygon = FloatArray(polygonBuffer.remaining()).also { polygonBuffer.get(it) }
    return ArPlaneSnapshot(
        id = "plane-${hashCode()}",
        type = getType().toArPlaneType(),
        tracking = getTrackingState().toArTrackingState(),
        centerPose = getCenterPose().toArPose(),
        extentX = getExtentX(),
        extentZ = getExtentZ(),
        polygon = polygon,
    )
}

/** Short English text for a tracking failure; `null` while ARCore tracks normally. */
internal fun TrackingFailureReason.toTrackingFailureText(): String? = when (this) {
    TrackingFailureReason.NONE -> null
    TrackingFailureReason.BAD_STATE -> "ARCore internal error; restart the AR session"
    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark; move to a brighter area"
    TrackingFailureReason.EXCESSIVE_MOTION -> "Move the device more slowly"
    TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a textured surface (floor or table)"
    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
}

/** Localizable reason for the scanner/HUD; unknown codes fall back to a generic message. */
internal fun TrackingFailureReason.toArTrackingFailure(): ArTrackingFailure = when (this) {
    TrackingFailureReason.NONE -> ArTrackingFailure.NONE
    TrackingFailureReason.BAD_STATE -> ArTrackingFailure.BAD_STATE
    TrackingFailureReason.INSUFFICIENT_LIGHT -> ArTrackingFailure.INSUFFICIENT_LIGHT
    TrackingFailureReason.EXCESSIVE_MOTION -> ArTrackingFailure.EXCESSIVE_MOTION
    TrackingFailureReason.INSUFFICIENT_FEATURES -> ArTrackingFailure.INSUFFICIENT_FEATURES
    TrackingFailureReason.CAMERA_UNAVAILABLE -> ArTrackingFailure.CAMERA_UNAVAILABLE
}
