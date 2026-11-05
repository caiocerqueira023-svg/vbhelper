package com.tamerslab.ar

import android.Manifest
import android.content.Context
import android.opengl.Matrix
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.View.BlendMode
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.tamerslab.rendering.sprite3d.ResidentFrameImage
import com.tamerslab.rendering.sprite3d.SpriteExtrusionGlb
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Debug-only ARCore smoke test and compositing probe for the AR battle stage.
 *
 * Launches full screen with three stacked layers:
 *  1. [ArCameraBackgroundView] (`GLSurfaceView`, camera feed through an external OES texture),
 *  2. a transparent Filament `TextureView` showing a generated magenta sprite (the same
 *     translucent-over-surface compositing the battle stage depends on),
 *  3. a Compose HUD with tracking/plane diagnostics, projected plane outlines and a tap marker.
 *
 * Not part of the release source set; the manifest entry only exists in `src/debug`.
 */
class ArDiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        setContent { ArDiagnosticsScreen() }
    }
}

private val HudBackground = Color(0x99000000)
private val HudText = Color(0xFFF5F5F5)
private val PlaneOutline = Color(0xFF00E5FF)
private val HitMarker = Color(0xFFFF5252)

/**
 * Which Compose layer is stacked over the camera `SurfaceView`, to prove (or disprove) that
 * Compose content composites above the AR camera feed:
 *  - [NONE]: camera feed + the small Compose HUD only (baseline),
 *  - [HALF_RED]: a full-screen 50% alpha red Compose box (the camera must stay visible, tinted),
 *  - [TRANSPARENT_BOX]: a full-screen transparent Compose box plus the HUD.
 */
private enum class StackingProbe { NONE, HALF_RED, TRANSPARENT_BOX }

@Composable
private fun ArDiagnosticsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraView = remember { ArCameraBackgroundView(context) }
    val overlayView = remember { ArDiagnosticsFilamentOverlay(context) }

    var permissionGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    var availability by remember { mutableStateOf(ArAvailability.UNKNOWN) }
    var snapshot by remember { mutableStateOf<ArFrameSnapshot?>(null) }
    var hit by remember { mutableStateOf<ArHitTestResult?>(null) }
    var status by remember { mutableStateOf("Waiting for camera") }
    var showSpriteOverlay by remember { mutableStateOf(true) }
    var stackingProbe by remember { mutableStateOf(StackingProbe.NONE) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        status = if (granted) "Camera permission granted" else "Camera permission denied"
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, cameraView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> cameraView.resumeAr()
                Lifecycle.Event.ON_PAUSE -> cameraView.pauseAr()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraView.releaseAr()
            overlayView.releaseScene()
        }
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        cameraView.setAvailabilityListener { availability = it }
        cameraView.startAr()
    }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = cameraView.cameraSource.latestFrame()
            delay(66)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { cameraView },
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { overlayView },
            update = { view -> view.visibility = if (showSpriteOverlay) View.VISIBLE else View.GONE },
        )
        // Compose-over-camera stacking probe (see StackingProbe).
        when (stackingProbe) {
            StackingProbe.NONE -> Unit
            StackingProbe.HALF_RED -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.5f)),
            )

            StackingProbe.TRANSPARENT_BOX -> Box(Modifier.fillMaxSize())
        }
        DiagnosticsCanvas(
            snapshot = snapshot,
            hit = hit,
            onTap = { x, y ->
                cameraView.hitTester.hitTest(x, y) { result ->
                    hit = result
                    status = if (result == null) {
                        "Tap: no horizontal plane hit"
                    } else {
                        "Tap: hit ${result.planeType} at " +
                            "${(result.distanceMeters * 100).roundToInt() / 100f} m"
                    }
                }
            },
        )
        Hud(
            availability = availability,
            snapshot = snapshot,
            hit = hit,
            status = status,
            permissionGranted = permissionGranted,
            showSpriteOverlay = showSpriteOverlay,
            onToggleOverlay = { showSpriteOverlay = !showSpriteOverlay },
            stackingProbe = stackingProbe,
            onCycleStackingProbe = {
                stackingProbe = StackingProbe.values()[
                    (stackingProbe.ordinal + 1) % StackingProbe.values().size,
                ]
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun Hud(
    availability: ArAvailability,
    snapshot: ArFrameSnapshot?,
    hit: ArHitTestResult?,
    status: String,
    permissionGranted: Boolean,
    showSpriteOverlay: Boolean,
    onToggleOverlay: () -> Unit,
    stackingProbe: StackingProbe,
    onCycleStackingProbe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera = snapshot?.camera
    val planeCount = snapshot?.planes?.count { it.isHorizontal } ?: 0
    Column(
        modifier = modifier
            .background(HudBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("ARCore smoke test", color = HudText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("availability: $availability", color = HudText, fontSize = 12.sp)
        Text("camera permission: $permissionGranted", color = HudText, fontSize = 12.sp)
        Text("tracking: ${snapshot?.tracking ?: ArTrackingState.STOPPED}", color = HudText, fontSize = 12.sp)
        Text("horizontal planes: $planeCount", color = HudText, fontSize = 12.sp)
        Text(
            "viewport: ${camera?.viewportWidth ?: 0}x${camera?.viewportHeight ?: 0} " +
                "rot=${camera?.displayRotation ?: 0}",
            color = HudText,
            fontSize = 12.sp,
        )
        snapshot?.trackingFailure?.let { Text("failure: $it", color = Color(0xFFFFB74D), fontSize = 12.sp) }
        Text(
            "hit: " + (hit?.let { "${it.planeType} ${(it.distanceMeters * 100).roundToInt() / 100f} m" }
                ?: "none"),
            color = HudText,
            fontSize = 12.sp,
        )
        Text(status, color = Color(0xFF80D8FF), fontSize = 12.sp)
        Text(
            if (showSpriteOverlay) "sprite overlay: ON (tap to hide)" else "sprite overlay: OFF (tap to show)",
            color = Color(0xFFFFD54F),
            fontSize = 12.sp,
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures { onToggleOverlay() } },
        )
        Text(
            "stacking probe: $stackingProbe (tap to cycle)",
            color = Color(0xFFB39DDB),
            fontSize = 12.sp,
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures { onCycleStackingProbe() } },
        )
        Text(
            "tap the camera feed to hit-test a horizontal plane",
            color = HudText,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DiagnosticsCanvas(
    snapshot: ArFrameSnapshot?,
    hit: ArHitTestResult?,
    onTap: (Float, Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset -> onTap(offset.x, offset.y) }
            },
    ) {
        val camera = snapshot?.camera ?: return@Canvas
        if (camera.viewportWidth <= 0 || camera.viewportHeight <= 0) return@Canvas
        val viewProjection = ArMath.multiply(camera.projectionMatrix, camera.viewMatrix)
        val widthPx = size.width.toInt()
        val heightPx = size.height.toInt()
        snapshot.planes.filter { it.isHorizontal }.forEach { plane ->
            val polygon = plane.polygon
            if (polygon.size < 6) return@forEach
            val path = Path()
            var started = false
            var index = 0
            while (index + 1 < polygon.size) {
                val world = ArMath.planePointToWorld(plane, polygon[index], polygon[index + 1])
                val projected = ArMath.projectToViewport(
                    viewProjection,
                    world,
                    widthPx,
                    heightPx,
                    padding = 0.5f,
                )
                if (projected != null) {
                    if (started) path.lineTo(projected[0], projected[1]) else path.moveTo(projected[0], projected[1])
                    started = true
                }
                index += 2
            }
            if (started) {
                path.close()
                drawPath(path, color = PlaneOutline, style = Stroke(width = 3f))
            }
        }
        hit?.let { result ->
            val projected = ArMath.projectToViewport(
                viewProjection,
                result.pose.translation,
                widthPx,
                heightPx,
                padding = 0.5f,
            )
            if (projected != null) {
                val center = Offset(projected[0], projected[1])
                drawCircle(color = HitMarker, radius = 14f, center = center, style = Stroke(width = 3f))
                drawLine(HitMarker, center - Offset(26f, 0f), center + Offset(26f, 0f), strokeWidth = 3f)
                drawLine(HitMarker, center - Offset(0f, 26f), center + Offset(0f, 26f), strokeWidth = 3f)
            }
        }
    }
}

/**
 * A transparent Filament `TextureView` used to prove that the battle scene can composite over the
 * camera `SurfaceView`. The geometry comes from the existing sprite GLB pipeline (no new assets):
 * a generated magenta square with a white border, four poses named like a resident sprite so
 * [SpriteExtrusionGlb] produces its usual shallow 3D solid.
 */
private class ArDiagnosticsFilamentOverlay(context: Context) : android.view.TextureView(context) {

    private val engine: Engine
    private var viewer: ModelViewer? = null
    private val worker = Executors.newSingleThreadExecutor()
    private var released = false
    private var loaded = false
    private var lastFrameNanos = 0L

    /**
     * Hosts the detached ModelViewer on a `CONFIG_TRANSPARENT` swap chain so this TextureView
     * carries alpha and the AR camera feed shows through (see [ArTransparentSwapChainInstaller]).
     */
    private var transparentSwapChains: ArTransparentSwapChainInstaller? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (released) return
            if (frameTimeNanos - lastFrameNanos >= 16_666_666L) {
                lastFrameNanos = frameTimeNanos
                runCatching { renderFrame(frameTimeNanos) }
                    .onFailure { Timber.tag(TAG).e(it, "Filament overlay render failed") }
            }
            if (!released) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        Filament.init()
        System.loadLibrary("gltfio-jni")
        engine = Engine.create()
        isOpaque = false
        // Detached viewer: ModelViewer must NOT attach itself to this TextureView (it would create
        // an opaque swap chain, and a second EGL window surface on the same SurfaceTexture is not
        // allowed). The installer owns the surface, installs a CONFIG_TRANSPARENT chain and mirrors
        // UiHelper's readiness so ModelViewer.render() proceeds.
        val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also { it.setOpaque(false) }
        val created = ModelViewer(engine, uiHelper)
        viewer = created
        transparentSwapChains = ArTransparentSwapChainInstaller(engine).also { it.start(created, this) }
        created.view.blendMode = BlendMode.TRANSLUCENT
        created.view.isPostProcessingEnabled = false
        created.scene.skybox = null
        created.renderer.clearOptions = created.renderer.clearOptions.apply {
            clear = true
            discard = false
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        worker.execute {
            val model = runCatching {
                SpriteExtrusionGlb.build(mapOf("idle" to generatedSprite()))
            }.onFailure { Timber.tag(TAG).e(it, "Could not build the diagnostics sprite GLB") }.getOrNull()
            post {
                if (released || model == null) return@post
                runCatching {
                    created.loadModelGlb(directBuffer(model))
                    created.clearRootTransform()
                    created.cameraFocalLength = 40f
                    created.cameraNear = 0.1f
                    created.cameraFar = 100f
                    loaded = true
                    Timber.tag(TAG).i("Filament transparency overlay ready")
                }.onFailure { Timber.tag(TAG).e(it, "Could not load the diagnostics sprite GLB") }
            }
        }
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun renderFrame(frameTimeNanos: Long) {
        val current = viewer ?: return
        val installer = transparentSwapChains ?: return
        // Gate render on the transparent chain: a detached ModelViewer has no swap chain of its own.
        // The installer also mirrors UiHelper readiness, so viewer.render() proceeds once installed.
        if (!installer.ensure(current, this)) return
        if (width <= 0 || height <= 0) return
        current.view.viewport = Viewport(0, 0, width, height)
        current.cameraFocalLength = 40f
        current.cameraNear = 0.1f
        current.cameraFar = 100f
        current.camera.lookAt(0.0, 0.5, 3.0, 0.0, 0.5, 0.0, 0.0, 1.0, 0.0)
        // Render even before the model finished loading: ModelViewer.render() drives the async
        // resource uploads, and it is the only place the swap chain gets presented.
        current.asset?.let { asset ->
            if (loaded) {
                val angleDegrees = ((frameTimeNanos / 1_000_000L) % 12_000L) * 0.03f
                val transform = FloatArray(16)
                Matrix.setIdentityM(transform, 0)
                // Keep the sprite small and centered so the live camera stays visible around it: the
                // GLB is 1 unit tall with its base at y = 0, camera looks at (0, 0.5, 0) from z = +3.
                Matrix.translateM(transform, 0, 0f, 0.25f, 0f)
                Matrix.rotateM(transform, 0, angleDegrees, 0f, 1f, 0f)
                Matrix.scaleM(transform, 0, 0.5f, 0.5f, 0.5f)
                val transformManager = engine.transformManager
                transformManager.setTransform(transformManager.getInstance(asset.root), transform)
            }
        }
        current.render(frameTimeNanos)
    }

    fun releaseScene() {
        if (released) return
        released = true
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        worker.shutdownNow()
        transparentSwapChains?.stop()
        transparentSwapChains = null
        runCatching { viewer?.destroy() }
            .onFailure { Timber.tag(TAG).w(it, "Filament overlay teardown failed") }
        viewer = null
    }

    override fun onDetachedFromWindow() {
        releaseScene()
        super.onDetachedFromWindow()
    }

    private fun generatedSprite(size: Int = 64): ResidentFrameImage {
        val pixels = IntArray(size * size) { index ->
            val x = index % size
            val y = index / size
            val border = x < 3 || y < 3 || x >= size - 3 || y >= size - 3
            if (border) 0xFFFFFFFF.toInt() else 0xFFE040FB.toInt()
        }
        return ResidentFrameImage(argb = pixels, width = size, height = size)
    }

    private fun directBuffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }

    private companion object {
        const val TAG = "ArDiagnosticsOverlay"
    }
}
