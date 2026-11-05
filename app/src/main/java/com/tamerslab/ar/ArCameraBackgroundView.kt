package com.tamerslab.ar

import android.app.Activity
import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import timber.log.Timber

/**
 * Bottom layer of the AR battle stage: a [GLSurfaceView] that owns the ARCore session and draws
 * the camera image full screen.
 *
 * The battle scene (Filament) and the placement HUD are stacked above this view; this class never
 * creates a Filament engine and knows nothing about the battle. It publishes one
 * [ArFrameSnapshot] per ARCore update through [cameraSource] and runs [hitTester] requests on the
 * GL thread right after `Session.update()`.
 *
 * Lifecycle: [startAr] once the CAMERA permission is granted, [pauseAr]/[resumeAr] from the host
 * lifecycle, [releaseAr] when the stage leaves composition. All three are idempotent and safe to
 * call repeatedly.
 */
class ArCameraBackgroundView(context: Context) : GLSurfaceView(context) {

    private companion object {
        const val TAG = "ArCameraBackgroundView"
        const val PROJECTION_NEAR_METERS = 0.1f
        const val PROJECTION_FAR_METERS = 100f
        const val MAX_AVAILABILITY_ATTEMPTS = 5
        const val AVAILABILITY_RETRY_DELAY_MILLIS = 250L

        /** Depth/instant hits must be at least this far below the camera to count as a table/floor. */
        const val SURFACE_BELOW_CAMERA_METERS = 0.25f

        /** ~30 fps camera-background rendering, matching the ARCore camera stream. */
        const val FRAME_INTERVAL_NANOS = 33_000_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionController = ArSessionController(context)
    private val backgroundRenderer = ArBackgroundRenderer()
    private val latestSnapshot = AtomicReference<ArFrameSnapshot?>(null)
    private val pendingHitTest = AtomicReference<HitTestRequest?>(null)

    @Volatile
    private var availabilityListener: ((ArAvailability) -> Unit)? = null

    @Volatile
    private var released = false

    @Volatile
    private var sessionRunning = false

    @Volatile
    private var glSurfaceReady = false

    @Volatile
    private var cameraTextureAttached = false

    /** Caps camera-background rendering at the ARCore camera rate (~30 fps). */
    private var lastDrawNanos = 0L

    @Volatile
    private var surfaceWidth = 0

    @Volatile
    private var surfaceHeight = 0

    /** Latest ARCore frame; poll from any thread. Never throws. */
    val cameraSource: ArCameraSource = object : ArCameraSource {
        override fun latestFrame(): ArFrameSnapshot? = latestSnapshot.get()
    }

    /** Screen-space hit testing against detected horizontal planes; callbacks run on the main thread. */
    val hitTester: ArHitTester = ArHitTester { xPx, yPx, callback -> enqueueHitTest(xPx, yPx, callback) }

    private val glRenderer = object : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (released) return
            // A new EGL context invalidates every GL object name owned by the previous one, so the
            // cached program/texture ids must never be reused. Drop them (no-op on the first
            // create and harmless in a fresh context) and recreate + re-bind the camera texture.
            backgroundRenderer.release()
            backgroundRenderer.createOnGlThread()
            glSurfaceReady = backgroundRenderer.isReady
            cameraTextureAttached = false
            if (glSurfaceReady) attachCameraTexture()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0, 0, width, height)
            surfaceWidth = width
            surfaceHeight = height
            sessionController.updateDisplayGeometry(currentDisplayRotation(), width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            drawFrame()
        }
    }

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        // The ARCore camera surface must stay behind the window so the translucent Filament
        // scene stacked above it composites correctly.
        setZOrderMediaOverlay(false)
        setZOrderOnTop(false)
        setRenderer(glRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Registers (or clears) the availability observer; the current state is delivered immediately. */
    fun setAvailabilityListener(listener: ((ArAvailability) -> Unit)?) {
        availabilityListener = listener
        if (listener != null) {
            val current = sessionController.availability
            mainHandler.post { listener(current) }
        }
    }

    /** Consecutive transient `UNKNOWN` availability results; reset once the state is definitive. */
    private var availabilityAttempts = 0

    /**
     * Starts the AR session once the CAMERA permission has been granted by the host UI.
     *
     * When ARCore is missing/outdated this only publishes the availability; the host is expected
     * to call [requestInstall] and retry. A transient `UNKNOWN` result (ARCore's check can be
     * `UNKNOWN_CHECKING`/`UNKNOWN_TIMED_OUT` on a fresh process) is retried a few times before it
     * is surfaced, so a first-call transient state is not terminal. Never throws and never
     * crashes on unsupported devices.
     */
    fun startAr() {
        if (released) return
        if (!sessionController.hasCameraPermission()) {
            Timber.tag(TAG).w("startAr() ignored: CAMERA permission is not granted")
            dispatchAvailability(ArAvailability.UNKNOWN, "Camera permission not granted")
            return
        }
        if (sessionController.session != null) {
            ensureSessionRunning()
            return
        }
        checkArAvailabilityAsync(context) { availability ->
            mainHandler.post {
                if (released) return@post
                when (availability) {
                    ArAvailability.SUPPORTED -> {
                        availabilityAttempts = 0
                        createAndStartSession()
                    }

                    ArAvailability.UNKNOWN -> retryAvailabilityCheck()

                    else -> {
                        availabilityAttempts = 0
                        dispatchAvailability(availability, arAvailabilityDiagnostic(availability))
                    }
                }
            }
        }
    }

    /** Bounded retry for a transient availability `UNKNOWN`; gives up after a short window. */
    private fun retryAvailabilityCheck() {
        if (released) return
        if (sessionController.session != null) return
        availabilityAttempts++
        if (availabilityAttempts >= MAX_AVAILABILITY_ATTEMPTS) {
            availabilityAttempts = 0
            dispatchAvailability(ArAvailability.UNKNOWN, "ARCore availability check timed out")
            return
        }
        Timber.tag(TAG).i(
            "ARCore availability check returned UNKNOWN; retry %d/%d",
            availabilityAttempts,
            MAX_AVAILABILITY_ATTEMPTS,
        )
        mainHandler.postDelayed(
            { if (!released) startAr() },
            AVAILABILITY_RETRY_DELAY_MILLIS,
        )
    }

    /**
     * Requests the ARCore runtime install/update from [activity].
     *
     * @return `true` when the Play Store flow was shown and the host must retry [startAr] after
     *   `onResume`; `false` when ARCore is already installed (this call starts the session) or the
     *   request failed.
     */
    fun requestInstall(activity: Activity): Boolean {
        if (released) return false
        val installRequested = sessionController.requestInstall(activity)
        if (!installRequested) startAr()
        return installRequested
    }

    /** Pauses the AR session and the GL thread. Safe to call when already paused. */
    fun pauseAr() {
        if (released) return
        if (sessionRunning) {
            sessionRunning = false
            sessionController.pauseSession()
        }
        super.onPause()
    }

    /** Resumes the AR session and the GL thread. Safe to call when already resumed. */
    fun resumeAr() {
        if (released) return
        if (!sessionController.hasCameraPermission()) {
            dispatchAvailability(ArAvailability.UNKNOWN, "Camera permission not granted")
            super.onResume()
            return
        }
        if (sessionController.session != null && !sessionRunning) {
            if (!sessionController.resumeSession()) {
                dispatchAvailability(sessionController.availability, sessionController.failureMessage)
                super.onResume()
                return
            }
            sessionRunning = true
        }
        super.onResume()
    }

    /**
     * Restarts the ARCore camera session without destroying the view.
     *
     * If tracking stays PAUSED the session never accumulates feature points, so it never detects
     * planes; restarting resumes the camera and resets the tracking state. Safe to call from the
     * main thread and ignored while released or not yet started.
     */
    fun restartAr() {
        if (released || sessionController.session == null) return
        sessionRunning = false
        sessionController.pauseSession()
        ensureSessionRunning()
        Timber.tag(TAG).i("ARCore session restarted to recover tracking")
    }

    /**
     * Releases the session and stops the GL thread. Idempotent; call from the main thread when the
     * AR stage leaves composition or the host activity is destroyed.
     */
    fun releaseAr() {
        if (released) return
        released = true
        sessionRunning = false
        pendingHitTest.getAndSet(null)?.let { request ->
            mainHandler.post { request.callback(null) }
        }
        releaseGlResources()
        runCatching { super.onPause() }
        sessionController.closeSession()
        latestSnapshot.set(null)
        availabilityListener = null
        Timber.tag(TAG).i("AR camera background released")
    }

    /**
     * Deletes the OES texture and shader program on the GL thread before the EGL context goes
     * away. Best effort: the GL thread frees everything with the context anyway.
     */
    private fun releaseGlResources() {
        val latch = CountDownLatch(1)
        queueEvent {
            runCatching { backgroundRenderer.release() }
            latch.countDown()
        }
        if (!latch.await(500, TimeUnit.MILLISECONDS)) {
            Timber.tag(TAG).w("GL cleanup did not finish before teardown")
        }
        glSurfaceReady = false
        cameraTextureAttached = false
    }

    override fun onDetachedFromWindow() {
        releaseAr()
        super.onDetachedFromWindow()
    }

    private fun createAndStartSession() {
        if (released) return
        if (sessionController.session != null) {
            ensureSessionRunning()
            return
        }
        val created = sessionController.createSession() ?: run {
            dispatchAvailability(sessionController.availability, sessionController.failureMessage)
            return
        }
        sessionController.configureSession(created)
        cameraTextureAttached = false
        dispatchAvailability(ArAvailability.SUPPORTED)
        ensureSessionRunning()
    }

    private fun ensureSessionRunning() {
        if (released || sessionController.session == null) return
        if (!sessionRunning) {
            if (!sessionController.resumeSession()) {
                dispatchAvailability(sessionController.availability, sessionController.failureMessage)
                super.onResume()
                return
            }
            sessionRunning = true
        }
        super.onResume()
    }

    private fun drawFrame() {
        if (released) return
        // ARCore's camera runs at ~30 fps; rendering the background faster only steals GPU time
        // from the battle scene and (on mid-range phones) from ARCore's own tracking pipeline.
        val nowNanos = System.nanoTime()
        if (lastDrawNanos != 0L && nowNanos - lastDrawNanos < FRAME_INTERVAL_NANOS) return
        lastDrawNanos = nowNanos
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val session = sessionController.session ?: return
        if (!sessionRunning || !glSurfaceReady || !backgroundRenderer.isReady) return
        if (!cameraTextureAttached) attachCameraTexture()
        if (!cameraTextureAttached) return

        val displayRotation = currentDisplayRotation()
        sessionController.updateDisplayGeometry(displayRotation, surfaceWidth, surfaceHeight)

        val frame = try {
            session.update()
        } catch (error: CameraNotAvailableException) {
            onFrameUpdateFailed(error)
            return
        } catch (error: IllegalStateException) {
            onFrameUpdateFailed(error)
            return
        }

        backgroundRenderer.draw(frame)
        processHitTest(frame)
        publishSnapshot(frame, displayRotation)
    }

    /** Runs the queued screen-space hit test against the freshly updated [frame] (GL thread). */
    private fun processHitTest(frame: Frame) {
        val request = pendingHitTest.getAndSet(null) ?: return
        val result = runCatching { resolveHit(frame, request.xPx, request.yPx) }
            .onFailure { Timber.tag(TAG).w(it, "AR hit test failed") }
            .getOrNull()
        mainHandler.post { request.callback(result) }
    }

    private fun resolveHit(frame: Frame, xPx: Float, yPx: Float): ArHitTestResult? {
        val cameraY = frame.camera.pose.translation[1]
        val hits = frame.hitTest(xPx, yPx)
        // 1) A detected plane is the best result: it carries a size and a stable anchor. Prefer a
        //    pose inside the plane polygon, but accept any horizontal plane hit as a second chance
        //    (small tables often report a polygon narrower than the tested point).
        var loosePlaneHit: Plane? = null
        var loosePlanePose: com.google.ar.core.Pose? = null
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable !is Plane) continue
            if (trackable.trackingState != TrackingState.TRACKING) continue
            if (trackable.type == Plane.Type.VERTICAL) continue
            val hitPose = hit.hitPose
            if (!trackable.isPoseInPolygon(hitPose)) {
                if (loosePlaneHit == null) {
                    loosePlaneHit = trackable
                    loosePlanePose = hitPose
                }
                continue
            }
            return ArHitTestResult(
                pose = hitPose.toArPose(),
                distanceMeters = hit.distance,
                planeId = "plane-${trackable.hashCode()}",
                planeType = trackable.type.toArPlaneType(),
            )
        }
        loosePlaneHit?.let { plane ->
            loosePlanePose?.let { pose ->
                return ArHitTestResult(
                    pose = pose.toArPose(),
                    distanceMeters = 0f,
                    planeId = "plane-${plane.hashCode()}",
                    planeType = plane.type.toArPlaneType(),
                )
            }
        }
        // 2) Depth hit: ARCore estimates the physical surface from the depth map even before a
        //    plane has been fitted. Only surfaces clearly below the camera count, otherwise a wall
        //    or furniture in front of the lens would produce a mid-air arena.
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable !is DepthPoint) continue
            if (trackable.trackingState != TrackingState.TRACKING) continue
            if (cameraY - hit.hitPose.translation[1] < SURFACE_BELOW_CAMERA_METERS) continue
            return ArHitTestResult(
                pose = hit.hitPose.toArPose(),
                distanceMeters = hit.distance,
                planeId = null,
                planeType = ArPlaneType.UNKNOWN,
            )
        }
        // 3) Instant placement: a surface-less point in front of the camera that ARCore refines as
        //    tracking improves. Last resort; also requires the ray to point below the horizon.
        val instantHits = runCatching {
            frame.hitTestInstantPlacement(xPx, yPx, 1.0f)
        }.getOrDefault(emptyList())
        for (hit in instantHits) {
            val trackable = hit.trackable
            if (trackable !is InstantPlacementPoint) continue
            if (trackable.trackingState != TrackingState.TRACKING) continue
            if (cameraY - hit.hitPose.translation[1] < SURFACE_BELOW_CAMERA_METERS * 0.5f) continue
            return ArHitTestResult(
                pose = hit.hitPose.toArPose(),
                distanceMeters = hit.distance,
                planeId = null,
                planeType = ArPlaneType.UNKNOWN,
                isInstant = true,
            )
        }
        return null
    }

    private fun publishSnapshot(frame: Frame, displayRotation: Int) {
        val camera = frame.camera
        val tracking = camera.trackingState.toArTrackingState()
        val viewportWidth = if (surfaceWidth > 0) surfaceWidth else width
        val viewportHeight = if (surfaceHeight > 0) surfaceHeight else height
        val viewMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        val projectionMatrix = FloatArray(16)
        camera.getProjectionMatrix(
            projectionMatrix,
            0,
            PROJECTION_NEAR_METERS,
            PROJECTION_FAR_METERS,
        )
        val cameraFrame = ArCameraFrame(
            timestampNanos = frame.timestamp,
            tracking = tracking,
            pose = camera.pose.toArPose(),
            viewMatrix = viewMatrix,
            projectionMatrix = projectionMatrix,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            displayRotation = displayRotation,
        )
        val planes = if (tracking == ArTrackingState.STOPPED) {
            emptyList()
        } else {
            sessionController.session
                ?.getAllTrackables(Plane::class.java)
                ?.mapNotNull { plane ->
                    if (plane.trackingState != TrackingState.TRACKING) null else plane.toArPlaneSnapshot()
                }
                .orEmpty()
        }
        latestSnapshot.set(
            ArFrameSnapshot(
                camera = cameraFrame,
                planes = planes,
                tracking = tracking,
                trackingFailure = if (tracking == ArTrackingState.TRACKING) {
                    null
                } else {
                    camera.trackingFailureReason.toTrackingFailureText()
                },
                trackingFailureKind = if (tracking == ArTrackingState.TRACKING) {
                    ArTrackingFailure.NONE
                } else {
                    camera.trackingFailureReason.toArTrackingFailure()
                },
            ),
        )
    }

    private fun onFrameUpdateFailed(error: Exception) {
        Timber.tag(TAG).e(error, "ARCore frame update failed")
        sessionRunning = false
        latestSnapshot.set(
            ArFrameSnapshot(
                camera = null,
                planes = emptyList(),
                tracking = ArTrackingState.STOPPED,
                trackingFailure = "AR frame update failed: ${error.message.orEmpty()}",
                trackingFailureKind = ArTrackingFailure.UNKNOWN,
            ),
        )
    }

    /** Binds the external camera texture created by the renderer to the ARCore session (GL thread). */
    private fun attachCameraTexture() {
        val session = sessionController.session ?: return
        if (!backgroundRenderer.isReady) return
        runCatching { session.setCameraTextureName(backgroundRenderer.textureId) }
            .onSuccess {
                cameraTextureAttached = true
                Timber.tag(TAG).i("ARCore camera texture attached (id=%d)", backgroundRenderer.textureId)
            }
            .onFailure {
                cameraTextureAttached = false
                Timber.tag(TAG).e(it, "Could not attach the ARCore camera texture")
            }
    }

    private fun enqueueHitTest(xPx: Float, yPx: Float, callback: (ArHitTestResult?) -> Unit) {
        if (released || sessionController.session == null || !sessionRunning) {
            mainHandler.post { callback(null) }
            return
        }
        pendingHitTest.set(HitTestRequest(xPx, yPx, callback))
    }

    private fun dispatchAvailability(availability: ArAvailability, failureMessage: String? = null) {
        sessionController.updateAvailability(availability, failureMessage)
        if (failureMessage != null) {
            latestSnapshot.compareAndSet(
                null,
                ArFrameSnapshot(
                    camera = null,
                    planes = emptyList(),
                    tracking = ArTrackingState.STOPPED,
                    trackingFailure = failureMessage,
                    trackingFailureKind = ArTrackingFailure.UNKNOWN,
                ),
            )
        }
        val listener = availabilityListener ?: return
        val deliver = { listener(availability) }
        if (Looper.myLooper() == Looper.getMainLooper()) deliver() else mainHandler.post(deliver)
    }

    private fun currentDisplayRotation(): Int = display?.rotation ?: Surface.ROTATION_0

    private class HitTestRequest(
        val xPx: Float,
        val yPx: Float,
        val callback: (ArHitTestResult?) -> Unit,
    )
}
