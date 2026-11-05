package com.tamerslab.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import timber.log.Timber

/**
 * ARCore availability mapping shared by the AR stage and the debug diagnostics tool.
 *
 * ARCore is an optional presentation layer: every helper here is best-effort and must never
 * throw into UI code. Failures are logged and mapped to [ArAvailability] so the radar battle
 * can fall back to the Coliseum renderer.
 */

private const val TAG = "ArAvailability"

/** Maps the detailed ARCore availability state onto the app-level [ArAvailability] contract. */
internal fun ArCoreApk.Availability.toArAvailability(): ArAvailability = when {
    this == ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailability.SUPPORTED
    isSupported -> ArAvailability.SUPPORTED_NOT_INSTALLED
    isUnsupported -> ArAvailability.UNSUPPORTED
    else -> ArAvailability.UNKNOWN
}

/** True when the app holds the CAMERA permission; ARCore sessions require it to be granted. */
fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Synchronous availability check. Prefer [checkArAvailabilityAsync] on the UI thread: the first
 * call may perform a (brief) blocking package-manager query on some devices.
 */
fun checkArAvailability(context: Context): ArAvailability = runCatching {
    ArCoreApk.getInstance().checkAvailability(context).toArAvailability()
}.onFailure {
    Timber.tag(TAG).w(it, "ARCore availability check failed")
}.getOrDefault(ArAvailability.UNKNOWN)

/**
 * Asynchronous availability check. The callback is invoked once the check completes and may run
 * on a background thread, so callers that touch UI must marshal it themselves.
 */
fun checkArAvailabilityAsync(
    context: Context,
    onResult: (ArAvailability) -> Unit,
) {
    runCatching {
        ArCoreApk.getInstance().checkAvailabilityAsync(context) { availability ->
            onResult(availability.toArAvailability())
        }
    }.onFailure {
        Timber.tag(TAG).w(it, "ARCore availability check failed")
        onResult(ArAvailability.UNKNOWN)
    }
}

/**
 * True when this device can start an ARCore session right now: ARCore is installed, the device
 * is supported and the CAMERA permission has already been granted.
 */
fun isArCoreReady(context: Context): Boolean =
    hasCameraPermission(context) && checkArAvailability(context) == ArAvailability.SUPPORTED

/**
 * Asks ARCore to install/update its runtime, showing the Play Store UI when [userRequestedInstall]
 * is true.
 *
 * @return `true` when an installation was requested and the caller must wait for it to finish
 *   (re-check on `onResume`); `false` when ARCore is already installed or the request failed.
 */
fun requestArInstall(activity: Activity, userRequestedInstall: Boolean = true): Boolean = runCatching {
    ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall) ==
        ArCoreApk.InstallStatus.INSTALL_REQUESTED
}.onFailure { error ->
    when (error) {
        is UnavailableUserDeclinedInstallationException ->
            Timber.tag(TAG).i("User declined the ARCore installation")
        is UnavailableDeviceNotCompatibleException ->
            Timber.tag(TAG).w("This device cannot install ARCore")
        else -> Timber.tag(TAG).e(error, "ARCore installation request failed")
    }
}.getOrDefault(false)

/** Maps a session-creation exception to the app-level availability contract. */
internal fun arAvailabilityFromError(error: Throwable): ArAvailability = when (error) {
    is UnavailableArcoreNotInstalledException,
    is UnavailableApkTooOldException,
    -> ArAvailability.SUPPORTED_NOT_INSTALLED

    is UnavailableSdkTooOldException,
    is UnavailableDeviceNotCompatibleException,
    -> ArAvailability.UNSUPPORTED

    else -> ArAvailability.UNKNOWN
}

/** Short English diagnostic text for an availability state (UI owns localized copy). */
internal fun arAvailabilityDiagnostic(availability: ArAvailability): String = when (availability) {
    ArAvailability.SUPPORTED -> "ARCore is ready"
    ArAvailability.SUPPORTED_NOT_INSTALLED -> "ARCore is supported but not installed"
    ArAvailability.UNSUPPORTED -> "This device does not support ARCore"
    ArAvailability.UNKNOWN -> "ARCore availability is unknown"
}
