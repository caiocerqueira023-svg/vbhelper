package com.github.nacabaro.vbhelper.screens.digifarmScreen

import android.content.Context
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.ModelViewer
import com.github.nacabaro.vbhelper.digifarm.map.Digifarm3dAssetCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One decoded sprite frame. [argb] uses Android ARGB packing, alpha 0 = transparent. */
data class ResidentFrameImage(
    val argb: IntArray,
    val width: Int,
    val height: Int,
)

/**
 * Full sprite set of one resident. Sent once per roster/art change —
 * never per animation tick — so the GL driver sees no create/destroy churn.
 * Pose keys: sleep, train, train2, happy, walk, walk2.
 */
data class ResidentFrames(
    val id: String,
    val poses: Map<String, ResidentFrameImage>,
    val setKey: String,
    /** Initial mirror until motion in camera space takes over. */
    val facingLeft: Boolean,
)

/** Per-tick state: which cached texture is bound plus where the billboard stands. */
data class ResidentPose(
    val id: String,
    val pose: String,
    val worldX: Float,
    val worldZ: Float,
    val selected: Boolean,
)

/**
 * Compose host for the 2.5D Digifarm scene.
 *
 * The Filament scene owns the island AND the Digimon billboards. Compose
 * above it only draws UI (speech bubbles, invisible tap targets, resident
 * controls) positioned with the same camera projection, so sprites sit on
 * the terrain with real depth and occlusion instead of floating over it.
 */
@Composable
fun Digifarm3dViewport(
    modifier: Modifier = Modifier,
    assetName: String = "digifarm/3d/digi_farm_3d.glb",
    onReady: ((Digifarm3dSceneView) -> Unit)? = null,
    onAssetError: ((String) -> Unit)? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).also { container ->
                runCatching { Digifarm3dSceneView(context) }
                    .onSuccess { view ->
                        container.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        view.loadAsset(assetName, onAssetError)
                        // Defer state callbacks until the AndroidView has been
                        // attached; mutating Compose state from the factory can
                        // otherwise race the first composition.
                        container.post { onReady?.invoke(view) }
                    }
                    .onFailure { failure ->
                        Log.e("Digifarm3d", "Could not create Digifarm scene view", failure)
                        container.post {
                            onAssetError?.invoke(
                                describeDigifarmFailure(
                                    failure,
                                    "Digifarm 3D renderer unavailable"
                                )
                            )
                        }
                    }
            }
        },
        onRelease = { container ->
            (container.getChildAt(0) as? Digifarm3dSceneView)?.releaseScene()
            container.removeAllViews()
        },
        update = { container ->
            // Toggle path (e.g. wireframe on/off): hot-swap inside the live
            // Engine. Recreating the whole GL view here crashed on devices.
            (container.getChildAt(0) as? Digifarm3dSceneView)
                ?.switchAsset(assetName, onAssetError)
        },
    )
}

/** A TextureView is used so Compose bubbles can be layered above it. */
class Digifarm3dSceneView(context: Context) : TextureView(context) {
    private val logTag = "Digifarm3d"
    private val engine: Engine
    private val viewer: ModelViewer
    private var released = false
    private var residentsReleased = false
    private var frameStarted = false
    private var viewW = 0
    private var viewH = 0

    /** Fired on the main thread while the camera moves, so Compose bubbles follow. */
    var onCameraChange: (() -> Unit)? = null
    private val lastEye = FloatArray(3)
    private var hasLastEye = false
    private var lastNotifyNanos = 0L

    /**
     * Resident id the camera follows; null = island center. Written from
     * Compose; consumed on the render thread each frame, so the target
     * glides after a moving resident instead of jumping.
     */
    var followId: String? = null
    private var curTargetX = TARGET_X
    private var curTargetZ = TARGET_Z

    // Constrained orbit camera (replaces the Filament Manipulator, which has
    // no angle clamps and a hot gain for a farm this small). The target is
    // fixed on the island and the elevation never drops to the horizon, so
    // the hollow underside / untextured backs can never come into view.
    private var camYaw = HOME_YAW
    private var camPitch = HOME_PITCH
    private var camDist = HOME_DIST
    private var touchMode = TOUCH_NONE
    private var dragId = -1
    private var pinchId = -1
    private var lastX = 0f
    private var lastY = 0f
    private var lastPinch = 0f
    private val tmpCam = FloatArray(3)
    private val tmpMat = FloatArray(16)

    // Resident billboard resources. Created lazily from resident.glb; every
    // resident gets its own asset so each sprite texture is independent.
    // Pose textures are uploaded once and only rebound per animation tick:
    // no GL create/destroy churn while the farm is open.
    private var residentProvider: UbershaderProvider? = null
    private var residentLoader: AssetLoader? = null
    private var residentResources: ResourceLoader? = null
    private var residentTemplate: ByteArray? = null
    private var pendingFrames: List<ResidentFrames> = emptyList()
    private val residents = LinkedHashMap<String, ResidentEntry>()
    // Lazy: TextureSampler calls into filament-jni, so it must only be
    // created after ensureFilament() ran in init. An eager val would throw
    // UnsatisfiedLinkError during view construction (before Filament.init).
    private val pixelSampler: TextureSampler by lazy {
        TextureSampler(
            TextureSampler.MinFilter.NEAREST,
            TextureSampler.MagFilter.NEAREST,
            TextureSampler.WrapMode.CLAMP_TO_EDGE
        )
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (released) return
            updateFollowTarget()
            applyCamera()
            updateResidentTransforms()
            viewer.render(frameTimeNanos)
            notifyCameraChangeIfMoved(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        ensureFilament()
        Log.i(logTag, "Creating Filament Digifarm scene view")
        engine = Engine.create()
        val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        viewer = ModelViewer(this, engine, helper, null)
        Log.i(logTag, "Filament Digifarm scene view ready")
        // ModelViewer applies the lens projection when UiHelper reports a
        // non-zero surface viewport. Do not set camera properties here: a new
        // TextureView reports 0x0 during construction and would yield NaN
        // aspect-ratio values in Camera.setLensProjection.
        // TextureView does not support background drawables. Leaving its
        // default background untouched keeps the surface transparent without
        // triggering TextureView.setBackgroundDrawable at runtime.
        setOnTouchListener { view, event ->
            // The Compose screen is vertically scrollable so its lower
            // controls remain reachable. Once a gesture starts on the 3D
            // viewport, keep the parent from stealing orbit or pinch input.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            if (!released && event.actionMasked == MotionEvent.ACTION_DOWN) {
                performClick()
            }
            if (!released) onTouchImpl(event) else false
        }
        startFrames()
    }

    fun loadAsset(assetName: String, onAssetError: ((String) -> Unit)? = null) {
        if (released) return
        loadGen += 1
        launchLoad(assetName, loadGen, onAssetError)
    }

    /**
     * Hot-swaps the map scene inside the live Engine (wireframe toggle).
     * Resident billboards live in separate assets and survive the swap, so
     * the GL view/Engine is never torn down mid-session here.
     */
    fun switchAsset(assetName: String, onAssetError: ((String) -> Unit)? = null) {
        if (released) return
        if (assetName == loadedAssetName && viewer.asset != null) return
        runCatching { viewer.destroyModel() }
        loadGen += 1
        launchLoad(assetName, loadGen, onAssetError)
    }

    private var loadedAssetName: String? = null
    private var loadGen = 0

    private fun launchLoad(assetName: String, gen: Int, onAssetError: ((String) -> Unit)? = null) {
        if (released) return
        Log.i(logTag, "Starting Digifarm asset load: $assetName")
        // GLB is self-contained. Copying into a direct buffer lets Filament
        // retain the source bytes while the asset loader resolves textures.
        Thread {
            val bytes = runCatching {
                if (assetName == "digifarm/3d/digi_farm_3d.glb") {
                    // Metadata controls spawns and collision mapping, but it
                    // must not prevent the renderer from opening a valid GLB.
                    // Keep the validation observable and let the map use its
                    // built-in defaults when a packaged manifest is stale or
                    // incomplete.
                    Digifarm3dAssetCatalog.read(context).onFailure { failure ->
                        Log.w(logTag, "Digifarm metadata unavailable; loading GLB with defaults", failure)
                    }
                }
                context.assets.open(assetName).use { it.readBytes() }
            }.getOrElse { failure ->
                Log.e(logTag, "Could not read Digifarm asset $assetName", failure)
                post {
                    onAssetError?.invoke(
                        describeDigifarmFailure(failure, "Digifarm 3D asset unavailable")
                    )
                }
                return@Thread
            }
            val template = runCatching {
                context.assets.open("digifarm/3d/resident.glb").use { it.readBytes() }
            }.onFailure { failure ->
                Log.w(logTag, "Resident billboard template unavailable; sprites hidden", failure)
            }.getOrNull()
            Log.d(logTag, "Read Digifarm asset $assetName (${bytes.size} bytes)")
            val buffer = ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .put(bytes)
                .apply { flip() }
            post {
                // Stale loads (superseded by a toggle) are dropped; only the
                // latest generation may touch the viewer.
                if (released || gen != loadGen) return@post
                runCatching {
                    viewer.loadModelGlb(buffer)
                    check(viewer.asset != null) {
                        "Filament returned no model for $assetName (${bytes.size} bytes)"
                    }
                    // The GLB is exported in canonical coordinates
                    // (island ~1.9 wide, top at y=0). Never rescale it at
                    // runtime: transformToUnitCube would frame the water
                    // plane instead of the island and desync every
                    // resident position.
                    viewer.clearRootTransform()
                    loadedAssetName = assetName
                    Log.i(logTag, "Digifarm asset loaded: $assetName (${bytes.size} bytes)")
                }.onFailure { failure ->
                    Log.e(logTag, "Could not load Digifarm asset $assetName", failure)
                    onAssetError?.invoke(
                        describeDigifarmFailure(
                            failure,
                            "Digifarm 3D asset could not be loaded"
                        )
                    )
                }
                template?.let { residentTemplate = it }
                if (pendingFrames.isNotEmpty()) {
                    val pending = pendingFrames
                    pendingFrames = emptyList()
                    setResidentFrames(pending)
                }
            }
        }.start()
    }

    /**
     * Uploads full sprite sets. Call only when the roster or the sprite art
     * changes; per-tick animation goes through [updateResidentPoses], which
     * only rebinds already-uploaded textures. Main thread.
     */
    fun setResidentFrames(frames: List<ResidentFrames>) {
        if (released || residentsReleased) return
        val template = residentTemplate
        if (template == null) {
            pendingFrames = frames
            return
        }
        if (residentProvider == null) {
            val provider = UbershaderProvider(engine)
            residentProvider = provider
            residentLoader = AssetLoader(engine, provider, EntityManager.get())
            residentResources = ResourceLoader(engine)
        }
        val wanted = frames.associateBy { it.id }
        for (id in residents.keys - wanted.keys) {
            destroyResident(id)
        }
        for (set in frames) {
            val existing = residents[set.id]
            if (existing != null && existing.setKey == set.setKey) continue
            if (existing != null) destroyResident(set.id)
            runCatching { createResident(set, template) }
                .onFailure { failure ->
                    Log.w(logTag, "Could not create billboard for ${set.id}", failure)
                }
        }
    }

    /** Per-tick update: pose binding swap + position/selection. Main thread. */
    fun updateResidentPoses(poses: List<ResidentPose>) {
        if (released || residentsReleased) return
        for (pose in poses) {
            val entry = residents[pose.id] ?: continue
            entry.worldX = pose.worldX
            entry.worldZ = pose.worldZ
            entry.selected = pose.selected
            if (entry.currentPose != pose.pose) {
                runCatching { bindPose(entry, pose.pose) }
                    .onFailure { failure ->
                        Log.w(logTag, "Could not bind pose ${pose.pose} for ${pose.id}", failure)
                    }
            }
        }
    }

    /**
     * Projects a world point to view pixels. Returns null when the view has
     * no size yet or the point is behind the camera. Used by Compose for
     * speech bubbles and tap targets with the exact same camera.
     */
    fun projectWorld(x: Float, y: Float, z: Float): Pair<Float, Float>? {
        val w = viewW
        val h = viewH
        if (w <= 0 || h <= 0) return null
        return runCatching {
            val view = viewer.camera.getViewMatrix(FloatArray(16))
            val projD = viewer.camera.getProjectionMatrix(DoubleArray(16))
            val proj = FloatArray(16) { i -> projD[i].toFloat() }
            val vp = FloatArray(16)
            Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
            val out = FloatArray(4)
            Matrix.multiplyMV(out, 0, vp, 0, floatArrayOf(x, y, z, 1f), 0)
            if (out[3] <= 0f) return null
            val ndcX = out[0] / out[3]
            val ndcY = out[1] / out[3]
            Pair((ndcX * 0.5f + 0.5f) * w, (1f - (ndcY * 0.5f + 0.5f)) * h)
        }.getOrNull()
    }

    fun resetCamera() {
        if (released) return
        camYaw = HOME_YAW
        camPitch = HOME_PITCH
        camDist = HOME_DIST
        followId = null
        curTargetX = TARGET_X
        curTargetZ = TARGET_Z
    }

    /** Recenters the orbit on the island without touching zoom/angle. */
    fun clearFollow() {
        followId = null
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) {
            viewW = width
            viewH = height
            // Flattened 2.5D lens: a longer focal length reads as oblique /
            // isometric instead of wide-angle perspective. Applied here (not
            // in init) because a 0x0 viewport would produce a NaN aspect.
            viewer.cameraFocalLength = DIGIFARM_FOCAL_MM
            // Tight depth range for the 2-unit farm: the default 0.05/1000
            // ratio starves precision and makes close layers (sprite grout,
            // stadium bases) shimmer. Nothing is nearer than ~0.6 or
            // farther than ~8 from the camera.
            viewer.cameraNear = DIGIFARM_NEAR
            viewer.cameraFar = DIGIFARM_FAR
        }
    }

    private fun startFrames() {
        if (frameStarted) return
        frameStarted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun releaseScene() {
        if (released) return
        released = true
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        destroyResidents()
        // ModelViewer registers its own detach listener and owns destruction
        // of the Engine. Marking the view released here prevents another frame
        // while that listener performs the single native teardown.
    }

    override fun onDetachedFromWindow() {
        if (!released) {
            released = true
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
        // Resident textures, assets and loaders must be freed BEFORE
        // ModelViewer's detach listener destroys the Engine.
        destroyResidents()
        super.onDetachedFromWindow()
    }

    // ---- Constrained orbit camera ----

    private fun onTouchImpl(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragId = event.getPointerId(0)
                lastX = event.x
                lastY = event.y
                pinchId = -1
                touchMode = TOUCH_DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    val other = (0 until event.pointerCount)
                        .map { event.getPointerId(it) }
                        .firstOrNull { it != dragId }
                    if (other != null) {
                        pinchId = other
                        lastPinch = pinchDist(event)
                        touchMode = TOUCH_PINCH
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchMode == TOUCH_PINCH && pinchId != -1) {
                    val d = pinchDist(event)
                    if (d > 0f && lastPinch > 0f) {
                        camDist = (camDist * lastPinch / d).coerceIn(DIST_MIN, DIST_MAX)
                    }
                    lastPinch = d
                    refreshDragAnchor(event)
                } else if (touchMode == TOUCH_DRAG) {
                    val di = event.findPointerIndex(dragId)
                    if (di >= 0) {
                        val x = event.getX(di)
                        val y = event.getY(di)
                        // Horizontal orbit locked to the modeled side: full
                        // spins would show unfinished backsides.
                        camYaw = (camYaw - (x - lastX) * YAW_GAIN)
                            .coerceIn(YAW_MIN, YAW_MAX)
                        camPitch = (camPitch + (y - lastY) * PITCH_GAIN)
                            .coerceIn(PITCH_MIN, PITCH_MAX)
                        lastX = x
                        lastY = y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val upId = event.getPointerId(event.actionIndex)
                if (upId == pinchId) {
                    pinchId = -1
                    touchMode = TOUCH_DRAG
                    refreshDragAnchor(event)
                } else if (upId == dragId) {
                    val other = (0 until event.pointerCount)
                        .map { event.getPointerId(it) }
                        .firstOrNull { it != upId && it != pinchId }
                    if (other != null) {
                        dragId = other
                        refreshDragAnchor(event)
                    }
                    pinchId = -1
                    touchMode = TOUCH_DRAG
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TOUCH_NONE
                dragId = -1
                pinchId = -1
            }
        }
        return true
    }

    private fun refreshDragAnchor(event: MotionEvent) {
        val di = event.findPointerIndex(dragId)
        if (di >= 0) {
            lastX = event.getX(di)
            lastY = event.getY(di)
        }
    }

    private fun pinchDist(event: MotionEvent): Float {
        val a = event.findPointerIndex(dragId)
        val b = event.findPointerIndex(pinchId)
        if (a < 0 || b < 0) return 0f
        val dx = event.getX(a) - event.getX(b)
        val dy = event.getY(a) - event.getY(b)
        return sqrt(dx * dx + dy * dy)
    }

    private fun applyCamera() {
        val cp = cos(camPitch)
        val ex = curTargetX + camDist * cp * sin(camYaw)
        val ey = TARGET_Y + camDist * sin(camPitch)
        val ez = curTargetZ + camDist * cp * cos(camYaw)
        viewer.camera.lookAt(
            ex.toDouble(), ey.toDouble(), ez.toDouble(),
            curTargetX.toDouble(), TARGET_Y.toDouble(), curTargetZ.toDouble(),
            0.0, 1.0, 0.0
        )
    }

    /** Eases the orbit target toward the followed resident (or back home). */
    private fun updateFollowTarget() {
        val followed = followId?.let { residents[it] }
        val tx = followed?.worldX ?: TARGET_X
        val tz = followed?.worldZ ?: TARGET_Z
        curTargetX += (tx - curTargetX) * FOLLOW_LERP
        curTargetZ += (tz - curTargetZ) * FOLLOW_LERP
        if (kotlin.math.abs(tx - curTargetX) < 0.001f) curTargetX = tx
        if (kotlin.math.abs(tz - curTargetZ) < 0.001f) curTargetZ = tz
    }

    // ---- Resident billboards ----

    private class ResidentEntry(
        val asset: FilamentAsset,
        val spriteMaterial: MaterialInstance,
        val setKey: String,
        val textures: MutableMap<String, Texture> = LinkedHashMap(),
        val pixelBuffers: MutableMap<String, ByteBuffer> = LinkedHashMap(),
        val aspects: MutableMap<String, Float> = LinkedHashMap(),
        var currentPose: String? = null,
        var worldX: Float = 0f,
        var worldZ: Float = 0f,
        var selected: Boolean = false,
        // Mirror is resolved from motion in CAMERA space every frame. The
        // simulation flag is legacy-map space and reads backwards as soon as
        // the user orbits, so it only seeds the initial value (inverted:
        // VB art faces left, so facingLeft means unmirrored).
        var mirror: Boolean = false,
        var prevX: Float = 0f,
        var prevZ: Float = 0f,
        var hasPrev: Boolean = false,
    )

    private fun aspectOf(image: ResidentFrameImage): Float {
        if (image.width <= 0 || image.height <= 0) return 1f
        return (image.width.toFloat() / image.height.toFloat()).coerceIn(0.5f, 1.8f)
    }

    private fun createResident(set: ResidentFrames, template: ByteArray) {
        val loader = residentLoader ?: return
        val resources = residentResources ?: return
        val buffer = ByteBuffer.allocateDirect(template.size)
            .order(ByteOrder.nativeOrder())
            .put(template)
            .apply { flip() }
        val asset = loader.createAsset(buffer) ?: run {
            Log.w(logTag, "Resident template parse returned null; skipping ${set.id}")
            return
        }
        resources.loadResources(asset)
        asset.releaseSourceData()
        viewer.scene.addEntities(asset.entities)
        val rcm = engine.renderableManager
        // The sprite and its contact shadow are fake 2D planes: they must
        // never cast or receive real shadows.
        for (entity in asset.entities) {
            if (rcm.hasComponent(entity)) {
                val instance = rcm.getInstance(entity)
                rcm.setCastShadows(instance, false)
                rcm.setReceiveShadows(instance, false)
            }
        }
        // Find the sprite material through its entity, not by array order:
        // binding the sprite texture to the shadow material (or vice versa)
        // leaves an opaque white quad on screen.
        val spriteMaterial = findSpriteMaterial(asset)
        if (spriteMaterial == null) {
            Log.w(logTag, "Resident template has no sprite material; skipping ${set.id}")
            runCatching {
                viewer.scene.removeEntities(asset.entities)
                loader.destroyAsset(asset)
            }
            return
        }
        val entry = ResidentEntry(asset = asset, spriteMaterial = spriteMaterial, setKey = set.setKey)
        // VB sprite art faces left unmirrored: facingLeft (moving legacy-left)
        // starts unmirrored; motion in camera space takes over below.
        entry.mirror = !set.facingLeft
        entry.prevX = 0f
        entry.prevZ = 0f
        entry.hasPrev = false
        residents[set.id] = entry
        for ((pose, image) in set.poses) {
            runCatching { uploadPose(entry, pose, image) }
                .onFailure { failure ->
                    Log.w(logTag, "Could not upload pose $pose for ${set.id}", failure)
                }
        }
        Log.i(logTag, "Billboard created for ${set.id} (${entry.textures.size} poses)")
    }

    /** Sprite material via the "sprite" entity; falls back to the first instance. */
    private fun findSpriteMaterial(asset: FilamentAsset): MaterialInstance? {
        val rcm = engine.renderableManager
        runCatching {
            val spriteEntity = asset.getFirstEntityByName("sprite")
            if (spriteEntity != 0 && rcm.hasComponent(spriteEntity)) {
                return rcm.getMaterialInstanceAt(rcm.getInstance(spriteEntity), 0)
            }
        }.onFailure { failure ->
            Log.w(logTag, "Sprite entity lookup failed, using first material", failure)
        }
        return asset.instance.materialInstances.firstOrNull()
    }

    private fun uploadPose(entry: ResidentEntry, pose: String, image: ResidentFrameImage) {
        if (image.width <= 0 || image.height <= 0) return
        if (image.argb.size < image.width * image.height) return
        val texture = Texture.Builder()
            .width(image.width)
            .height(image.height)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .usage(Texture.Usage.SAMPLEABLE or Texture.Usage.UPLOADABLE)
            .build(engine)
        val rgba = ByteBuffer.allocateDirect(image.width * image.height * 4)
            .order(ByteOrder.nativeOrder())
        for (pixel in image.argb.take(image.width * image.height)) {
            val a = (pixel ushr 24) and 0xFF
            rgba.put(((pixel ushr 16) and 0xFF).toByte())
            rgba.put(((pixel ushr 8) and 0xFF).toByte())
            rgba.put((pixel and 0xFF).toByte())
            rgba.put(a.toByte())
        }
        rgba.flip()
        // Keep our own reference per pose, and use a completion callback so
        // the driver never reads a released buffer (white-quad sampling).
        entry.pixelBuffers[pose] = rgba
        texture.setImage(
            engine, 0,
            Texture.PixelBufferDescriptor(
                rgba, Texture.Format.RGBA, Texture.Type.UBYTE,
                1, 0, 0, 0,
                Handler(Looper.getMainLooper())
            ) {
                // Upload consumed; the entry reference keeps it alive anyway.
            }
        )
        entry.textures[pose]?.let { runCatching { engine.destroyTexture(it) } }
        entry.textures[pose] = texture
        entry.aspects[pose] = aspectOf(image)
    }

    private fun bindPose(entry: ResidentEntry, pose: String) {
        val texture = entry.textures[pose] ?: entry.textures.values.firstOrNull() ?: return
        if (entry.currentPose != pose) {
            entry.spriteMaterial.setParameter("baseColorMap", texture, pixelSampler)
            entry.currentPose = pose
        }
    }

    private fun destroyResident(id: String) {
        val entry = residents.remove(id) ?: return
        runCatching {
            viewer.scene.removeEntities(entry.asset.entities)
            for (texture in entry.textures.values) {
                runCatching { engine.destroyTexture(texture) }
            }
            entry.textures.clear()
            entry.pixelBuffers.clear()
            residentLoader?.destroyAsset(entry.asset)
        }.onFailure { failure ->
            Log.w(logTag, "Could not destroy billboard for $id", failure)
        }
    }

    private fun updateResidentTransforms() {
        if (residents.isEmpty()) return
        val tm = engine.transformManager
        val hasCam = runCatching {
            viewer.camera.getPosition(tmpCam)
            true
        }.getOrDefault(false)
        if (!hasCam) return
        // Camera-right on the ground plane; mirror comes from motion along it.
        val rightX = cos(camYaw)
        val rightZ = -sin(camYaw)
        for (entry in residents.values) {
            if (!entry.hasPrev) {
                entry.prevX = entry.worldX
                entry.prevZ = entry.worldZ
                entry.hasPrev = true
            } else {
                val dx = entry.worldX - entry.prevX
                val dz = entry.worldZ - entry.prevZ
                if (dx * dx + dz * dz > MOVE_EPS_SQ) {
                    // VB art faces left unmirrored: mirror only when moving
                    // screen-right, so the sprite faces its travel direction.
                    val screenVel = dx * rightX + dz * rightZ
                    if (screenVel < -VEL_EPS) entry.mirror = false
                    else if (screenVel > VEL_EPS) entry.mirror = true
                    // Moving straight toward/away from the viewer keeps facing.
                    entry.prevX = entry.worldX
                    entry.prevZ = entry.worldZ
                }
            }
            // Cylindrical billboard: rotate around Y so the sprite plane
            // faces the camera, but keep the feet planted on the ground.
            val yaw = atan2(tmpCam[0] - entry.worldX, tmpCam[2] - entry.worldZ)
            val worldH = if (entry.selected) SELECTED_HEIGHT else RESIDENT_HEIGHT
            val aspect = entry.aspects[entry.currentPose] ?: 1f
            val worldW = worldH * aspect * if (entry.mirror) -1f else 1f
            Matrix.setIdentityM(tmpMat, 0)
            Matrix.translateM(tmpMat, 0, entry.worldX, 0f, entry.worldZ)
            Matrix.rotateM(tmpMat, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
            // Z shares the sprite width so the contact-shadow blob underneath
            // stays proportional (it lives in the XZ plane).
            Matrix.scaleM(tmpMat, 0, worldW, worldH, worldW)
            runCatching {
                val instance = tm.getInstance(entry.asset.root)
                if (instance != 0) tm.setTransform(instance, tmpMat)
            }
        }
    }

    private fun notifyCameraChangeIfMoved(frameTimeNanos: Long) {
        val cb = onCameraChange ?: return
        val eye = runCatching {
            viewer.camera.getPosition(FloatArray(3))
        }.getOrNull() ?: return
        if (!hasLastEye) {
            lastEye[0] = eye[0]
            lastEye[1] = eye[1]
            lastEye[2] = eye[2]
            hasLastEye = true
            return
        }
        val dx = eye[0] - lastEye[0]
        val dy = eye[1] - lastEye[1]
        val dz = eye[2] - lastEye[2]
        if (dx * dx + dy * dy + dz * dz <= CAMERA_EPS_SQ) return
        lastEye[0] = eye[0]
        lastEye[1] = eye[1]
        lastEye[2] = eye[2]
        // Throttled: bubbles/tap targets at ~8Hz during motion is plenty,
        // while per-frame recomposition caused visible stutter.
        if (frameTimeNanos - lastNotifyNanos < CAMERA_NOTIFY_MIN_NANOS) return
        lastNotifyNanos = frameTimeNanos
        runCatching { cb() }.onFailure { failure ->
            Log.w(logTag, "Camera change callback failed", failure)
        }
    }

    private fun destroyResidents() {
        if (residentsReleased) return
        residentsReleased = true
        pendingFrames = emptyList()
        runCatching {
            for (id in residents.keys.toList()) {
                destroyResident(id)
            }
            residentLoader?.destroy()
            residentResources?.destroy()
            residentProvider?.destroyMaterials()
            residentProvider?.destroy()
        }.onFailure { failure ->
            Log.w(logTag, "Could not release resident billboards", failure)
        }
        residentLoader = null
        residentResources = null
        residentProvider = null
        residentTemplate = null
    }

    companion object {
        private val filamentInitialized = AtomicBoolean(false)

        /** Flattened lens for the oblique 2.5D look (default ModelViewer is 28mm). */
        private const val DIGIFARM_FOCAL_MM = 50f
        private const val DIGIFARM_NEAR = 0.5f
        private const val DIGIFARM_FAR = 20f

        /** Resident sprite height in world units; the island is ~1.9 wide. */
        private const val RESIDENT_HEIGHT = 0.13f
        private const val SELECTED_HEIGHT = 0.15f

        // Constrained orbit camera. Home matches the previous oblique frame
        // (eye ~0/2.35/3.0 over target 0/0.05/-0.1). Elevation never reaches
        // the horizon, so the island underside stays out of view.
        private const val TARGET_X = 0f
        private const val TARGET_Y = 0.05f
        private const val TARGET_Z = -0.1f
        private const val HOME_YAW = 0f
        private const val HOME_PITCH = 0.64f
        private const val HOME_DIST = 3.86f
        // Yaw locked around home: only the modeled side is shown.
        private const val YAW_MIN = -0.65f
        private const val YAW_MAX = 0.65f
        private const val PITCH_MIN = 0.52f
        private const val PITCH_MAX = 1.10f
        private const val DIST_MIN = 1.2f
        private const val DIST_MAX = 4.8f
        private const val FOLLOW_LERP = 0.08f
        private const val YAW_GAIN = 0.0016f
        private const val PITCH_GAIN = 0.0013f
        private const val TOUCH_NONE = 0
        private const val TOUCH_DRAG = 1
        private const val TOUCH_PINCH = 2

        private const val CAMERA_EPS_SQ = 1e-8f
        private const val CAMERA_NOTIFY_MIN_NANOS = 120_000_000L

        // Camera-space facing: any real step updates the mirror from the
        // velocity along camera-right (art faces left unmirrored); near-zero
        // screen velocity keeps facing.
        private const val MOVE_EPS_SQ = 1e-10f
        private const val VEL_EPS = 1e-6f

        @Synchronized
        private fun ensureFilament() {
            if (filamentInitialized.get()) return
            // Filament.init() loads only filament-jni. The glTF AssetLoader
            // lives in the companion JNI library and otherwise fails at
            // runtime with UnsatisfiedLinkError.
            Filament.init()
            System.loadLibrary("gltfio-jni")
            filamentInitialized.set(true)
            Log.i("Digifarm3d", "Filament JNI initialized")
        }
    }
}

private fun describeDigifarmFailure(failure: Throwable, fallback: String): String =
    failure.message?.takeIf { it.isNotBlank() }
        ?: "${failure::class.java.simpleName}: $fallback"
