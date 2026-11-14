package com.tamerslab.battle.ui.offline

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.tamerslab.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tamerslab.battle.offline.core.BattleSnapshot
import com.tamerslab.battle.offline.core.CombatantState
import com.tamerslab.battle.offline.core.BattleFinisherKind
import com.tamerslab.battle.offline.core.BattleFinisherPhase
import com.tamerslab.battle.offline.core.BattlePosition
import com.tamerslab.rendering.HybridSceneKind
import com.tamerslab.rendering.applyHybridSceneProfile
import com.tamerslab.rendering.sprite3d.cameraAssistedSpriteYaw
import com.tamerslab.ui.theme.DeepPurpleBgAlt
import com.tamerslab.ui.theme.AppTheme
import com.tamerslab.ui.theme.LocalAppTheme
import com.tamerslab.rendering.EnvironmentThemeController
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Skybox
import com.google.android.filament.View as FilamentView
import com.google.android.filament.android.UiHelper
import com.tamerslab.ar.ArBattlePresentation
import com.tamerslab.ar.ArTrackingState
import com.tamerslab.ar.ArTransparentSwapChainInstaller
import com.tamerslab.ar.arSceneCamera
import com.tamerslab.ar.arSceneCameraPitch
import com.tamerslab.ar.arSceneCameraYaw
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.sqrt

/** Filament scene for the local arena. The battle clock remains independent of rendering. */
@Composable
fun OfflineBattleScene(
    snapshot: BattleSnapshot?,
    fighters: Map<String, BattleFighterPresentation>,
    manifest: OfflineArenaManifest?,
    sessionId: String?,
    onReady: (OfflineBattleSceneView?) -> Unit,
    onSceneReady: () -> Unit,
    onProjectionChanged: () -> Unit,
    onFighterTapped: (String) -> Unit,
    onAssetError: (String) -> Unit,
    renderingEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onReleased: () -> Unit = {},
    onCreated: () -> Unit = {},
    preparedForms: Map<String, BattleFighterPresentation> = emptyMap(),
    allowMotion: Boolean = true,
    /**
     * Phase 5.4 full-rate render channel. When provided (flag enabled), the scene
     * consumes the 60 Hz snapshots directly instead of waiting for Compose state
     * recomposition; [snapshot] keeps carrying the throttled UI-state snapshot.
     */
    sceneSnapshotFlow: StateFlow<BattleSnapshot?>? = null,
    /**
     * AR presentation: when non-null the scene becomes a transparent overlay whose camera is
     * driven by the ARCore pose instead of the cinematic/orbit camera. Null keeps the regular
     * opaque arena rendering and is the default so existing call sites are unaffected.
     */
    arPresentation: ArBattlePresentation? = null,
) {
    if (manifest == null) return
    val backgroundColor = DeepPurpleBgAlt
    val appTheme = LocalAppTheme.current
    val sceneViewRef = remember { mutableStateOf<OfflineBattleSceneView?>(null) }
    val latestFighters = rememberUpdatedState(fighters)
    val latestPreparedForms = rememberUpdatedState(preparedForms)
    val latestAllowMotion = rememberUpdatedState(allowMotion)
    val latestOnSceneReady = rememberUpdatedState(onSceneReady)
    val latestOnProjectionChanged = rememberUpdatedState(onProjectionChanged)
    val latestOnFighterTapped = rememberUpdatedState(onFighterTapped)
    val channel = sceneSnapshotFlow
    LaunchedEffect(channel, sessionId) {
        channel?.collect { sceneSnapshot ->
            sceneViewRef.value?.setBattleState(
                sceneSnapshot,
                latestFighters.value,
                sessionId,
                { latestOnSceneReady.value() },
                { latestOnProjectionChanged.value() },
                { fighterId -> latestOnFighterTapped.value(fighterId) },
                latestPreparedForms.value,
                latestAllowMotion.value,
            )
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).also { host ->
                runCatching { OfflineBattleSceneView(context, backgroundColor, appTheme, arPresentation != null) }
                    .onSuccess { scene ->
                        sceneViewRef.value = scene
                        scene.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        scene.setArPresentation(arPresentation)
                        onCreated()
                        host.addView(scene, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                        scene.loadArena(manifest, onAssetError)
                        host.post { onReady(scene) }
                    }
                    .onFailure { failure ->
                        Log.e("OfflineBattle3d", "Could not create arena renderer", failure)
                        host.post {
                            onReady(null)
                            onAssetError(failure.message ?: context.getString(R.string.ui_battle_error_renderer))
                        }
                    }
            }
        },
        update = { host ->
            val scene = host.getChildAt(0) as? OfflineBattleSceneView
            scene?.setArPresentation(arPresentation)
            scene?.setTheme(appTheme)
            scene?.setRenderingEnabled(renderingEnabled)
            scene?.setBattleState(
                snapshot, fighters, sessionId, onSceneReady, onProjectionChanged, onFighterTapped,
                preparedForms, allowMotion
            )
        },
        onRelease = { host ->
            val scene = host.getChildAt(0) as? OfflineBattleSceneView
            sceneViewRef.value = null
            scene?.releaseScene()
            val attached = scene?.isAttachedToWindow == true
            host.removeAllViews()
            if (!attached) scene?.releaseUnattached()
            // ModelViewer's 1.76.1 detach listener destroys the Engine before this posted ack.
            android.os.Handler(android.os.Looper.getMainLooper()).post { onReady(null); onReleased() }
        }
    )
}

class OfflineBattleSceneView(
    context: Context,
    backgroundColor: Color = AppTheme.VB_HELPER.palette.backgroundAlt,
    initialTheme: AppTheme = AppTheme.VB_HELPER,
    /** AR presentation: the viewer must be detached and hosted on a transparent swap chain. */
    private val arMode: Boolean = false,
) : TextureView(context) {
    private val engine: Engine
    private val viewer: ModelViewer
    private val environmentThemes: EnvironmentThemeController
    private var sceneTheme = initialTheme
    internal val appliedEnvironmentTheme get() = environmentThemes.appliedTheme
    internal val environmentThemeFailure get() = environmentThemes.failure
    internal val environmentMaterialNames get() = environmentThemes.boundMaterialNames
    internal val environmentLoadCount get() = environmentThemes.bindCount
    private val backgroundSkybox: Skybox
    private val radarSkybox: Skybox
    private var released = false
    private var attachedOnce = false
    private var arenaLoadGeneration = 0
    private var arenaLoaded = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var previousPinchDistance = 0f
    private var dragPointerId = -1
    private var pinchPointerId = -1
    private var cameraYaw = 0.0
    private var cameraPitch = 0.27
    private var effectCameraYaw = 0.0
    private var effectCameraPitch = 0.27
    private var cameraDistance = 13.5
    private var cameraTargetX = 0.0
    private var cameraTargetZ = 0.0
    private var cameraFocusFighterId: String? = null
    private var initialCameraFocusFighterId: String? = null
    private var initialCameraCompositionApplied = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var tapCandidate = false
    private var manifest: OfflineArenaManifest? = null
    private var cameraDirty = true
    private var lastCameraFrameNanos = 0L
    private var lastOrbitFrameNanos = 0L
    private var manualOrbitResumeAtNanos = 0L
    private var renderingEnabled = true
    private var projectionNotificationPending = true

    private var provider: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private val renderedFighters = linkedMapOf<String, RenderedFighter>()
    private val fighterFacings = linkedMapOf<String, BattleFighterFacing>()
    /** Retained base/result/transition instances. Movies toggle them; they never replace assets. */
    private val fighterPool = linkedMapOf<String, RenderedFighter>()
    private var desiredPreparedForms: Map<String, BattleFighterPresentation> = emptyMap()
    private var cinematicRenderer: BattleCinematicRenderer? = null
    private var cinematicPreparedKeys: Set<String> = emptySet()
    private var cinematicPreparationFailed = false
    private var allowCinematicMotion = true
    private var cinematicSequenceId: Long? = null
    private var cinematicSavedCamera: CinematicCameraPose? = null
    private var cinematicAttackSide: Int? = null
    private var cinematicTargetY: Double? = null
    private val renderedImpacts = linkedMapOf<Pair<String, String>, RenderedImpact>()
    private val failedImpactKeys = mutableSetOf<Pair<String, String>>()
    private var latestSnapshot: BattleSnapshot? = null
    private var desiredFighters: Map<String, BattleFighterPresentation> = emptyMap()
    private var latestSessionId: String? = null
    /** Session the accepted [latestSnapshot] belongs to; guards the throttled-UI stale check. */
    private var latestSnapshotSessionId: String? = null
    private var onAssetError: ((String) -> Unit)? = null
    private var onSceneReady: (() -> Unit)? = null
    private var onProjectionChanged: (() -> Unit)? = null
    private var onFighterTapped: ((String) -> Unit)? = null
    private var sceneReadyNotified = false
    internal var fighterAssetLoadCount = 0
        private set
    private var lastProjectionNotifyNanos = 0L
    /** (combatantId, setKey) pairs that failed to build; a new setKey always retries. */
    private var failedFighterKeys = linkedSetOf<Pair<String, String>>()
    private var arenaEnergyMaterial: MaterialInstance? = null
    private var arenaVoxelGroups: List<ArenaVoxelMotion> = emptyList()
    private var arenaVoxelCubes: List<ArenaVoxelMotion> = emptyList()
    private var arenaVoxelStartNanos = 0L
    /** AR overlay state; null keeps the regular opaque arena presentation. */
    private var arPresentation: ArBattlePresentation? = null
    /** Timestamp of the last ARCore camera frame applied, so repeated polls are cheap. */
    private var arCameraAppliedNanos = 0L
    /** Whether the fallback framing was applied before the first ARCore frame. */
    private var arDefaultCameraApplied = false
    /** AR renders at ~30 fps: ARCore needs the GPU budget for tracking on mid-range devices. */
    private var lastArRenderNanos = 0L
    /**
     * ModelViewer 1.76.1 always creates an opaque swap chain (`UiHelper.setOpaque(false)` is
     * ignored), which would cover the camera feed with black. While AR is active this shim swaps
     * in a CONFIG_TRANSPARENT chain and re-installs it whenever the surface is recreated.
     */
    private var arTransparentSwapChain: ArTransparentSwapChainInstaller? = null
    /** The AR view owns the detached ModelViewer/Engine teardown (no attach listener in AR). */
    private var viewerDestroyed = false
    private var arenaUsesRadarSkybox = false

    private data class ArenaVoxelMotion(
        val transformInstance: Int,
        val base: FloatArray,
        val animated: FloatArray,
        val amplitude: Float,
        val angularSpeed: Double,
        val phase: Double,
    )

    private data class RenderedFighter(
        val asset: FilamentAsset,
        val presentation: BattleFighterPresentation,
        val poses: Map<String, Int>,
        val renderables: List<Int>,
        val shadows: List<Int>,
        val materials: List<MaterialInstance>,
        val boundsCenter: FloatArray = asset.boundingBox.center.copyOf(),
        val boundsHalfExtent: FloatArray = asset.boundingBox.halfExtent.copyOf(),
        val worldTransform: FloatArray = FloatArray(16),
        var activePose: String? = null,
        var poseChangedAtNanos: Long = 0L
    )

    private data class RenderedImpact(
        val asset: FilamentAsset,
        val model: ByteArray,
        val renderables: List<Int>,
        val materials: List<MaterialInstance>,
        var impactId: Long? = null,
        var firstShownAtNanos: Long = 0
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (released || !renderingEnabled) return
            if (arPresentation != null) {
                // Cap the AR battle render at ~30 fps: the camera-background GL view is drawing
                // the ARCore feed on the same GPU, and starving that pipeline is what drops
                // ARCore tracking to PAUSED on mid-range phones.
                if (lastArRenderNanos != 0L && frameTimeNanos - lastArRenderNanos < AR_RENDER_INTERVAL_NANOS) {
                    Choreographer.getInstance().postFrameCallback(this)
                    return
                }
                lastArRenderNanos = frameTimeNanos
                // AR: the device pose owns the camera; orbit/focus/cinematic camera changes are
                // skipped (finisher actors and effects keep animating below).
                updateArCamera()
                cameraDirty = false
            } else {
                updateCinematicCamera()
                if (latestSnapshot?.finisher == null) {
                    if (allowCinematicMotion) updateAutomaticOrbit(frameTimeNanos)
                    updateFocusedCameraTarget(frameTimeNanos)
                }
            }
            if (allowCinematicMotion) {
                updateArenaEmission(frameTimeNanos)
                updateArenaVoxelMotion(frameTimeNanos)
            }
            projectionNotificationPending = projectionNotificationPending || cameraDirty
            if (arPresentation == null) applyCameraIfNeeded()
            syncFighters()
            fighterPool.values.filter { it !in renderedFighters.values }.forEach(::hideFighter)
            val poseTime = (latestSnapshot?.elapsedMillis ?: 0L) +
                (latestSnapshot?.finisher?.let { cinematicVisualSnapshot(it, allowCinematicMotion).elapsedMillis } ?: 0L)
            updateFighterTransforms(poseTime * 1_000_000L)
            updateCinematicActors()
            val effectAnchors = renderedFighters.mapValues { (_, fighter) -> frontEffectAnchor(fighter) }
            syncImpactAssets()
            if (latestSnapshot?.finisher == null) {
                latestSnapshot?.let { snapshot -> manifest?.let { arena ->
                    cinematicRenderer?.updateStartup(snapshot, desiredFighters, arena,
                        effectCameraYaw, effectCameraPitch, effectAnchors, allowCinematicMotion)
                } }
                updateImpactTransforms(frameTimeNanos)
            } else {
                renderedImpacts.values.forEach { effect -> effect.renderables.forEach {
                    engine.renderableManager.setLayerMask(it, 0xFF, 0)
                } }
                latestSnapshot?.let { snapshot -> manifest?.let { arena ->
                    cinematicRenderer?.update(snapshot, desiredFighters, desiredPreparedForms,
                        arena, effectCameraYaw, effectCameraPitch, allowCinematicMotion, effectAnchors)
                } }
            }
            val rendered = if (arPresentation != null) {
                // The transparent host owns this TextureView's SurfaceTexture; rendering is only
                // legal on frames where it reports a live swap chain (never hand Filament a dead
                // or missing one). `rendered = false` simply skips the frame; the loop continues.
                val ready = arTransparentSwapChain?.ensure(viewer, this@OfflineBattleSceneView) == true
                if (ready) viewer.render(frameTimeNanos) else false
            } else {
                viewer.render(frameTimeNanos)
            }
            environmentThemes.onFrame(viewer.progress >= 1f)
            if (projectionNotificationPending && frameTimeNanos - lastProjectionNotifyNanos >= PROJECTION_NOTIFY_NANOS) {
                lastProjectionNotifyNanos = frameTimeNanos
                projectionNotificationPending = false
                onProjectionChanged?.invoke()
            }
            // Cosmetic palette preparation must not gate combat readiness or trap the loading overlay.
            if (!sceneReadyNotified && rendered && viewer.progress >= 1f && arenaLoaded && desiredFighters.isNotEmpty() &&
                desiredFighters.keys.all { it in renderedFighters } &&
                desiredPreparedForms.keys.all { it in fighterPool } &&
                cinematicPreparedKeys.containsAll(desiredFighters.values.map { it.setKey } + desiredPreparedForms.keys)) {
                sceneReadyNotified = true
                onSceneReady?.invoke()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        ensureFilament()
        engine = Engine.create()
        // Transparency-capable swap chain: the AR battle presentation draws this scene over the
        // camera feed, so the underlying window surface must carry alpha. Filament's UiHelper is
        // opaque by default and decides the swap chain flags at attach time, which is why this
        // must happen before the ModelViewer attaches. The non-AR battle is unaffected because it
        // renders an opaque skybox/arena over the whole viewport.
        val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also { it.setOpaque(false) }
        // AR: build the viewer detached (`ModelViewer(engine, uiHelper)`) so it never opens its own
        // opaque window surface; ArTransparentSwapChainInstaller owns this TextureView's surface.
        viewer = if (arMode) {
            ModelViewer(engine, uiHelper)
        } else {
            ModelViewer(this, engine, uiHelper, null)
        }
        environmentThemes = EnvironmentThemeController(context, engine, initialTheme)
        viewer.view.applyHybridSceneProfile(HybridSceneKind.BATTLE)
        arTransparentSwapChain = if (arMode) ArTransparentSwapChainInstaller(engine) else null
        arTransparentSwapChain?.start(viewer, this)
        val backdrop = Colors.toLinear(
            Colors.RgbType.SRGB,
            backgroundColor.red, backgroundColor.green, backgroundColor.blue
        )
        backgroundSkybox = Skybox.Builder()
            .color(backdrop[0], backdrop[1], backdrop[2], 1f)
            .build(engine)
            .also { viewer.scene.skybox = it }
        radarSkybox = Skybox.Builder()
            .color(0f, 0f, 0f, 1f)
            .build(engine)
        updateThemeBackdrop()
        contentDescription = context.getString(R.string.ui_battle_scene_description)
        isFocusable = true
        isOpaque = false
        setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) performClick()
            // AR overlays must not steal drags from the Compose HUD; the camera is pose-driven.
            if (arPresentation == null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            handleCameraTouch(event)
        }
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun loadArena(manifest: OfflineArenaManifest, onError: (String) -> Unit) {
        if (released) return
        this.manifest = manifest
        cameraPitch = manifest.cameraPitchRadians
        cameraDistance = manifest.cameraDistance
        cameraDirty = true
        onAssetError = onError
        val generation = ++arenaLoadGeneration
        val assetPath = manifest.assetPath
        Thread({
            val bytes = runCatching {
                context.assets.open(assetPath).use { it.readBytes() }
            }.getOrElse { failure ->
                Log.e(TAG, "Could not read $assetPath", failure)
                post { if (!released && generation == arenaLoadGeneration) onError(context.getString(R.string.ui_battle_error_arena_missing, failure.message)) }
                return@Thread
            }
            post {
                if (released || generation != arenaLoadGeneration) return@post
                runCatching {
                    val buffer = ByteBuffer.allocateDirect(bytes.size)
                        .order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
                    viewer.loadModelGlb(buffer)
                    val arena = checkNotNull(viewer.asset) { "O Coliseu não contém uma cena renderizável." }
                    viewer.clearRootTransform()
                    Matrix.setIdentityM(arenaTransform, 0)
                    Matrix.scaleM(arenaTransform, 0, manifest.visualScale, manifest.visualScale, manifest.visualScale)
                    val transform = engine.transformManager.getInstance(arena.root)
                    check(transform != 0) { "A cena do Coliseu não possui transform raiz." }
                    engine.transformManager.setTransform(transform, arenaTransform)
                    configureArenaEmission(arena)
                    configureArenaVoxelMotion(arena)
                    environmentThemes.bind(arena, assetPath)
                    arenaLoaded = true
                    cameraDirty = true
                    Log.i(TAG, "Loaded $assetPath (${bytes.size} bytes)")
                }.onFailure { failure ->
                    Log.e(TAG, "Could not load arena GLB", failure)
                    onError(context.getString(R.string.ui_battle_error_colosseum_load, failure.message))
                }
            }
        }, "offline-arena-loader").start()
    }

    fun setBattleState(
        snapshot: BattleSnapshot?,
        fighters: Map<String, BattleFighterPresentation>,
        sessionId: String?,
        onReady: () -> Unit,
        onProjectionChanged: () -> Unit,
        onFighterTapped: (String) -> Unit,
        preparedForms: Map<String, BattleFighterPresentation> = emptyMap(),
        allowMotion: Boolean = true,
    ) {
        if (released) return
        val changingSession = latestSessionId != sessionId
        if (changingSession) {
            fighterFacings.clear()
            val reuse = canReuseBattleRenderAssets(sceneReadyNotified && !cinematicPreparationFailed,
                desiredFighters, fighters, desiredPreparedForms, preparedForms)
            if (latestSessionId != null && !reuse) releaseFighters()
            if (!reuse) cinematicAttackSide = null
            if (reuse) {
                // Reset match-local poses/effects without decoding or uploading any GLB again.
                cinematicRenderer?.hide()
                fighterPool.values.forEach { fighter ->
                    hideFighter(fighter)
                    fighter.activePose = null
                    fighter.poseChangedAtNanos = 0L
                }
                renderedFighters.clear()
                renderedImpacts.values.forEach { impact ->
                    impact.impactId = null
                    impact.firstShownAtNanos = 0L
                    impact.renderables.forEach { engine.renderableManager.setLayerMask(it, 0xFF, 0) }
                }
                failedImpactKeys.clear()
            }
            cinematicSequenceId = null
            cinematicSavedCamera = null
            cinematicTargetY = null
            cameraFocusFighterId = null
            initialCameraFocusFighterId = null
            initialCameraCompositionApplied = false
            cameraTargetX = 0.0
            cameraTargetZ = 0.0
            lastCameraFrameNanos = 0L
            lastOrbitFrameNanos = 0L
            manualOrbitResumeAtNanos = 0L
            cameraDirty = true
        }
        if (changingSession || desiredFighters.keys != fighters.keys ||
            desiredFighters.any { (id, old) -> fighters[id]?.setKey != old.setKey }) {
            sceneReadyNotified = false
        }
        latestSessionId = sessionId
        if (changingSession) {
            // Never compare a new session's elapsed time against the settled session it replaces.
            latestSnapshot = null
            latestSnapshotSessionId = null
        }
        // The throttled Compose UI state can lag the full-rate scene channel; never
        // let an older simulation snapshot rewind the scene. Elapsed time is
        // monotonic within one session (it only repeats while paused), so the guard
        // only applies when both snapshots belong to the same session.
        val staleFromThrottledUi = !changingSession && snapshot != null &&
            latestSnapshotSessionId == sessionId && latestSnapshot != null &&
            snapshot.elapsedMillis < latestSnapshot!!.elapsedMillis
        if (!staleFromThrottledUi) {
            latestSnapshot = snapshot
            latestSnapshotSessionId = if (snapshot != null) sessionId else null
        }
        desiredFighters = fighters
        desiredPreparedForms = preparedForms
        allowCinematicMotion = allowMotion
        onSceneReady = onReady
        this.onProjectionChanged = onProjectionChanged
        this.onFighterTapped = onFighterTapped
        if (!initialCameraCompositionApplied && snapshot != null) {
            val livingAllies = snapshot.alliedMembers.filter {
                it.health > 0 && it.state != CombatantState.DEFEATED
            }
            val livingOpponents = snapshot.opposingMembers.filter {
                it.health > 0 && it.state != CombatantState.DEFEATED
            }
            val livingCombatants = livingAllies + livingOpponents
            if (livingCombatants.isNotEmpty()) {
                val cues = livingCombatants.map {
                    BattleCameraCue(it.combatantId, it.position, it.state)
                }
                // Team battles open on an allied Digimon. A 1v1 opens centered
                // between both fighters, preserving the established framing.
                initialCameraFocusFighterId = if (cues.size > 2) {
                    (livingAllies.firstOrNull() ?: livingOpponents.first()).combatantId
                } else null
                val target = chooseBattleCameraTarget(
                    manualFighterId = null,
                    cues = cues,
                    impacts = emptyList(),
                    initialFocusFighterId = initialCameraFocusFighterId,
                )
                val scale = manifest?.positionScale?.toDouble() ?: 1.0
                cameraTargetX = target.x * scale
                cameraTargetZ = target.z * scale
                cameraDirty = true
                initialCameraCompositionApplied = true
            }
        }
    }

    fun setRenderingEnabled(enabled: Boolean) {
        if (released || enabled == renderingEnabled) return
        renderingEnabled = enabled
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        if (enabled) {
            cameraDirty = true
            lastOrbitFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun setTheme(theme: AppTheme) {
        if (released || sceneTheme == theme) return
        sceneTheme = theme
        updateThemeBackdrop()
        environmentThemes.setTheme(theme)
    }

    /**
     * Switches between the regular opaque arena and the AR overlay presentation.
     *
     * The same Filament engine, scene and view are reused; only the backdrop (blend mode, clear
     * colour, skybox), the arena occluders and the camera source change. Passing null restores
     * the regular presentation so non-AR battles are unaffected.
     */
    fun setArPresentation(presentation: ArBattlePresentation?) {
        if (released || arPresentation === presentation) return
        val wasAr = arPresentation != null
        arPresentation = presentation
        val isAr = presentation != null
        if (wasAr == isAr) return
        arCameraAppliedNanos = 0L
        if (isAr) {
            applyArBackdrop(true)
            // Apply the occluders again if the arena was loaded before AR was switched on.
            viewer.asset?.let { arena -> hideArArenaOccluders(arena) }
        } else {
            arTransparentSwapChain?.stop()
            arTransparentSwapChain = null
            applyArBackdrop(false)
            cameraYaw = 0.0
            cameraPitch = manifest?.cameraPitchRadians ?: 0.27
            cameraDistance = manifest?.cameraDistance ?: 19.0
            cameraTargetX = 0.0
            cameraTargetZ = 0.0
            cameraDirty = true
            lastCameraFrameNanos = 0L
            lastOrbitFrameNanos = 0L
            manualOrbitResumeAtNanos = 0L
        }
    }

    /** Camera feed as backdrop: translucent view, transparent clear colour, no skybox. */
    private fun applyArBackdrop(ar: Boolean) {
        if (ar) {
            viewer.view.blendMode = FilamentView.BlendMode.TRANSLUCENT
            viewer.scene.skybox = null
            // AR renders a camera feed and a couple of sprites: full post-processing is wasted GPU
            // time and competes with ARCore's tracking pipeline on mid-range devices.
            viewer.view.setPostProcessingEnabled(false)
            viewer.view.setAntiAliasing(FilamentView.AntiAliasing.NONE)
            viewer.view.setDithering(FilamentView.Dithering.NONE)
            val options = Renderer.ClearOptions()
            options.clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            options.clear = true
            options.discard = true
            viewer.renderer.clearOptions = options
        } else {
            viewer.view.blendMode = FilamentView.BlendMode.OPAQUE
            viewer.renderer.clearOptions = Renderer.ClearOptions()
            viewer.scene.skybox = if (arenaUsesRadarSkybox) radarSkybox else backgroundSkybox
            viewer.view.applyHybridSceneProfile(HybridSceneKind.BATTLE)
        }
    }

    /**
     * Applies the latest ARCore camera frame. The pose is applied even while tracking is PAUSED
     * (ARCore keeps returning its last pose): otherwise a fight started from a paused scan would
     * render from the default ModelViewer camera and the arena/fighters would be off-screen.
     * Until the first frame arrives a sensible default framing keeps the scene visible.
     */
    private fun updateArCamera() {
        val presentation = arPresentation ?: return
        val frame = presentation.cameraSource.latestFrame()?.camera
        if (frame == null) {
            applyDefaultArCameraIfNeeded()
            return
        }
        if (frame.timestampNanos == arCameraAppliedNanos) return
        arCameraAppliedNanos = frame.timestampNanos
        arDefaultCameraApplied = false
        val sceneCamera = arSceneCamera(frame, presentation.placement)
        viewer.camera.setModelMatrix(sceneCamera.modelMatrix)
        // The Filament viewport is the battle stage, which can differ from the camera image
        // aspect (the stage is square); project with the stage aspect so the rendered geometry
        // lines up with the cropped camera background.
        val stageAspect = if (viewportWidth > 0 && viewportHeight > 0) {
            viewportWidth.toFloat() / viewportHeight.toFloat()
        } else {
            sceneCamera.aspect
        }
        // Keep ModelViewer's lens fields coherent with the AR clip planes. They are only
        // meaningful once the surface has a viewport (the setters re-project using it).
        if (viewportWidth > 0 && viewportHeight > 0) {
            if (viewer.cameraNear != sceneCamera.nearSceneUnits) viewer.cameraNear = sceneCamera.nearSceneUnits
            if (viewer.cameraFar != sceneCamera.farSceneUnits) viewer.cameraFar = sceneCamera.farSceneUnits
        }
        viewer.camera.setProjection(
            sceneCamera.fovYDegrees.toDouble(),
            stageAspect.toDouble(),
            sceneCamera.nearSceneUnits.toDouble(),
            sceneCamera.farSceneUnits.toDouble(),
            Camera.Fov.VERTICAL,
        )
        // Sprite fighters, impact billboards and finisher effects face the device camera.
        cameraYaw = arSceneCameraYaw(sceneCamera).toDouble()
        cameraPitch = arSceneCameraPitch(sceneCamera).toDouble()
        effectCameraYaw = cameraYaw
        effectCameraPitch = cameraPitch
        projectionNotificationPending = true
    }

    /**
     * Fallback framing used before the first ARCore frame is available (or if the camera view
     * never publishes one): look at the arena from a few scene units away so a battle is never
     * rendered into an invisible default camera.
     */
    private fun applyDefaultArCameraIfNeeded() {
        if (arDefaultCameraApplied || viewportWidth <= 0 || viewportHeight <= 0) return
        arDefaultCameraApplied = true
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        viewer.camera.lookAt(
            0.0, 8.0, 16.0,
            0.0, 0.9, 0.0,
            0.0, 1.0, 0.0,
        )
        viewer.camera.setProjection(60.0, aspect.toDouble(), 0.1, 500.0, Camera.Fov.VERTICAL)
        cameraYaw = 0.0
        cameraPitch = 0.27
        effectCameraYaw = cameraYaw
        effectCameraPitch = cameraPitch
        projectionNotificationPending = true
    }

    /**
     * AR shows only the two fighters and the ring that delimits the battle space: the camera feed
     * is the backdrop, so the floor, grid, dome and voxel debris are hidden. The ring is the
     * `battle_boundary` material primitive of the shared arena mesh — exactly the playable-radius
     * circle authored with the GLB.
     */
    private fun hideArArenaOccluders(arena: FilamentAsset) {
        val renderables = engine.renderableManager
        for (entity in arena.entities) {
            if (!renderables.hasComponent(entity)) continue
            val instance = renderables.getInstance(entity)
            if (instance == 0) continue
            var keep = false
            for (primitive in 0 until renderables.getPrimitiveCount(instance)) {
                val material = renderables.getMaterialInstanceAt(instance, primitive).material
                if (material.name == AR_BOUNDARY_MATERIAL) {
                    keep = true
                    break
                }
            }
            renderables.setLayerMask(instance, 0xFF, if (keep) 0x01 else 0)
        }
    }

    private fun updateThemeBackdrop() {
        val c = sceneTheme.palette.backgroundAlt
        val linear = Colors.toLinear(Colors.RgbType.SRGB, c.red, c.green, c.blue)
        backgroundSkybox.setColor(linear[0], linear[1], linear[2], 1f)
        if (sceneTheme == AppTheme.VB_HELPER) radarSkybox.setColor(0f, 0f, 0f, 1f)
        else radarSkybox.setColor(linear[0], linear[1], linear[2], 1f)
    }

    fun retryScene() {
        if (released) return
        sceneReadyNotified = false
        failedFighterKeys.clear()
        arenaLoaded = false
        arenaEnergyMaterial = null
        arenaVoxelGroups = emptyList()
        arenaVoxelCubes = emptyList()
        environmentThemes.clearEnvironment()
        releaseFighters()
        runCatching { if (viewer.asset != null) viewer.destroyModel() }
        val currentManifest = manifest ?: return
        onAssetError?.let { loadArena(currentManifest, it) }
    }

    fun resetCamera() {
        // In AR the device pose owns the camera; there is nothing to recentre.
        if (arPresentation != null) return
        cameraYaw = 0.0
        cameraPitch = manifest?.cameraPitchRadians ?: 0.27
        cameraDistance = manifest?.cameraDistance ?: 19.0
        cameraFocusFighterId = null
        cameraTargetX = 0.0
        cameraTargetZ = 0.0
        lastOrbitFrameNanos = 0L
        manualOrbitResumeAtNanos = 0L
        cameraDirty = true
    }

    /** Projects arena coordinates into TextureView pixels for the Compose HUD. */
    fun projectBattlePosition(x: Float, y: Float, z: Float): Pair<Float, Float>? {
        val currentManifest = manifest ?: return null
        if (viewportWidth <= 0 || viewportHeight <= 0 || !arenaLoaded) return null
        return runCatching {
            readViewProjection(viewProjection)
            Matrix.multiplyMV(clipPosition, 0, viewProjection, 0,
                floatArrayOf(x * currentManifest.positionScale, y, z * currentManifest.positionScale, 1f), 0)
            if (clipPosition[3] <= 0f) return null
            val ndcX = clipPosition[0] / clipPosition[3]
            val ndcY = clipPosition[1] / clipPosition[3]
            if (ndcX !in -1.15f..1.15f || ndcY !in -1.15f..1.15f) return null
            (ndcX * 0.5f + 0.5f) * viewportWidth to (1f - (ndcY * 0.5f + 0.5f)) * viewportHeight
        }.getOrNull()
    }

    /**
     * Picks the arena point under a tap, in simulator units ([BattlePosition] on the floor
     * `y = 0`).
     *
     * [x] and [y] are pixels in this view's coordinate space (the [MotionEvent] system, i.e. what
     * the Compose overlay under the same bounds reports). The camera's projection is short and
     * perspective, and Filament 1.76.1 remaps depth, so raying the inverse matrix made far taps
     * land closer than the player aimed. Instead the pick projects a grid of floor candidates with
     * the exact forward matrix the projected HUD already uses and returns the candidate nearest to
     * the tap, refined twice around the winner. What the player sees is what they get.
     *
     * Returns null when the scene/camera is not ready or when no floor point is on screen.
     * Points outside the playable radius are not returned (the search is bounded to the arena).
     * Main thread only: it reads the same camera state the frame callback writes.
     */
    fun pickArenaPoint(x: Float, y: Float): BattlePosition? {
        if (released) return null
        val currentManifest = manifest ?: return null
        if (!arenaLoaded || viewportWidth <= 0 || viewportHeight <= 0) return null
        if (!x.isFinite() || !y.isFinite()) return null
        val radius = currentManifest.playableRadius
        if (radius <= 0f) return null
        var bestX = 0f
        var bestZ = 0f
        var bestDistanceSq = Float.MAX_VALUE
        val steps = 24
        val cell = radius * 2f / steps
        for (i in 0..steps) {
            for (j in 0..steps) {
                val px = -radius + i * cell
                val pz = -radius + j * cell
                if (px * px + pz * pz > radius * radius) continue
                val screen = projectBattlePosition(px, 0f, pz) ?: continue
                val dx = screen.first - x
                val dy = screen.second - y
                val distanceSq = dx * dx + dy * dy
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq
                    bestX = px
                    bestZ = pz
                }
            }
        }
        if (bestDistanceSq == Float.MAX_VALUE) return null
        var refine = cell
        repeat(3) {
            refine /= 4f
            var nextX = bestX
            var nextZ = bestZ
            var nextDistanceSq = bestDistanceSq
            for (i in -2..2) {
                for (j in -2..2) {
                    val px = (bestX + i * refine).coerceIn(-radius, radius)
                    val pz = (bestZ + j * refine).coerceIn(-radius, radius)
                    if (px * px + pz * pz > radius * radius) continue
                    val screen = projectBattlePosition(px, 0f, pz) ?: continue
                    val dx = screen.first - x
                    val dy = screen.second - y
                    val distanceSq = dx * dx + dy * dy
                    if (distanceSq < nextDistanceSq) {
                        nextDistanceSq = distanceSq
                        nextX = px
                        nextZ = pz
                    }
                }
            }
            if (nextDistanceSq < bestDistanceSq) {
                bestDistanceSq = nextDistanceSq
                bestX = nextX
                bestZ = nextZ
            }
        }
        return BattlePosition(bestX, bestZ)
    }

    /** Fills [out] with the camera's current column-major projection * view matrix. */
    private fun readViewProjection(out: FloatArray) {
        val view = viewer.camera.getViewMatrix(FloatArray(16))
        val projection = viewer.camera.getProjectionMatrix(DoubleArray(16))
        val projectionFloat = FloatArray(16) { projection[it].toFloat() }
        Matrix.multiplyMM(out, 0, projectionFloat, 0, view, 0)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        viewportWidth = width
        viewportHeight = height
        viewer.cameraFocalLength = 28f
        viewer.cameraNear = 0.05f
        viewer.cameraFar = 140f
        cameraDirty = true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyCameraIfNeeded() {
        if (!cameraDirty || viewportWidth <= 0 || viewportHeight <= 0) return
        cameraDirty = false
        val sequence = latestSnapshot?.finisher
        val impact = latestSnapshot?.impacts?.maxByOrNull { it.impactId }.takeIf { sequence == null }
        val shake = if (allowCinematicMotion) {
            impact?.let { battleImpactShake(it.impactId, it.remainingMillis, it.critical) }
                ?: BattleCameraShake(0.0, 0.0)
        } else {
            BattleCameraShake(0.0, 0.0)
        }
        val horizontal = cameraDistance * kotlin.math.cos(cameraPitch)
        val desiredEyeX = cameraTargetX + kotlin.math.sin(cameraYaw) * horizontal
        val desiredEyeZ = cameraTargetZ + kotlin.math.cos(cameraYaw) * horizontal
        val cameraEye = manifest?.let {
            constrainArenaCameraEye(desiredEyeX, desiredEyeZ, it.cameraCollisionRadius)
        } ?: ArenaCameraPoint(desiredEyeX, desiredEyeZ)
        val targetY = cinematicTargetY ?: manifest?.cameraTargetY ?: 0.7
        val eyeY = targetY + cameraDistance * kotlin.math.sin(cameraPitch)
        effectCameraYaw = kotlin.math.atan2(cameraEye.x-cameraTargetX, cameraEye.z-cameraTargetZ)
        effectCameraPitch = kotlin.math.atan2(eyeY-targetY,
            kotlin.math.hypot(cameraEye.x-cameraTargetX, cameraEye.z-cameraTargetZ))
        viewer.camera.lookAt(
            cameraEye.x + shake.x, eyeY, cameraEye.z + shake.z,
            cameraTargetX + shake.x, targetY, cameraTargetZ + shake.z,
            0.0, 1.0, 0.0,
        )
        if (impact != null && impact.remainingMillis > 0L) {
            cameraDirty = true
            projectionNotificationPending = true
        }
    }

    private fun updateFocusedCameraTarget(frameTimeNanos: Long) {
        val currentManifest = manifest ?: return
        val snapshot = latestSnapshot ?: return
        val livingAllies = snapshot.alliedMembers.filter {
            it.health > 0 && it.state != CombatantState.DEFEATED
        }
        val livingOpponents = snapshot.opposingMembers.filter {
            it.health > 0 && it.state != CombatantState.DEFEATED
        }
        val cues = (livingAllies + livingOpponents).map {
            BattleCameraCue(it.combatantId, it.position, it.state)
        }
        if (cameraFocusFighterId != null && cues.none { it.fighterId == cameraFocusFighterId }) {
            cameraFocusFighterId = null
        }
        if (initialCameraFocusFighterId != null && cues.none {
                it.fighterId == initialCameraFocusFighterId
            }) {
            initialCameraFocusFighterId = (livingAllies.firstOrNull() ?: livingOpponents.firstOrNull())
                ?.combatantId
        }
        val target = chooseBattleCameraTarget(
            manualFighterId = cameraFocusFighterId,
            cues = cues,
            impacts = snapshot.impacts,
            initialFocusFighterId = initialCameraFocusFighterId,
        )
        val positionScale = currentManifest.positionScale.toDouble()
        val desiredX = target.x.toDouble() * positionScale
        val desiredZ = target.z.toDouble() * positionScale
        val deltaSeconds = if (lastCameraFrameNanos == 0L) 1.0 / 60.0 else
            (frameTimeNanos - lastCameraFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0
        lastCameraFrameNanos = frameTimeNanos
        val nextX = approachCameraAxis(cameraTargetX, desiredX, deltaSeconds)
        val nextZ = approachCameraAxis(cameraTargetZ, desiredZ, deltaSeconds)
        if (abs(nextX - cameraTargetX) > CAMERA_FOCUS_EPSILON ||
            abs(nextZ - cameraTargetZ) > CAMERA_FOCUS_EPSILON) {
            cameraTargetX = nextX
            cameraTargetZ = nextZ
            cameraDirty = true
        }
    }

    private fun updateAutomaticOrbit(frameTimeNanos: Long) {
        val previous = lastOrbitFrameNanos
        lastOrbitFrameNanos = frameTimeNanos
        if (previous == 0L) return
        val deltaSeconds = (frameTimeNanos - previous).coerceAtLeast(0L) / 1_000_000_000.0
        val orbitBlend = when {
            dragPointerId >= 0 || pinchPointerId >= 0 -> 0.0
            frameTimeNanos < manualOrbitResumeAtNanos -> 0.0
            else -> (frameTimeNanos - manualOrbitResumeAtNanos)
                .toDouble() / ORBIT_ACCELERATION_NANOS
        }
        val nextYaw = advanceBattleCameraYaw(cameraYaw, deltaSeconds, orbitBlend)
        if (nextYaw != cameraYaw) {
            cameraYaw = nextYaw
            cameraDirty = true
        }
    }

    private fun pauseAutomaticOrbit() {
        manualOrbitResumeAtNanos = System.nanoTime() + MANUAL_ORBIT_PAUSE_NANOS
    }

    private fun configureArenaEmission(arena: FilamentAsset) {
        val dome = arena.getFirstEntityByName("Sphere001")
        val renderables = engine.renderableManager
        val isRadarArena = arena.getFirstEntityByName("RadarVoxelFragments") != 0
        arenaUsesRadarSkybox = isRadarArena
        if (isRadarArena && dome != 0 && renderables.hasComponent(dome)) {
            // The radar's enclosing sphere carries the RadarSkyDome mosaic; from inside it
            // only cut a hard band across the horizon and hid the themed backdrop, so the
            // radar scene now renders without it (the skybox is the whole backdrop).
            renderables.setLayerMask(renderables.getInstance(dome), 0xFF, 0)
        }
        val arActive = arPresentation != null
        // AR draws over the camera feed: no skybox, and no emissive environment pulse that
        // would wash out the physical background.
        viewer.scene.skybox = when {
            arActive -> null
            isRadarArena -> radarSkybox
            else -> backgroundSkybox
        }
        arenaEnergyMaterial = if (!arActive && !isRadarArena && dome != 0 && renderables.hasComponent(dome)) {
            val instance = renderables.getInstance(dome)
            if (instance != 0 && renderables.getPrimitiveCount(instance) > 0) {
                renderables.getMaterialInstanceAt(instance, 0)
                    .takeIf { it.material.hasParameter("emissiveFactor") }
            } else null
        } else null
        if (arActive) hideArArenaOccluders(arena)
    }

    private fun configureArenaVoxelMotion(arena: FilamentAsset) {
        val transforms = engine.transformManager
        val groups = ArrayList<ArenaVoxelMotion>()
        val cubes = ArrayList<ArenaVoxelMotion>()
        val cubeCounts = intArrayOf(4, 3, 4, 3, 4, 3, 4, 3, 3)
        for (groupIndex in 1..cubeCounts.size) {
            val suffix = groupIndex.toString().padStart(2, '0')
            val groupEntity = arena.getFirstEntityByName("RadarVoxelFragmentsFar$suffix")
            if (groupEntity == 0) continue
            val groupTransform = transforms.getInstance(groupEntity)
            if (groupTransform != 0) {
                val base = transforms.getTransform(groupTransform, FloatArray(16))
                groups += ArenaVoxelMotion(
                    groupTransform, base, base.copyOf(),
                    amplitude = 0.14f + (groupIndex % 4) * 0.018f,
                    angularSpeed = 0.72 + (groupIndex % 5) * 0.09,
                    phase = groupIndex * 1.17,
                )
            }
            for (blockIndex in 1..cubeCounts[groupIndex - 1]) {
                val blockName = "RadarVoxelBlockFar${suffix}_${blockIndex.toString().padStart(2, '0')}"
                val blockEntity = arena.getFirstEntityByName(blockName)
                if (blockEntity == 0) continue
                val blockTransform = transforms.getInstance(blockEntity)
                if (blockTransform == 0) continue
                val base = transforms.getTransform(blockTransform, FloatArray(16))
                val ordinal = cubes.size
                cubes += ArenaVoxelMotion(
                    blockTransform, base, base.copyOf(),
                    amplitude = 0.045f + (ordinal % 5) * 0.008f,
                    angularSpeed = 1.05 + (ordinal % 7) * 0.10,
                    phase = ordinal * 1.39,
                )
            }
        }
        arenaVoxelGroups = groups
        arenaVoxelCubes = cubes
        arenaVoxelStartNanos = 0L
    }

    private fun updateArenaVoxelMotion(frameTimeNanos: Long) {
        // The AR overlay keeps the arena quiet so the camera feed stays readable; the transforms
        // keep their last (or authored) values and resume untouched when AR is disabled.
        if (arPresentation != null) return
        if (arenaVoxelGroups.isEmpty() && arenaVoxelCubes.isEmpty()) return
        if (arenaVoxelStartNanos == 0L) arenaVoxelStartNanos = frameTimeNanos
        val elapsed = (frameTimeNanos - arenaVoxelStartNanos) / 1_000_000_000.0
        val transforms = engine.transformManager
        for (group in arenaVoxelGroups) {
            group.animated[12] = group.base[12] +
                (kotlin.math.sin(elapsed * group.angularSpeed * 0.58 + group.phase) * 0.012).toFloat()
            group.animated[13] = group.base[13] +
                (kotlin.math.sin(elapsed * group.angularSpeed + group.phase) * group.amplitude).toFloat()
            group.animated[14] = group.base[14] +
                (kotlin.math.cos(elapsed * group.angularSpeed * 0.47 + group.phase) * 0.012).toFloat()
            transforms.setTransform(group.transformInstance, group.animated)
        }
        for (cube in arenaVoxelCubes) {
            cube.animated[13] = cube.base[13] +
                (kotlin.math.sin(elapsed * cube.angularSpeed + cube.phase) * cube.amplitude).toFloat()
            transforms.setTransform(cube.transformInstance, cube.animated)
        }
    }

    private fun updateArenaEmission(frameTimeNanos: Long) {
        if (arPresentation != null) return
        val elapsed = frameTimeNanos / 1_000_000_000.0
        val pulse = (0.5 + 0.5 * kotlin.math.sin(elapsed * ARENA_ENERGY_PULSE_SPEED)).toFloat()
        val strength = ARENA_ENERGY_BASE + ARENA_ENERGY_AMPLITUDE * pulse
        arenaEnergyMaterial?.let { if (environmentThemes.updateEmission(it, strength)) return }
        arenaEnergyMaterial?.setParameter(
            "emissiveFactor",
            0.50f * strength,
            0.16f * strength,
            1.0f * strength,
        )
    }

    private fun centerCameraOnFighter(fighterId: String): Boolean {
        val fighter = latestSnapshot?.let { snapshot ->
            (snapshot.alliedMembers + snapshot.opposingMembers).firstOrNull { it.combatantId == fighterId }
        } ?: return false
        cameraFocusFighterId = fighterId
        cameraDirty = true
        return true
    }

    private fun pickFighterAt(screenX: Float, screenY: Float): String? {
        val currentManifest = manifest ?: return null
        val combatants = latestSnapshot?.let { it.alliedMembers + it.opposingMembers } ?: return null
        val touchRadius = 24f * resources.displayMetrics.density
        return combatants.mapNotNull { fighter ->
            val visualScale = desiredFighters[fighter.combatantId]?.visualScaleMultiplier ?: 1f
            val foot = projectBattlePosition(fighter.position.x, 0f, fighter.position.z) ?: return@mapNotNull null
            val head = projectBattlePosition(
                fighter.position.x,
                currentManifest.fighterScale * visualScale,
                fighter.position.z
            ) ?: return@mapNotNull null
            val bodyTop = minOf(head.second, foot.second)
            val bodyBottom = maxOf(head.second, foot.second)
            val halfWidth = maxOf(abs(bodyBottom - bodyTop) * 0.48f, touchRadius)
            val left = head.first - halfWidth - touchRadius
            val right = head.first + halfWidth + touchRadius
            val top = bodyTop - touchRadius
            val bottom = bodyBottom + touchRadius
            if (screenX !in left..right || screenY !in top..bottom) return@mapNotNull null
            val centerX = head.first
            val centerY = (bodyTop + bodyBottom) * 0.5f
            val dx = screenX - centerX
            val dy = screenY - centerY
            fighter.combatantId to dx * dx + dy * dy
        }.minByOrNull { it.second }?.first
    }

    private fun handleCameraTouch(event: MotionEvent): Boolean {
        // In AR the orbit/zoom gestures would fight the device pose; only fighter taps survive.
        if (arPresentation != null) return handleArSceneTouch(event)
        if (latestSnapshot?.finisher != null) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pauseAutomaticOrbit()
                dragPointerId = event.getPointerId(0)
                lastPointerX = event.x
                lastPointerY = event.y
                touchStartX = event.x
                touchStartY = event.y
                tapCandidate = true
                pinchPointerId = -1
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pauseAutomaticOrbit()
                tapCandidate = false
                if (event.pointerCount >= 2) {
                    val firstIndex = 0
                    val secondIndex = 1
                    dragPointerId = event.getPointerId(firstIndex)
                    pinchPointerId = event.getPointerId(secondIndex)
                    previousPinchDistance = pointerDistance(event, firstIndex, secondIndex)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                pauseAutomaticOrbit()
                val dragIndex = event.findPointerIndex(dragPointerId)
                if (pinchPointerId >= 0 && event.pointerCount >= 2) {
                    val firstIndex = event.findPointerIndex(dragPointerId)
                    val secondIndex = event.findPointerIndex(pinchPointerId)
                    if (firstIndex >= 0 && secondIndex >= 0) {
                        val nextDistance = pointerDistance(event, firstIndex, secondIndex)
                        if (previousPinchDistance > 0f && nextDistance > 0f) {
                            val limits = manifest
                            cameraDistance = (cameraDistance * previousPinchDistance / nextDistance).coerceIn(
                                limits?.cameraMinDistance ?: 7.5, limits?.cameraMaxDistance ?: 24.0
                            )
                            cameraDirty = true
                        }
                        previousPinchDistance = nextDistance
                    }
                    return true
                }
                if (dragIndex >= 0) {
                    val x = event.getX(dragIndex)
                    val y = event.getY(dragIndex)
                    val touchSlop = 8f * resources.displayMetrics.density
                    if (abs(x - touchStartX) > touchSlop || abs(y - touchStartY) > touchSlop) {
                        tapCandidate = false
                    }
                    cameraYaw += (x - lastPointerX) * 0.0065
                    val limits = manifest
                    cameraPitch = (cameraPitch + (y - lastPointerY) * 0.004).coerceIn(
                        limits?.cameraMinPitchRadians ?: 0.2, limits?.cameraMaxPitchRadians ?: 0.92
                    )
                    lastPointerX = x
                    lastPointerY = y
                    cameraDirty = true
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                if (liftedId == pinchPointerId) pinchPointerId = -1
                if (liftedId == dragPointerId && event.pointerCount > 1) {
                    val otherIndex = if (event.actionIndex == 0) 1 else 0
                    dragPointerId = event.getPointerId(otherIndex)
                    lastPointerX = event.getX(otherIndex)
                    lastPointerY = event.getY(otherIndex)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && tapCandidate) {
                    pickFighterAt(event.x, event.y)?.let { fighterId ->
                        if (centerCameraOnFighter(fighterId)) onFighterTapped?.invoke(fighterId)
                    }
                }
                dragPointerId = -1
                pinchPointerId = -1
                tapCandidate = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    /**
     * AR touch handling: the camera is the device pose, so no orbit/pinch is applied. Taps still
     * resolve against the projected fighters so the HUD can select a target.
     */
    private fun handleArSceneTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                tapCandidate = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val touchSlop = 8f * resources.displayMetrics.density
                if (abs(event.x - touchStartX) > touchSlop || abs(event.y - touchStartY) > touchSlop) {
                    tapCandidate = false
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (tapCandidate) {
                    pickFighterAt(event.x, event.y)?.let { fighterId -> onFighterTapped?.invoke(fighterId) }
                }
                tapCandidate = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                tapCandidate = false
                return true
            }
        }
        return true
    }

    private fun pointerDistance(event: MotionEvent, first: Int, second: Int): Float {
        val dx = event.getX(first) - event.getX(second)
        val dy = event.getY(first) - event.getY(second)
        return sqrt(dx * dx + dy * dy)
    }

    private fun syncFighters() {
        if (desiredFighters.isEmpty() && renderedFighters.isEmpty()) return
        if (provider == null) {
            val nextProvider = UbershaderProvider(engine)
            provider = nextProvider
            assetLoader = AssetLoader(engine, nextProvider, EntityManager.get())
            resourceLoader = ResourceLoader(engine)
        }
        val presentations = desiredFighters.values + desiredPreparedForms.values
        for (presentation in presentations) {
            if (presentation.setKey !in fighterPool) {
                tryCreateFighter(presentation.combatantId, presentation)?.let { fighterPool[presentation.setKey] = it }
            }
            presentation.transitionGlb?.let { bytes ->
                val key = "${presentation.setKey}:transition"
                if (key !in fighterPool) {
                    tryCreateFighter(presentation.combatantId, presentation.copy(modelGlb = bytes,
                        setKey = key, transitionGlb = null))?.let { fighterPool[key] = it }
                }
            }
        }
        renderedFighters.keys.filter { it !in desiredFighters }.forEach { id ->
            renderedFighters.remove(id)?.let(::hideFighter)
        }
        for ((id, presentation) in desiredFighters) {
            // Keep the last valid actor if a new asset failed; don't poison its base identity.
            fighterPool[presentation.setKey]?.let { replacement ->
                if (renderedFighters[id] !== replacement) renderedFighters[id]?.let(::hideFighter)
                renderedFighters[id] = replacement
            }
        }
        val keys = presentations.map { it.setKey }.toSet()
        if (cinematicPreparedKeys != keys && !cinematicPreparationFailed) {
            runCatching {
                val renderer = cinematicRenderer ?: BattleCinematicRenderer(engine, viewer.scene,
                    requireNotNull(assetLoader), requireNotNull(resourceLoader)).also { cinematicRenderer = it }
                renderer.prepare(presentations)
                cinematicPreparedKeys = keys
            }.onFailure { failure ->
                cinematicPreparationFailed = true
                Log.e(TAG, "Could not prepare finisher effects", failure)
                onAssetError?.invoke(context.getString(R.string.ui_battle_error_fighter_load,
                    desiredFighters.values.firstOrNull()?.displayName.orEmpty(), failure.message))
            }
        }
    }

    private fun tryCreateFighter(id: String, presentation: BattleFighterPresentation): RenderedFighter? {
        val key = id to presentation.setKey
        if (key in failedFighterKeys) return null
        var pendingAsset: FilamentAsset? = null
        return runCatching {
            val bytes = presentation.modelGlb
            val buffer = ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
            val asset = checkNotNull(assetLoader?.createAsset(buffer)) { "O GLB ${presentation.displayName} é inválido." }
            pendingAsset = asset
            resourceLoader?.loadResources(asset)
            asset.releaseSourceData()
            viewer.scene.addEntities(asset.entities)
            val renderables = engine.renderableManager
            val instances = asset.entities.filter(renderables::hasComponent).map(renderables::getInstance)
            instances.forEach { renderables.setLayerMask(it, 0xFF, 0) }
            asset.entities.forEach { entity ->
                if (renderables.hasComponent(entity)) {
                    val instance = renderables.getInstance(entity)
                    renderables.setCastShadows(instance, false)
                    renderables.setReceiveShadows(instance, false)
                }
            }
            val poses = presentation.poseNames.mapNotNull { pose ->
                val entity = asset.getFirstEntityByName("pose_$pose")
                if (entity != 0 && renderables.hasComponent(entity)) {
                    val instance = renderables.getInstance(entity)
                    renderables.setLayerMask(instance, 0xFF, 0)
                    pose to entity
                } else null
            }.toMap()
            check(poses.isNotEmpty()) { "O modelo de ${presentation.displayName} não possui poses." }
            val poseInstances = poses.values.map(renderables::getInstance).toSet()
            val materials = instances.flatMap { instance ->
                (0 until renderables.getPrimitiveCount(instance)).map { renderables.getMaterialInstanceAt(instance, it) }
            }
            if (presentation.setKey.endsWith(":transition")) {
                materials.forEach { it.setDepthWrite(false) }
            }
            RenderedFighter(asset, presentation, poses, instances, instances.filter { it !in poseInstances }, materials).also {
                pendingAsset = null
                fighterAssetLoadCount++
                Log.i(TAG, "Loaded 3D fighter ${presentation.displayName} (${poses.size} poses)")
            }
        }.onFailure { failure ->
            failedFighterKeys += key
            pendingAsset?.let { viewer.scene.removeEntities(it.entities); assetLoader?.destroyAsset(it) }
            Log.e(TAG, "Could not load fighter ${presentation.displayName}", failure)
            // Initial loads still surface; a failed mid-battle Blast swap
            // falls back to the current model silently.
            if (id !in renderedFighters) {
                onAssetError?.invoke(context.getString(R.string.ui_battle_error_fighter_load, presentation.displayName, failure.message))
            }
        }.getOrNull()
    }

    private fun hideFighter(fighter: RenderedFighter) {
        fighter.renderables.forEach { engine.renderableManager.setLayerMask(it, 0xFF, 0) }
        fighter.activePose = null
    }

    private fun frontEffectAnchor(fighter: RenderedFighter): BattleEffectAnchor = battleFrontEffectAnchor(
        fighter.worldTransform, fighter.boundsCenter, fighter.boundsHalfExtent,
        (manifest?.fighterScale ?: 1.65f)*fighter.presentation.visualScaleMultiplier,
        effectCameraYaw, effectCameraPitch)

    private fun updateCinematicCamera() {
        val snapshot = latestSnapshot ?: return
        val sequence = snapshot.finisher?.let { cinematicVisualSnapshot(it, allowCinematicMotion) }
        if (sequence == null) {
            cinematicSavedCamera?.let(::applyCinematicCamera)
            if (cinematicSequenceId != null) {
                lastCameraFrameNanos = 0L
                lastOrbitFrameNanos = 0L
                pauseAutomaticOrbit()
            }
            cinematicSequenceId = null
            cinematicSavedCamera = null
            cinematicTargetY = null
            return
        }
        val arena = manifest ?: return
        val members = (snapshot.alliedMembers + snapshot.opposingMembers).associateBy { it.combatantId }
        val lead = members[sequence.leadId] ?: return
        if (cinematicSequenceId != sequence.sequenceId) {
            cinematicSequenceId = sequence.sequenceId
            cinematicSavedCamera = CinematicCameraPose(cameraTargetX, cameraTargetZ,
                arena.cameraTargetY, cameraYaw, cameraPitch, cameraDistance)
        }
        val result = preparedFinisherPresentation(sequence, desiredPreparedForms) ?: desiredFighters[sequence.leadId]
        val height = arena.fighterScale * (result?.visualScaleMultiplier ?: 1f)
        fun world(point: BattlePosition) = BattlePosition(point.x * arena.positionScale, point.z * arena.positionScale)
        if (cinematicAttackSide == null) {
            val anchor = cinematicAnchor(lead.position, members[sequence.partnerId]?.position, sequence.kind)
            val target = members[sequence.targetId]?.position ?: BattlePosition(anchor.x + 2f, anchor.z)
            val dx = target.x - anchor.x
            val dz = target.z - anchor.z
            cinematicAttackSide = if (dx * kotlin.math.cos(cameraYaw) - dz * kotlin.math.sin(cameraYaw) >= 0) 1 else -1
        }
        val pose = cinematicCameraPose(sequence, world(lead.position), members[sequence.partnerId]?.position?.let(::world),
            members[sequence.targetId]?.position?.let(::world), height.toDouble(),
            viewportWidth.toDouble() / viewportHeight.coerceAtLeast(1), requireNotNull(cinematicSavedCamera), allowCinematicMotion,
            attackSide = cinematicAttackSide, attackStyle = result?.attackProfile?.style ?: FinisherAttackStyle.PROJECTILE,
            emitterHeight = result?.attackProfile?.emitterHeight ?: 0.7f)
        val shake = if (allowCinematicMotion && sequence.phase == BattleFinisherPhase.IMPACT && sequence.phaseProgress < 0.55f) {
            val p = sequence.phaseProgress / 0.55f
            kotlin.math.sin(p * Math.PI * 8) * (1f - p) * height * 0.085
        } else 0.0
        applyCinematicCamera(pose.copy(targetX = pose.targetX + shake))
    }

    private fun applyCinematicCamera(pose: CinematicCameraPose) {
        cameraTargetX = pose.targetX
        cameraTargetZ = pose.targetZ
        cinematicTargetY = pose.targetY
        cameraYaw = pose.yaw
        cameraPitch = pose.pitch
        cameraDistance = pose.distance
        cameraDirty = true
    }

    private fun updateCinematicActors() {
        val snapshot = latestSnapshot ?: return
        val sequence = snapshot.finisher?.let { cinematicVisualSnapshot(it, allowCinematicMotion) } ?: return
        val arena = manifest ?: return
        val members = (snapshot.alliedMembers + snapshot.opposingMembers).associateBy { it.combatantId }
        val lead = members[sequence.leadId] ?: return
        val partner = members[sequence.partnerId]
        val target = members[sequence.targetId]
        val resultPresentation = preparedFinisherPresentation(sequence, desiredPreparedForms)
        val result = resultPresentation?.let { fighterPool[it.setKey] }
        val isTransform = sequence.kind in listOf(BattleFinisherKind.FORM, BattleFinisherKind.JOGRESS) && result != null
        val frame = if (isTransform) sampleCinematicActors(sequence, allowCinematicMotion) else CinematicActorFrame(true)
        val anchor = cinematicAnchor(lead.position, partner?.position, sequence.kind)
        val style = (resultPresentation ?: desiredFighters[sequence.leadId])?.attackProfile?.style ?: FinisherAttackStyle.PROJECTILE
        val p = sequence.phaseProgress
        val attacking = sequence.phase in BattleFinisherPhase.CHARGE..BattleFinisherPhase.AFTERMATH
        val pose = if (attacking) "attack" else "idle"
        val fusionSources = if (sequence.kind == BattleFinisherKind.JOGRESS && partner != null) {
            cinematicFusionSources(lead.position, partner.position, target?.position,
                arena.fighterScale * (resultPresentation?.visualScaleMultiplier ?: 1f) / arena.positionScale)
        } else null
        fun bodyPosition(point: BattlePosition): BattlePosition {
            if (style != FinisherAttackStyle.MELEE || target == null || !allowCinematicMotion) return point
            val amount = when (sequence.phase) {
                BattleFinisherPhase.RELEASE -> cinematicLaunchProgress(p)
                BattleFinisherPhase.IMPACT -> 1f - cinematicEase((p - 0.20f) / 0.60f)
                else -> 0f
            }
            val dx = target.position.x - point.x
            val dz = target.position.z - point.z
            val distance = kotlin.math.hypot(dx, dz).coerceAtLeast(0.01f)
            val reach = (distance - 0.55f).coerceAtLeast(0f) * amount
            return BattlePosition(point.x + dx / distance * reach, point.z + dz / distance * reach)
        }
        for (id in sequence.participantIds) {
            val member = members[id] ?: continue
            val source = renderedFighters[id] ?: continue
            val staged = if (fusionSources != null) {
                val point = if (id == sequence.leadId) fusionSources.first else fusionSources.second
                val staging = if (sequence.phase == BattleFinisherPhase.FOCUS) cinematicEase(p)
                    else if (sequence.phase == BattleFinisherPhase.RESTORE) 1f - cinematicEase((p - 0.65f) / 0.35f) else 1f
                BattlePosition(member.position.x + (point.x - member.position.x) * staging,
                    member.position.z + (point.z - member.position.z) * staging)
            } else member.position
            val merged = if (sequence.kind == BattleFinisherKind.JOGRESS) BattlePosition(
                staged.x + (anchor.x - staged.x) * frame.mergeProgress,
                staged.z + (anchor.z - staged.z) * frame.mergeProgress) else staged
            if (frame.sourceVisible) {
                showCinematicFighter(source, bodyPosition(merged), target?.position, pose,
                    arena.fighterScale * source.presentation.visualScaleMultiplier)
            } else hideFighter(source)
            fighterPool["${source.presentation.setKey}:transition"]?.let { silhouette ->
                if (frame.sourceTransition > 0f) showCinematicFighter(silhouette, merged, target?.position, "idle",
                    arena.fighterScale * source.presentation.visualScaleMultiplier * (1f - frame.mergeProgress * 0.2f),
                    opacity = frame.sourceTransition)
            }
        }
        if (result != null && isTransform) {
            val scale = arena.fighterScale * result.presentation.visualScaleMultiplier
            if (frame.resultVisible) showCinematicFighter(result, bodyPosition(anchor), target?.position, pose, scale)
            fighterPool["${result.presentation.setKey}:transition"]?.let { silhouette ->
                if (frame.resultTransition > 0f) showCinematicFighter(silhouette, anchor, target?.position, "idle",
                    scale * frame.resultScale * 1.008f, opacity = frame.resultTransition)
            }
        }
        // Keep the affected target on-screen through a lethal aftermath; victory is deferred by core.
        val hitSucceeded = sequence.damage > 0
        if (target != null && hitSucceeded && sequence.phase in BattleFinisherPhase.IMPACT..BattleFinisherPhase.AFTERMATH) {
            renderedFighters[target.combatantId]?.let { victim ->
                val amount = if (sequence.phase == BattleFinisherPhase.IMPACT) cinematicEase(p / 0.7f) else 1f
                val dx = target.position.x - anchor.x
                val dz = target.position.z - anchor.z
                val distance = kotlin.math.hypot(dx, dz).coerceAtLeast(0.01f)
                val retreat = if (allowCinematicMotion) amount * 0.18f else 0f
                showCinematicFighter(victim, BattlePosition(target.position.x + dx / distance * retreat,
                    target.position.z + dz / distance * retreat), anchor,
                    if (target.health <= 0) "defeated" else "guard",
                    arena.fighterScale * victim.presentation.visualScaleMultiplier)
            }
        }
    }

    private fun showCinematicFighter(fighter: RenderedFighter, position: BattlePosition, target: BattlePosition?,
        requestedPose: String, scale: Float, opacity: Float = 1f) {
        val renderables = engine.renderableManager
        val transition = fighter.presentation.setKey.endsWith(":transition")
        val pose = animatedPose(fighter, requestedPose, (latestSnapshot?.finisher?.elapsedMillis ?: 0L) * 1_000_000L)
        hideFighter(fighter)
        fighter.poses[pose]?.let { renderables.setLayerMask(renderables.getInstance(it), 0xFF, 0x01) }
        if (!transition) fighter.shadows.forEach { renderables.setLayerMask(it, 0xFF, 0x01) }
        if (transition) fighter.materials.forEach { it.setParameter("baseColorFactor", 1f, 1f, 1f, opacity.coerceIn(0f, 1f)) }
        fighter.activePose = pose
        val arena = manifest ?: return
        val dx = (target?.x ?: position.x + 1f) - position.x
        val dz = (target?.z ?: position.z) - position.z
        val yaw = cameraAssistedSpriteYaw(kotlin.math.atan2(-dz, dx), cameraYaw.toFloat())
        val facesRight = dx * kotlin.math.cos(cameraYaw) - dz * kotlin.math.sin(cameraYaw) >= 0
        Matrix.setIdentityM(fighterTransform, 0)
        Matrix.translateM(fighterTransform, 0, position.x * arena.positionScale,
            if (transition) 0.03f else 0.025f, position.z * arena.positionScale)
        Matrix.rotateM(fighterTransform, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.scaleM(fighterTransform, 0, if (facesRight) -scale else scale, scale, scale)
        val root = engine.transformManager.getInstance(fighter.asset.root)
        fighterTransform.copyInto(fighter.worldTransform)
        if (root != 0) engine.transformManager.setTransform(root, fighterTransform)
    }

    private fun updateFighterTransforms(frameTimeNanos: Long) {
        val combatants = latestSnapshot?.let { it.alliedMembers + it.opposingMembers }?.associateBy { it.combatantId }
            ?: return
        for ((id, fighter) in renderedFighters) {
            val combatant = combatants[id] ?: continue
            fighter.shadows.forEach { engine.renderableManager.setLayerMask(it, 0xFF, 0x01) }
            val requestedPose = when (combatant.state) {
                CombatantState.MOVE_TO_TARGET, CombatantState.MOVE_AWAY,
                CombatantState.POSITIONING, CombatantState.KNOCKBACK -> "walk"
                CombatantState.ATTACK_STARTUP, CombatantState.ATTACK_ACTIVE, CombatantState.USING_SPECIAL -> "attack"
                CombatantState.DEFEATED -> "defeated"
                CombatantState.STUNNED, CombatantState.DEFENDING -> "guard"
                else -> "idle"
            }
            val pose = animatedPose(fighter, requestedPose, frameTimeNanos)
            if (fighter.activePose != pose) {
                fighter.activePose?.let { old ->
                    fighter.poses[old]?.let { entity -> engine.renderableManager.setLayerMask(engine.renderableManager.getInstance(entity), 0xFF, 0) }
                }
                fighter.poses[pose]?.let { entity ->
                    engine.renderableManager.setLayerMask(engine.renderableManager.getInstance(entity), 0xFF, 0x01)
                }
                fighter.activePose = pose
            }
            Matrix.setIdentityM(fighterTransform, 0)
            val currentManifest = manifest ?: return
            val scale = currentManifest.fighterScale * fighter.presentation.visualScaleMultiplier
            Matrix.translateM(fighterTransform, 0,
                combatant.position.x * currentManifest.positionScale, 0.025f,
                combatant.position.z * currentManifest.positionScale)
            // Preserve the combatant's physical heading and apply only a soft
            // camera correction so the thin extrusion remains readable.
            val target = combatants[combatant.targetId]?.takeIf { it.side != combatant.side }
                ?: combatants.values.filter { it.side != combatant.side && it.health > 0 }
                    .minByOrNull { it.position.distanceTo(combatant.position) }
            val direction = fighterFacings.getOrPut(id) { BattleFighterFacing() }.direction(
                combatant.position, target?.position, combatant.state, latestSnapshot?.elapsedMillis ?: 0L)
            val dx = direction.x
            val dz = direction.z
            val worldHeadingYaw = kotlin.math.atan2(-dz, dx)
            val visibleYaw = cameraAssistedSpriteYaw(worldHeadingYaw, cameraYaw.toFloat())
            Matrix.rotateM(fighterTransform, 0,
                Math.toDegrees(visibleYaw.toDouble()).toFloat(), 0f, 1f, 0f)
            val facesRight = dx * kotlin.math.cos(cameraYaw) - dz * kotlin.math.sin(cameraYaw) >= 0
            Matrix.scaleM(fighterTransform, 0,
                if (facesRight) -scale else scale,
                scale, scale)
            val root = engine.transformManager.getInstance(fighter.asset.root)
            fighterTransform.copyInto(fighter.worldTransform)
            if (root != 0) engine.transformManager.setTransform(root, fighterTransform)
        }
    }

    private fun animatedPose(fighter: RenderedFighter, requested: String, now: Long): String {
        val alternate = when (requested) {
            "walk" -> "walk2"
            "idle" -> "idle2"
            else -> null
        }
        val frame = if (alternate != null && allowCinematicMotion && (now / POSE_FRAME_NANOS) % 2L == 1L) {
            alternate
        } else {
            requested
        }
        return when {
            frame in fighter.poses -> frame
            requested in fighter.poses -> requested
            "idle" in fighter.poses -> "idle"
            else -> fighter.poses.keys.first()
        }
    }

    /** Small per-victim pools are loaded before combat; no textures/GLBs are built on a hit. */
    private fun syncImpactAssets() {
        val loader = assetLoader ?: return
        val wanted = desiredFighters.flatMap { (id, fighter) ->
            fighter.impactModels.map { (variant, bytes) -> (id to variant) to bytes }
        }.toMap()
        for (key in renderedImpacts.keys.toList()) {
            if (wanted[key] !== renderedImpacts[key]?.model) destroyImpact(key)
        }
        val renderables = engine.renderableManager
        for ((key, bytes) in wanted) {
            if (key in renderedImpacts || key in failedImpactKeys) continue
            var pendingAsset: FilamentAsset? = null
            runCatching {
                val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    .put(bytes).apply { flip() }
                val asset = checkNotNull(loader.createAsset(buffer))
                pendingAsset = asset
                resourceLoader?.loadResources(asset)
                asset.releaseSourceData()
                val instances = asset.entities.filter(renderables::hasComponent).map { renderables.getInstance(it) }
                val materials = instances.flatMap { instance ->
                    renderables.setCastShadows(instance, false)
                    renderables.setReceiveShadows(instance, false)
                    renderables.setLayerMask(instance, 0xFF, 0)
                    (0 until renderables.getPrimitiveCount(instance)).map { renderables.getMaterialInstanceAt(instance, it) }
                }
                // Effects participate in the same depth buffer as the actual fighter silhouettes.
                materials.forEach { it.setDepthCulling(true); it.setDepthWrite(false) }
                viewer.scene.addEntities(asset.entities)
                renderedImpacts[key] = RenderedImpact(asset, bytes, instances, materials)
                pendingAsset = null
            }.onFailure { failure ->
                pendingAsset?.let { loader.destroyAsset(it) }
                failedImpactKeys += key
                Log.w(TAG, "Could not load impact sprite $key", failure)
            }
        }
    }

    private fun updateImpactTransforms(frameTimeNanos: Long) {
        val snapshot = latestSnapshot ?: return
        val currentManifest = manifest ?: return
        val combatants = (snapshot.alliedMembers + snapshot.opposingMembers).associateBy { it.combatantId }
        val flashes = visibleBattleImpactSprites(snapshot.impacts).associateBy { it.targetId }
        for ((key, effect) in renderedImpacts) {
            val (targetId, variant) = key
            val impact = flashes[targetId]
            val desiredVariant = if (impact?.isSpecial == true && "special" in desiredFighters[targetId]?.impactModels.orEmpty()) "special" else "normal"
            val target = combatants[targetId]
            if (impact != null && effect.impactId != impact.impactId) {
                effect.impactId = impact.impactId
                effect.firstShownAtNanos = frameTimeNanos
            }
            val opacity = impact?.let {
                battleImpactFlashOpacity(it.remainingMillis, (frameTimeNanos - effect.firstShownAtNanos) / 1_000_000L)
            } ?: 0f
            val visible = opacity > 0f && target != null && variant == desiredVariant
            effect.renderables.forEach { engine.renderableManager.setLayerMask(it, 0xFF, if (visible) 0x01 else 0) }
            if (!visible || impact == null) continue
            val fighterScale = currentManifest.fighterScale * (desiredFighters[targetId]?.visualScaleMultiplier ?: 1f)
            val size = battleImpactSizeMultiplier(impact.remainingMillis,
                (frameTimeNanos - effect.firstShownAtNanos) / 1_000_000L, impact.isSpecial, impact.critical)
            val placement = renderedFighters[targetId]?.let {
                battleImpactPlacementFromAnchor(frontEffectAnchor(it), fighterScale, effectCameraYaw, effectCameraPitch, size)
            } ?: battleImpactPlacement(target.position.x * currentManifest.positionScale,
                target.position.z * currentManifest.positionScale, fighterScale, effectCameraYaw, effectCameraPitch, size)
            Matrix.setIdentityM(impactTransform, 0)
            Matrix.translateM(impactTransform, 0, placement.x, placement.y, placement.z)
            Matrix.rotateM(impactTransform, 0, Math.toDegrees(effectCameraYaw).toFloat(), 0f, 1f, 0f)
            Matrix.rotateM(impactTransform, 0, -Math.toDegrees(effectCameraPitch).toFloat(), 1f, 0f, 0f)
            Matrix.scaleM(impactTransform, 0, placement.height, placement.height, placement.height)
            val root = engine.transformManager.getInstance(effect.asset.root)
            if (root != 0) engine.transformManager.setTransform(root, impactTransform)
            effect.materials.forEach { it.setParameter("baseColorFactor", 1f, 1f, 1f, opacity) }
        }
    }

    private fun destroyImpact(key: Pair<String, String>) {
        val effect = renderedImpacts.remove(key) ?: return
        viewer.scene.removeEntities(effect.asset.entities)
        assetLoader?.destroyAsset(effect.asset)
    }

    private fun releaseImpacts() {
        renderedImpacts.keys.toList().forEach(::destroyImpact)
        failedImpactKeys.clear()
    }

    private fun destroyFighter(id: String) {
        val fighter = renderedFighters.remove(id) ?: return
        fighterPool.remove(fighter.presentation.setKey)
        runCatching {
            viewer.scene.removeEntities(fighter.asset.entities)
            assetLoader?.destroyAsset(fighter.asset)
        }.onFailure { Log.w(TAG, "Could not release fighter $id", it) }
    }

    fun releaseScene() {
        if (released) return
        released = true
        arPresentation = null
        arenaLoadGeneration++
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        // Must go before the model/engine teardown below; flushes the engine and drops the
        // transparent swap chain while the Engine is still alive.
        arTransparentSwapChain?.stop()
        arTransparentSwapChain = null
        releaseFighters()
        arenaEnergyMaterial = null
        environmentThemes.release()
        arenaVoxelGroups = emptyList()
        arenaVoxelCubes = emptyList()
        viewer.scene.skybox = null
        engine.destroySkybox(backgroundSkybox)
        engine.destroySkybox(radarSkybox)
    }

    override fun onDetachedFromWindow() {
        if (!released) {
            released = true
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            arTransparentSwapChain?.stop()
            arTransparentSwapChain = null
            releaseFighters()
            arenaEnergyMaterial = null
            environmentThemes.release()
            arenaVoxelGroups = emptyList()
            arenaVoxelCubes = emptyList()
            viewer.scene.skybox = null
            engine.destroySkybox(backgroundSkybox)
            engine.destroySkybox(radarSkybox)
        }
        super.onDetachedFromWindow()
        // The detached AR viewer never registers an attach-state listener (that only exists in the
        // view-attached ModelViewer constructors), so the AR view owns the Engine teardown. The
        // non-AR viewer destroys itself in its own detach listener; do not double-destroy it.
        if (arMode) destroyViewerOnce()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); attachedOnce = true }
    fun releaseUnattached() { if (!attachedOnce) destroyViewerOnce() }

    private fun destroyViewerOnce() {
        if (viewerDestroyed) return
        viewerDestroyed = true
        runCatching { viewer.destroy() }
    }

    private fun releaseFighters() {
        releaseImpacts()
        cinematicRenderer?.release()
        cinematicRenderer = null
        cinematicPreparedKeys = emptySet()
        cinematicPreparationFailed = false
        fighterPool.values.forEach { fighter ->
            viewer.scene.removeEntities(fighter.asset.entities)
            assetLoader?.destroyAsset(fighter.asset)
        }
        fighterPool.clear()
        fighterFacings.clear()
        renderedFighters.clear()
        failedFighterKeys.clear()
        runCatching { assetLoader?.destroy() }
        assetLoader = null
        runCatching { resourceLoader?.destroy() }
        resourceLoader = null
        runCatching { provider?.destroyMaterials() }
        runCatching { provider?.destroy() }
        provider = null
    }

    private val arenaTransform = FloatArray(16)
    private val fighterTransform = FloatArray(16)
    private val impactTransform = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val clipPosition = FloatArray(4)

    private companion object {
        const val TAG = "OfflineBattle3d"
        const val POSE_FRAME_NANOS = 460_000_000L
        const val PROJECTION_NOTIFY_NANOS = 75_000_000L
        const val CAMERA_FOCUS_EPSILON = 0.001
        const val MANUAL_ORBIT_PAUSE_NANOS = 900_000_000L
        const val ORBIT_ACCELERATION_NANOS = 1_800_000_000.0
        const val ARENA_ENERGY_PULSE_SPEED = 0.82
        const val ARENA_ENERGY_BASE = 1.10f
        const val ARENA_ENERGY_AMPLITUDE = 0.24f
        /** ~30 fps AR battle rendering, leaving GPU headroom for ARCore tracking. */
        const val AR_RENDER_INTERVAL_NANOS = 33_000_000L
        /** Arena material whose primitives form the battle-space delimiter ring (AR keeps only it). */
        val AR_BOUNDARY_MATERIAL = "battle_boundary"
        val nativeInitialized = AtomicBoolean(false)

        fun ensureFilament() {
            if (!nativeInitialized.compareAndSet(false, true)) return
            try {
                Filament.init()
                System.loadLibrary("gltfio-jni")
            } catch (failure: Throwable) {
                nativeInitialized.set(false)
                throw failure
            }
        }
    }
}
