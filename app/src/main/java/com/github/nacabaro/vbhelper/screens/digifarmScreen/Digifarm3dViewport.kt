package com.github.nacabaro.vbhelper.screens.digifarmScreen

import android.content.Context
import android.opengl.Matrix
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
import com.google.android.filament.Colors
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Skybox
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.ModelViewer
import com.github.nacabaro.vbhelper.digifarm.map.Digifarm3dAssetCatalog
import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import com.github.nacabaro.vbhelper.rendering.sprite3d.cameraAssistedSpriteYaw
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Full sprite set of one resident. Sent once per roster/art change —
 * never per animation tick — so the GL driver sees no create/destroy churn.
 * Pose keys: idle, idle2, walk, walk2.
 */
data class ResidentFrames(
    val id: String,
    val poses: Map<String, ResidentFrameImage>,
    val modelGlb: ByteArray,
    val setKey: String,
)

/** Persisted position; the renderer interpolates each confirmed simulation step. */
data class ResidentPose(
    val id: String,
    val worldX: Float,
    val worldZ: Float,
    val activity: String,
    val facingLeft: Boolean,
    val selected: Boolean,
)

/**
 * Compose host for the 2.5D Digifarm scene.
 *
 * The Filament scene owns the island and the extruded Digimon models. Compose
 * above it only draws UI (speech bubbles, invisible tap targets, resident
 * controls) positioned with the same camera projection, so residents sit on
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
    private var backgroundSkybox: Skybox? = null
    private var released = false
    private var residentsReleased = false
    private var frameStarted = false
    private var viewW = 0
    private var viewH = 0

    /** Fired on the main thread while the camera moves, so Compose bubbles follow. */
    var onCameraChange: (() -> Unit)? = null
    private var lastNotifyNanos = 0L

    /**
     * Resident id the camera follows; null = island center. Written from
     * Compose; consumed on the render thread each frame, so the target
     * glides after a moving resident instead of jumping.
     */
    var followId: String? = null
    private var curTargetX = TARGET_X
    private var curTargetZ = TARGET_Z

    // Orbit camera with a bounded elevation and zoom. The island now has a
    // complete cliff around its perimeter, so horizontal orbit is unrestricted.
    private var camYaw = HOME_YAW
    private var camPitch = HOME_PITCH
    private var camDist = HOME_DIST
    private var touchMode = TOUCH_NONE
    private var dragId = -1
    private var pinchId = -1
    private var lastX = 0f
    private var lastY = 0f
    private var lastPinch = 0f
    private val tmpMat = FloatArray(16)

    private data class FloatingBlocks(
        val transformInstance: Int,
        val base: FloatArray,
        val animated: FloatArray,
        val amplitude: Float,
        val angularSpeed: Double,
        val phase: Double,
    )

    private data class FloatingCube(
        val transformInstance: Int,
        val base: FloatArray,
        val animated: FloatArray,
        val amplitude: Float,
        val angularSpeed: Double,
        val phase: Double,
    )

    private data class FloatingIsland(
        val transformInstance: Int,
        val base: FloatArray,
        val animated: FloatArray,
    )

    private data class PulsingGlow(
        val material: MaterialInstance,
        val phase: Double,
        val amplitude: Float,
    )

    private var floatingBlocks: List<FloatingBlocks> = emptyList()
    private var floatingCubes: List<FloatingCube> = emptyList()
    private var floatingIsland: FloatingIsland? = null
    private var pulsingGlows: List<PulsingGlow> = emptyList()
    private var islandFloatOffsetY = 0f
    private var floatingStartNanos = 0L

    // One generated GLB per resident contains the activity and walking poses plus a
    // contact shadow. Pose changes toggle renderable layers without rebuilding.
    private var residentProvider: UbershaderProvider? = null
    private var residentLoader: AssetLoader? = null
    private var residentResources: ResourceLoader? = null
    private var pendingFrames: List<ResidentFrames> = emptyList()
    private var latestResidentPoses: List<ResidentPose> = emptyList()
    private val residents = LinkedHashMap<String, ResidentEntry>()
    private var lastMotionFrameNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (released) return
            updateResidentMotion(frameTimeNanos)
            updateFollowTarget()
            applyCamera()
            updateFloatingBlocks(frameTimeNanos)
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
        viewer.view.setPostProcessingEnabled(true)
        viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
            enabled = true
            strength = 0.35f
            resolution = 256
            threshold = true
        }
        val backdrop = Colors.toLinear(
            Colors.RgbType.SRGB,
            DeepPurpleBgAlt.red, DeepPurpleBgAlt.green, DeepPurpleBgAlt.blue,
        )
        backgroundSkybox = Skybox.Builder()
            .color(backdrop[0], backdrop[1], backdrop[2], 1f)
            .build(engine)
            .also { viewer.scene.skybox = it }
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
     * Hot-swaps the map scene inside the live Engine.
     * Resident solids live in separate assets and survive the swap, so
     * the GL view/Engine is never torn down mid-session here.
     */
    fun switchAsset(assetName: String, onAssetError: ((String) -> Unit)? = null) {
        if (released) return
        if (assetName == loadedAssetName && viewer.asset != null) return
        floatingBlocks = emptyList()
        floatingCubes = emptyList()
        floatingIsland = null
        pulsingGlows = emptyList()
        islandFloatOffsetY = 0f
        floatingStartNanos = 0L
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
                    collectFloatingBlocks(checkNotNull(viewer.asset))
                    collectPulsingGlows(checkNotNull(viewer.asset))
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
                if (pendingFrames.isNotEmpty()) {
                    val pending = pendingFrames
                    pendingFrames = emptyList()
                    setResidentFrames(pending)
                }
            }
        }.start()
    }

    private fun collectFloatingBlocks(asset: FilamentAsset) {
        val manager = engine.transformManager
        val groups = ArrayList<FloatingBlocks>()
        val cubes = ArrayList<FloatingCube>()
        for (index in 1..6) {
            val name = "VoxelFragmentsNear${index.toString().padStart(2, '0')}"
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0) {
                Log.w(logTag, "Floating block node missing: $name")
                continue
            }
            val instance = manager.getInstance(entity)
            if (instance == 0) {
                Log.w(logTag, "Floating block transform missing: $name")
                continue
            }
            val base = manager.getTransform(instance, FloatArray(16))
            groups += FloatingBlocks(
                instance, base, base.copyOf(), 0.052f + groups.size * 0.003f,
                1.25 + groups.size * 0.11, groups.size * 1.19,
            )
            val blockCount = BLOCKS_PER_GROUP[index - 1]
            for (blockIndex in 1..blockCount) {
                val blockName = "VoxelBlockNear${index.toString().padStart(2, '0')}_" +
                    blockIndex.toString().padStart(2, '0')
                val blockEntity = asset.getFirstEntityByName(blockName)
                if (blockEntity == 0) {
                    Log.w(logTag, "Individual floating cube missing: $blockName")
                    continue
                }
                val blockTransform = manager.getInstance(blockEntity)
                if (blockTransform == 0) {
                    Log.w(logTag, "Individual cube transform missing: $blockName")
                    continue
                }
                val blockBase = manager.getTransform(blockTransform, FloatArray(16))
                val ordinal = cubes.size
                cubes += FloatingCube(
                    blockTransform,
                    blockBase,
                    blockBase.copyOf(),
                    0.018f + (ordinal % 5) * 0.0035f,
                    1.7 + (ordinal % 6) * 0.13,
                    ordinal * 1.37,
                )
            }
        }
        val islandEntity = asset.getFirstEntityByName("Island")
        floatingIsland = if (islandEntity != 0) {
            val instance = manager.getInstance(islandEntity)
            if (instance != 0) {
                val base = manager.getTransform(instance, FloatArray(16))
                FloatingIsland(instance, base, base.copyOf())
            } else {
                Log.w(logTag, "Island transform missing; island bob disabled")
                null
            }
        } else {
            Log.w(logTag, "Island node missing; island bob disabled")
            null
        }
        floatingBlocks = groups
        floatingCubes = cubes
        islandFloatOffsetY = 0f
        floatingStartNanos = 0L
        Log.i(logTag, "Loaded ${groups.size}/6 floating voxel groups")
        Log.i(logTag, "Loaded ${cubes.size}/14 individually floating cubes")
    }

    private fun updateFloatingBlocks(frameTimeNanos: Long) {
        if (floatingBlocks.isEmpty() && floatingCubes.isEmpty() &&
            floatingIsland == null && pulsingGlows.isEmpty()) return
        if (floatingStartNanos == 0L) floatingStartNanos = frameTimeNanos
        val elapsed = (frameTimeNanos - floatingStartNanos) / 1_000_000_000.0
        val manager = engine.transformManager
        for (group in floatingBlocks) {
            group.animated[12] = group.base[12] +
                (sin(elapsed * group.angularSpeed * 0.62 + group.phase) * 0.012).toFloat()
            group.animated[13] = group.base[13] +
                (sin(elapsed * group.angularSpeed + group.phase) * group.amplitude).toFloat()
            manager.setTransform(group.transformInstance, group.animated)
        }
        for (cube in floatingCubes) {
            cube.animated[12] = cube.base[12] +
                (sin(elapsed * cube.angularSpeed * 0.58 + cube.phase) * 0.0045).toFloat()
            cube.animated[13] = cube.base[13] +
                (sin(elapsed * cube.angularSpeed + cube.phase) * cube.amplitude).toFloat()
            cube.animated[14] = cube.base[14] +
                (cos(elapsed * cube.angularSpeed * 0.47 + cube.phase) * 0.003).toFloat()
            manager.setTransform(cube.transformInstance, cube.animated)
        }
        floatingIsland?.let { island ->
            islandFloatOffsetY = (sin(elapsed * ISLAND_FLOAT_SPEED) * ISLAND_FLOAT_AMPLITUDE).toFloat()
            island.animated[13] = island.base[13] + islandFloatOffsetY
            manager.setTransform(island.transformInstance, island.animated)
        }
        pulsingGlows.forEach { glow ->
            val pulse = (0.5 + 0.5 * sin(elapsed * GLOW_PULSE_SPEED + glow.phase)).toFloat()
            val strength = GLOW_BASE_INTENSITY + glow.amplitude * pulse
            glow.material.setParameter("emissiveFactor", strength, strength, strength)
        }
    }

    private fun collectPulsingGlows(asset: FilamentAsset) {
        val renderables = engine.renderableManager
        val materials = LinkedHashMap<Long, PulsingGlow>()

        fun add(entityName: String, primitiveIndex: Int, phase: Double, amplitude: Float) {
            val entity = asset.getFirstEntityByName(entityName)
            if (entity == 0 || !renderables.hasComponent(entity)) return
            val instance = renderables.getInstance(entity)
            if (instance == 0 || primitiveIndex !in 0 until renderables.getPrimitiveCount(instance)) return
            val material = renderables.getMaterialInstanceAt(instance, primitiveIndex)
            val nativeMaterial = material.getNativeObject()
            if (!materials.containsKey(nativeMaterial)) {
                materials[nativeMaterial] = PulsingGlow(material, phase, amplitude)
            }
        }

        // The island top contains the violet wireframe texture; keep its pulse
        // restrained because the grass shares this baked material.
        add("Island", primitiveIndex = 0, phase = 0.0, amplitude = 0.48f)

        var blockOrdinal = 0
        for (groupIndex in BLOCKS_PER_GROUP.indices) {
            val groupNumber = groupIndex + 1
            for (blockIndex in 1..BLOCKS_PER_GROUP[groupIndex]) {
                val name = "VoxelBlockNear${groupNumber.toString().padStart(2, '0')}_" +
                    blockIndex.toString().padStart(2, '0')
                add(
                    name,
                    primitiveIndex = 0,
                    phase = blockOrdinal * 0.37,
                    amplitude = 0.68f,
                )
                blockOrdinal += 1
            }
        }
        pulsingGlows = materials.values.toList()
        Log.i(logTag, "Loaded ${pulsingGlows.size} pulsing Digifarm emissive materials")
    }

    /** Loads resident solids once per art change; pose ticks only change visible layers. */
    fun setResidentFrames(frames: List<ResidentFrames>) {
        if (released || residentsReleased) return
        if (viewer.asset == null) {
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
            runCatching { createResident(set) }
                .onFailure { failure ->
                    Log.w(logTag, "Could not create extruded resident for ${set.id}", failure)
                }
        }
        updateResidentPoses(latestResidentPoses)
    }

    /** Per-tick update: interpolate only the collision-checked positions from Room. */
    fun updateResidentPoses(poses: List<ResidentPose>) {
        if (released || residentsReleased) return
        latestResidentPoses = poses
        for (pose in poses) {
            val entry = residents[pose.id] ?: continue
            if (!entry.hasVisualPosition) {
                entry.visualX = pose.worldX
                entry.visualZ = pose.worldZ
                entry.headingYaw = if (pose.facingLeft) -PI.toFloat() else 0f
                entry.desiredYaw = entry.headingYaw
                entry.hasVisualPosition = true
            } else {
                val dx = pose.worldX - entry.worldX
                val dz = pose.worldZ - entry.worldZ
                val distanceSquared = dx * dx + dz * dz
                if (distanceSquared >= MIN_MOVEMENT_DISTANCE_SQ) {
                    if (distanceSquared > MAX_INTERPOLATED_STEP_SQ) {
                        entry.visualX = pose.worldX
                        entry.visualZ = pose.worldZ
                        entry.segmentActive = false
                    } else {
                        entry.segmentStartX = entry.visualX
                        entry.segmentStartZ = entry.visualZ
                        entry.segmentEndX = pose.worldX
                        entry.segmentEndZ = pose.worldZ
                        entry.segmentStartNanos = lastMotionFrameNanos.takeIf { it > 0L } ?: System.nanoTime()
                        entry.segmentActive = true
                        entry.desiredYaw = atan2(-dz, dx)
                        val turn = atan2(
                            sin(entry.desiredYaw - entry.headingYaw),
                            cos(entry.desiredYaw - entry.headingYaw),
                        )
                        if (kotlin.math.abs(turn) > PI / 2) entry.headingYaw = entry.desiredYaw
                    }
                }
            }
            entry.worldX = pose.worldX
            entry.worldZ = pose.worldZ
            entry.activity = pose.activity
            entry.selected = pose.selected
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
            Matrix.multiplyMV(out, 0, vp, 0, floatArrayOf(x, y + islandFloatOffsetY, z, 1f), 0)
            if (out[3] <= 0f) return null
            val ndcX = out[0] / out[3]
            val ndcY = out[1] / out[3]
            Pair((ndcX * 0.5f + 0.5f) * w, (1f - (ndcY * 0.5f + 0.5f)) * h)
        }.getOrNull()
    }

    fun projectResident(id: String, height: Float): Pair<Float, Float>? {
        val resident = residents[id] ?: return null
        return projectWorld(resident.visualX, height, resident.visualZ)
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
        releaseBackgroundSkybox()
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
        releaseBackgroundSkybox()
        super.onDetachedFromWindow()
    }

    private fun releaseBackgroundSkybox() {
        val skybox = backgroundSkybox ?: return
        backgroundSkybox = null
        viewer.scene.skybox = null
        engine.destroySkybox(skybox)
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
                        val nextYaw = camYaw - (x - lastX) * YAW_GAIN
                        camYaw = atan2(sin(nextYaw), cos(nextYaw))
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
        val tx = followed?.visualX ?: TARGET_X
        val tz = followed?.visualZ ?: TARGET_Z
        curTargetX += (tx - curTargetX) * FOLLOW_LERP
        curTargetZ += (tz - curTargetZ) * FOLLOW_LERP
        if (kotlin.math.abs(tx - curTargetX) < 0.001f) curTargetX = tx
        if (kotlin.math.abs(tz - curTargetZ) < 0.001f) curTargetZ = tz
    }

    // ---- Extruded residents ----

    private class ResidentEntry(
        val asset: FilamentAsset,
        val setKey: String,
        val poseEntities: Map<String, Int>,
        var currentPose: String? = null,
        var worldX: Float = 0f,
        var worldZ: Float = 0f,
        var visualX: Float = 0f,
        var visualZ: Float = 0f,
        var segmentStartX: Float = 0f,
        var segmentStartZ: Float = 0f,
        var segmentEndX: Float = 0f,
        var segmentEndZ: Float = 0f,
        var segmentStartNanos: Long = 0L,
        var segmentActive: Boolean = false,
        var hasVisualPosition: Boolean = false,
        var selected: Boolean = false,
        var activity: String = "EXPLORE",
        var headingYaw: Float = 0f,
        var desiredYaw: Float = 0f,
        var mirrorSprite: Boolean = false,
        var animationMode: String? = null,
        var animationFrame: Int = 0,
        var lastAnimationFrameNanos: Long = 0L,
    )

    private fun createResident(set: ResidentFrames) {
        val loader = residentLoader ?: return
        val resources = residentResources ?: return
        val buffer = ByteBuffer.allocateDirect(set.modelGlb.size)
            .order(ByteOrder.nativeOrder())
            .put(set.modelGlb)
            .apply { flip() }
        val asset = loader.createAsset(buffer) ?: run {
            Log.w(logTag, "Extruded resident GLB parse returned null: ${set.id}")
            return
        }
        resources.loadResources(asset)
        asset.releaseSourceData()
        viewer.scene.addEntities(asset.entities)
        val rcm = engine.renderableManager
        val poseEntities = LinkedHashMap<String, Int>()
        for (entity in asset.entities) {
            if (rcm.hasComponent(entity)) {
                val instance = rcm.getInstance(entity)
                rcm.setCastShadows(instance, false)
                rcm.setReceiveShadows(instance, false)
            }
        }
        for (pose in set.poses.keys) {
            val entity = asset.getFirstEntityByName("pose_$pose")
            if (entity != 0 && rcm.hasComponent(entity)) {
                poseEntities[pose] = entity
                rcm.setLayerMask(rcm.getInstance(entity), 0xFF, 0)
            }
        }
        if (poseEntities.isEmpty()) {
            Log.w(logTag, "Extruded resident has no pose meshes: ${set.id}")
            runCatching {
                viewer.scene.removeEntities(asset.entities)
                loader.destroyAsset(asset)
            }
            return
        }
        val entry = ResidentEntry(asset = asset, setKey = set.setKey, poseEntities = poseEntities)
        residents[set.id] = entry
        bindPose(entry, "idle")
        Log.i(logTag, "Extruded resident created: ${set.id} (${poseEntities.size} poses)")
    }

    private fun bindPose(entry: ResidentEntry, pose: String) {
        val nextPose = if (pose in entry.poseEntities) pose else entry.poseEntities.keys.firstOrNull() ?: return
        if (entry.currentPose == nextPose) return
        val rcm = engine.renderableManager
        entry.currentPose?.let { old ->
            entry.poseEntities[old]?.let { entity ->
                rcm.setLayerMask(rcm.getInstance(entity), 0xFF, 0)
            }
        }
        val entity = entry.poseEntities.getValue(nextPose)
        rcm.setLayerMask(rcm.getInstance(entity), 0xFF, 0x01)
        entry.currentPose = nextPose
    }

    private fun destroyResident(id: String) {
        val entry = residents.remove(id) ?: return
        runCatching {
            viewer.scene.removeEntities(entry.asset.entities)
            residentLoader?.destroyAsset(entry.asset)
        }.onFailure { failure ->
            Log.w(logTag, "Could not destroy extruded resident for $id", failure)
        }
    }

    private fun updateResidentMotion(frameTimeNanos: Long) {
        val previous = lastMotionFrameNanos
        lastMotionFrameNanos = frameTimeNanos
        val dt = if (previous == 0L) 0f else
            ((frameTimeNanos - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
        for (entry in residents.values) {
            if (!entry.hasVisualPosition) continue
            var walking = false
            if (entry.segmentActive) {
                val progress = ((frameTimeNanos - entry.segmentStartNanos).toFloat() /
                    RESIDENT_STEP_NANOS).coerceIn(0f, 1f)
                entry.visualX = entry.segmentStartX + (entry.segmentEndX - entry.segmentStartX) * progress
                entry.visualZ = entry.segmentStartZ + (entry.segmentEndZ - entry.segmentStartZ) * progress
                walking = progress < 1f
                if (!walking) entry.segmentActive = false
            }
            if (walking) {
                val turn = atan2(
                    sin(entry.desiredYaw - entry.headingYaw),
                    cos(entry.desiredYaw - entry.headingYaw),
                )
                entry.headingYaw += turn.coerceIn(-TURN_SPEED_RADIANS_PER_SECOND * dt,
                    TURN_SPEED_RADIANS_PER_SECOND * dt)
            }
            updateResidentAnimation(entry, walking, frameTimeNanos)
        }
    }

    private fun updateResidentAnimation(entry: ResidentEntry, walking: Boolean, frameTimeNanos: Long) {
        val mode = when {
            walking -> "walk"
            entry.activity == "REST" -> "sleep"
            entry.activity == "TRAIN" -> "train"
            entry.activity == "PLAY" || entry.activity == "EAT" ||
                entry.activity == "SOCIALIZE" -> "happy"
            else -> "idle"
        }
        if (entry.animationMode != mode) {
            entry.animationMode = mode
            entry.animationFrame = 0
            entry.lastAnimationFrameNanos = frameTimeNanos
            bindPose(entry, mode)
            return
        }
        val alternate = when (mode) {
            "walk" -> "walk2"
            "train" -> "train2"
            "idle" -> "idle2"
            "happy" -> "happy2"
            else -> return
        }
        val frameDuration = when (mode) {
            "walk" -> WALK_FRAME_NANOS
            "train" -> TRAIN_FRAME_NANOS
            "happy" -> ACTION_FRAME_NANOS
            else -> IDLE_FRAME_NANOS
        }
        val elapsed = frameTimeNanos - entry.lastAnimationFrameNanos
        if (elapsed < frameDuration) return
        val frameSteps = (elapsed / frameDuration).toInt()
        entry.animationFrame = (entry.animationFrame + frameSteps) % 2
        entry.lastAnimationFrameNanos += frameSteps * frameDuration
        bindPose(entry, if (entry.animationFrame == 0) mode else alternate)
    }

    private fun updateResidentTransforms() {
        if (residents.isEmpty()) return
        val tm = engine.transformManager
        for (entry in residents.values) {
            if (!entry.hasVisualPosition) continue
            val worldH = if (entry.selected) SELECTED_HEIGHT else RESIDENT_HEIGHT
            // Keep some camera assistance for readability, but preserve most of
            // the resident's physical heading so orbiting does not drag it along.
            val relativeHeading = atan2(
                sin(entry.headingYaw - camYaw),
                cos(entry.headingYaw - camYaw),
            )
            val lateral = cos(relativeHeading)
            // Imported walk frames face left in their native orientation.
            // Mirror them only for travel toward screen right.
            if (lateral > FACING_FLIP_THRESHOLD) entry.mirrorSprite = true
            if (lateral < -FACING_FLIP_THRESHOLD) entry.mirrorSprite = false
            val visibleYaw = cameraAssistedSpriteYaw(entry.headingYaw, camYaw)
            Matrix.setIdentityM(tmpMat, 0)
            Matrix.translateM(tmpMat, 0, entry.visualX, islandFloatOffsetY, entry.visualZ)
            Matrix.rotateM(tmpMat, 0,
                Math.toDegrees(visibleYaw.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.scaleM(tmpMat, 0,
                if (entry.mirrorSprite) -worldH else worldH, worldH, worldH)
            runCatching {
                val instance = tm.getInstance(entry.asset.root)
                if (instance != 0) tm.setTransform(instance, tmpMat)
            }
        }
    }

    private fun notifyCameraChangeIfMoved(frameTimeNanos: Long) {
        val cb = onCameraChange ?: return
        if (residents.isEmpty() || frameTimeNanos - lastNotifyNanos < CAMERA_NOTIFY_MIN_NANOS) return
        lastNotifyNanos = frameTimeNanos
        runCatching { cb() }.onFailure { failure ->
            Log.w(logTag, "Resident projection callback failed", failure)
        }
    }

    private fun destroyResidents() {
        if (residentsReleased) return
        residentsReleased = true
        pendingFrames = emptyList()
        latestResidentPoses = emptyList()
        runCatching {
            for (id in residents.keys.toList()) {
                destroyResident(id)
            }
            residentLoader?.destroy()
            residentResources?.destroy()
            residentProvider?.destroyMaterials()
            residentProvider?.destroy()
        }.onFailure { failure ->
            Log.w(logTag, "Could not release extruded residents", failure)
        }
        residentLoader = null
        residentResources = null
        residentProvider = null
    }

    companion object {
        private val filamentInitialized = AtomicBoolean(false)

        /** Flattened lens for the oblique 2.5D look (default ModelViewer is 28mm). */
        private const val DIGIFARM_FOCAL_MM = 50f
        private const val DIGIFARM_NEAR = 0.5f
        private const val DIGIFARM_FAR = 20f

        /** Extruded resident height in world units; the island is ~1.9 wide. */
        private const val RESIDENT_HEIGHT = 0.13f
        private const val SELECTED_HEIGHT = 0.15f
        private const val MAX_INTERPOLATED_STEP_SQ = 0.35f * 0.35f
        private const val MIN_MOVEMENT_DISTANCE = 0.0002f
        private const val MIN_MOVEMENT_DISTANCE_SQ = MIN_MOVEMENT_DISTANCE * MIN_MOVEMENT_DISTANCE
        private const val RESIDENT_STEP_NANOS = 1_100_000_000f
        private const val TURN_SPEED_RADIANS_PER_SECOND = 12f
        private const val FACING_FLIP_THRESHOLD = 0.16f
        private const val WALK_FRAME_NANOS = 250_000_000L
        private const val TRAIN_FRAME_NANOS = 340_000_000L
        private const val ACTION_FRAME_NANOS = 460_000_000L
        private const val IDLE_FRAME_NANOS = 950_000_000L
        private const val ISLAND_FLOAT_AMPLITUDE = 0.012f
        private const val ISLAND_FLOAT_SPEED = 0.52
        private const val GLOW_PULSE_SPEED = 1.15
        private const val GLOW_BASE_INTENSITY = 0.9f
        private val BLOCKS_PER_GROUP = intArrayOf(3, 3, 2, 2, 2, 2)

        // Orbit around the single island. Elevation stays above the horizon.
        private const val TARGET_X = 0f
        private const val TARGET_Y = -0.05f
        private const val TARGET_Z = 0f
        private const val HOME_YAW = 0f
        private const val HOME_PITCH = 0.64f
        private const val HOME_DIST = 3.86f
        private const val PITCH_MIN = 0.18f
        private const val PITCH_MAX = 1.22f
        private const val DIST_MIN = 1.2f
        private const val DIST_MAX = 4.8f
        private const val FOLLOW_LERP = 0.08f
        private const val YAW_GAIN = 0.0016f
        private const val PITCH_GAIN = 0.0013f
        private const val TOUCH_NONE = 0
        private const val TOUCH_DRAG = 1
        private const val TOUCH_PINCH = 2

        private const val CAMERA_NOTIFY_MIN_NANOS = 120_000_000L

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
