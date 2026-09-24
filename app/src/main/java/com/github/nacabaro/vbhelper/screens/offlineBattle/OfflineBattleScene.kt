package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantState
import com.github.nacabaro.vbhelper.rendering.sprite3d.cameraAssistedSpriteYaw
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.Skybox
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
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
    modifier: Modifier = Modifier
) {
    if (manifest == null) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).also { host ->
                runCatching { OfflineBattleSceneView(context) }
                    .onSuccess { scene ->
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
                            onAssetError(failure.message ?: "Não foi possível iniciar o renderizador 3D.")
                        }
                    }
            }
        },
        update = { host ->
            val scene = host.getChildAt(0) as? OfflineBattleSceneView
            scene?.setRenderingEnabled(renderingEnabled)
            scene?.setBattleState(
                snapshot, fighters, sessionId, onSceneReady, onProjectionChanged, onFighterTapped
            )
        },
        onRelease = { host ->
            (host.getChildAt(0) as? OfflineBattleSceneView)?.releaseScene()
            host.removeAllViews()
            host.post { onReady(null) }
        }
    )
}

class OfflineBattleSceneView(context: Context) : TextureView(context) {
    private val engine: Engine
    private val viewer: ModelViewer
    private val backgroundSkybox: Skybox
    private var released = false
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
    private var cameraPitch = 0.62
    private var cameraDistance = 13.5
    private var cameraTargetX = 0.0
    private var cameraTargetZ = 0.0
    private var cameraFocusFighterId: String? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var tapCandidate = false
    private var manifest: OfflineArenaManifest? = null
    private var cameraDirty = true
    private var renderingEnabled = true
    private var projectionNotificationPending = true

    private var provider: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private val renderedFighters = linkedMapOf<String, RenderedFighter>()
    private var latestSnapshot: BattleSnapshot? = null
    private var desiredFighters: Map<String, BattleFighterPresentation> = emptyMap()
    private var latestSessionId: String? = null
    private var onAssetError: ((String) -> Unit)? = null
    private var onSceneReady: (() -> Unit)? = null
    private var onProjectionChanged: (() -> Unit)? = null
    private var onFighterTapped: ((String) -> Unit)? = null
    private var sceneReadyNotified = false
    private var lastProjectionNotifyNanos = 0L
    private val failedFighterIds = linkedSetOf<String>()

    private data class RenderedFighter(
        val asset: FilamentAsset,
        val presentation: BattleFighterPresentation,
        val poses: Map<String, Int>,
        var activePose: String? = null,
        var poseChangedAtNanos: Long = 0L
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (released || !renderingEnabled) return
            updateFocusedCameraTarget()
            projectionNotificationPending = projectionNotificationPending || cameraDirty
            applyCameraIfNeeded()
            syncFighters()
            updateFighterTransforms((latestSnapshot?.elapsedMillis ?: 0L) * 1_000_000L)
            val rendered = viewer.render(frameTimeNanos)
            if (projectionNotificationPending && frameTimeNanos - lastProjectionNotifyNanos >= PROJECTION_NOTIFY_NANOS) {
                lastProjectionNotifyNanos = frameTimeNanos
                projectionNotificationPending = false
                onProjectionChanged?.invoke()
            }
            if (!sceneReadyNotified && rendered && viewer.progress >= 1f && arenaLoaded && desiredFighters.isNotEmpty() &&
                desiredFighters.keys.all { it in renderedFighters }) {
                sceneReadyNotified = true
                onSceneReady?.invoke()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        ensureFilament()
        engine = Engine.create()
        viewer = ModelViewer(this, engine, UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK), null)
        viewer.view.setPostProcessingEnabled(true)
        viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
            enabled = true
            strength = 0.08f
            resolution = 256
            threshold = true
        }
        val backdrop = Colors.toLinear(
            Colors.RgbType.SRGB,
            DeepPurpleBgAlt.red, DeepPurpleBgAlt.green, DeepPurpleBgAlt.blue
        )
        backgroundSkybox = Skybox.Builder()
            .color(backdrop[0], backdrop[1], backdrop[2], 1f)
            .build(engine)
            .also { viewer.scene.skybox = it }
        contentDescription = "Arena tridimensional. Toque em um Digimon para centralizar a câmera, arraste para orbitar e use dois dedos para ajustar o zoom."
        isFocusable = true
        isOpaque = false
        setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) performClick()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.parent?.requestDisallowInterceptTouchEvent(false)
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
                post { if (!released && generation == arenaLoadGeneration) onError("Arena não encontrada: ${failure.message}") }
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
                    arenaLoaded = true
                    cameraDirty = true
                    Log.i(TAG, "Loaded $assetPath (${bytes.size} bytes)")
                }.onFailure { failure ->
                    Log.e(TAG, "Could not load arena GLB", failure)
                    onError("Não foi possível carregar o Coliseu: ${failure.message}")
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
        onFighterTapped: (String) -> Unit
    ) {
        if (released) return
        if (latestSessionId != sessionId) {
            cameraFocusFighterId = null
            cameraTargetX = 0.0
            cameraTargetZ = 0.0
            cameraDirty = true
        }
        if (latestSessionId != sessionId || desiredFighters.keys != fighters.keys ||
            desiredFighters.any { (id, old) -> fighters[id]?.setKey != old.setKey }) {
            sceneReadyNotified = false
        }
        latestSessionId = sessionId
        latestSnapshot = snapshot
        desiredFighters = fighters
        onSceneReady = onReady
        this.onProjectionChanged = onProjectionChanged
        this.onFighterTapped = onFighterTapped
    }

    fun setRenderingEnabled(enabled: Boolean) {
        if (released || enabled == renderingEnabled) return
        renderingEnabled = enabled
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        if (enabled) {
            cameraDirty = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun retryScene() {
        if (released) return
        sceneReadyNotified = false
        failedFighterIds.clear()
        arenaLoaded = false
        renderedFighters.keys.toList().forEach(::destroyFighter)
        runCatching { if (viewer.asset != null) viewer.destroyModel() }
        val currentManifest = manifest ?: return
        onAssetError?.let { loadArena(currentManifest, it) }
    }

    fun resetCamera() {
        cameraYaw = 0.0
        cameraPitch = manifest?.cameraPitchRadians ?: 0.62
        cameraDistance = manifest?.cameraDistance ?: 19.0
        cameraFocusFighterId = null
        cameraTargetX = 0.0
        cameraTargetZ = 0.0
        cameraDirty = true
    }

    /** Projects arena coordinates into TextureView pixels for the Compose HUD. */
    fun projectBattlePosition(x: Float, y: Float, z: Float): Pair<Float, Float>? {
        val currentManifest = manifest ?: return null
        if (viewportWidth <= 0 || viewportHeight <= 0 || !arenaLoaded) return null
        return runCatching {
            val view = viewer.camera.getViewMatrix(FloatArray(16))
            val projection = viewer.camera.getProjectionMatrix(DoubleArray(16))
            val projectionFloat = FloatArray(16) { projection[it].toFloat() }
            Matrix.multiplyMM(viewProjection, 0, projectionFloat, 0, view, 0)
            Matrix.multiplyMV(clipPosition, 0, viewProjection, 0,
                floatArrayOf(x * currentManifest.positionScale, y, z * currentManifest.positionScale, 1f), 0)
            if (clipPosition[3] <= 0f) return null
            val ndcX = clipPosition[0] / clipPosition[3]
            val ndcY = clipPosition[1] / clipPosition[3]
            if (ndcX !in -1.15f..1.15f || ndcY !in -1.15f..1.15f) return null
            (ndcX * 0.5f + 0.5f) * viewportWidth to (1f - (ndcY * 0.5f + 0.5f)) * viewportHeight
        }.getOrNull()
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
        val horizontal = cameraDistance * kotlin.math.cos(cameraPitch)
        val desiredEyeX = cameraTargetX + kotlin.math.sin(cameraYaw) * horizontal
        val desiredEyeZ = cameraTargetZ + kotlin.math.cos(cameraYaw) * horizontal
        val cameraEye = manifest?.let {
            constrainArenaCameraEye(desiredEyeX, desiredEyeZ, it.cameraCollisionRadius)
        } ?: ArenaCameraPoint(desiredEyeX, desiredEyeZ)
        val targetY = manifest?.cameraTargetY ?: 0.7
        val eyeY = targetY + cameraDistance * kotlin.math.sin(cameraPitch)
        viewer.camera.lookAt(cameraEye.x, eyeY, cameraEye.z, cameraTargetX, targetY, cameraTargetZ, 0.0, 1.0, 0.0)
    }

    private fun updateFocusedCameraTarget() {
        val focusedId = cameraFocusFighterId ?: return
        val currentManifest = manifest ?: return
        val fighter = latestSnapshot?.let { snapshot ->
            (snapshot.alliedMembers + snapshot.opposingMembers).firstOrNull { it.combatantId == focusedId }
        }
        if (fighter == null) {
            cameraFocusFighterId = null
            cameraTargetX = 0.0
            cameraTargetZ = 0.0
            cameraDirty = true
            return
        }
        val nextX = (fighter.position.x * currentManifest.positionScale).toDouble()
        val nextZ = (fighter.position.z * currentManifest.positionScale).toDouble()
        if (abs(nextX - cameraTargetX) > CAMERA_FOCUS_EPSILON ||
            abs(nextZ - cameraTargetZ) > CAMERA_FOCUS_EPSILON) {
            cameraTargetX = nextX
            cameraTargetZ = nextZ
            cameraDirty = true
        }
    }

    private fun centerCameraOnFighter(fighterId: String): Boolean {
        val currentManifest = manifest ?: return false
        val fighter = latestSnapshot?.let { snapshot ->
            (snapshot.alliedMembers + snapshot.opposingMembers).firstOrNull { it.combatantId == fighterId }
        } ?: return false
        cameraFocusFighterId = fighterId
        cameraTargetX = (fighter.position.x * currentManifest.positionScale).toDouble()
        cameraTargetZ = (fighter.position.z * currentManifest.positionScale).toDouble()
        cameraDirty = true
        return true
    }

    private fun pickFighterAt(screenX: Float, screenY: Float): String? {
        val currentManifest = manifest ?: return null
        val combatants = latestSnapshot?.let { it.alliedMembers + it.opposingMembers } ?: return null
        val touchRadius = 24f * resources.displayMetrics.density
        return combatants.mapNotNull { fighter ->
            val foot = projectBattlePosition(fighter.position.x, 0f, fighter.position.z) ?: return@mapNotNull null
            val head = projectBattlePosition(
                fighter.position.x,
                currentManifest.fighterScale,
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
        for (id in renderedFighters.keys.toList()) {
            val wanted = desiredFighters[id]
            if (wanted == null || wanted.setKey != renderedFighters[id]?.presentation?.setKey) destroyFighter(id)
        }
        for ((id, presentation) in desiredFighters) {
            if (id in renderedFighters || id in failedFighterIds) continue
            runCatching {
                val bytes = presentation.modelGlb
                val buffer = ByteBuffer.allocateDirect(bytes.size)
                    .order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
                val asset = checkNotNull(assetLoader?.createAsset(buffer)) { "O GLB ${presentation.displayName} é inválido." }
                resourceLoader?.loadResources(asset)
                asset.releaseSourceData()
                viewer.scene.addEntities(asset.entities)
                val renderables = engine.renderableManager
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
                renderedFighters[id] = RenderedFighter(asset, presentation, poses)
                Log.i(TAG, "Loaded 3D fighter ${presentation.displayName} (${poses.size} poses)")
            }.onFailure { failure ->
                failedFighterIds += id
                Log.e(TAG, "Could not load fighter ${presentation.displayName}", failure)
                onAssetError?.invoke("Não foi possível carregar ${presentation.displayName}: ${failure.message}")
            }
        }
    }

    private fun updateFighterTransforms(frameTimeNanos: Long) {
        val combatants = latestSnapshot?.let { it.alliedMembers + it.opposingMembers }?.associateBy { it.combatantId }
            ?: return
        for ((id, fighter) in renderedFighters) {
            val combatant = combatants[id] ?: continue
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
            val scale = currentManifest.fighterScale
            Matrix.translateM(fighterTransform, 0,
                combatant.position.x * currentManifest.positionScale, 0.025f,
                combatant.position.z * currentManifest.positionScale)
            // Preserve the combatant's physical heading and apply only a soft
            // camera correction so the thin extrusion remains readable.
            val target = combatants[combatant.targetId]?.takeIf { it.side != combatant.side }
                ?: combatants.values.filter { it.side != combatant.side && it.health > 0 }
                    .minByOrNull { it.position.distanceTo(combatant.position) }
            val dx = (target?.position?.x ?: -combatant.position.x) - combatant.position.x
            val dz = (target?.position?.z ?: -combatant.position.z) - combatant.position.z
            val worldHeadingYaw = kotlin.math.atan2(-dz, dx)
            val visibleYaw = cameraAssistedSpriteYaw(worldHeadingYaw, cameraYaw.toFloat())
            Matrix.rotateM(fighterTransform, 0,
                Math.toDegrees(visibleYaw.toDouble()).toFloat(), 0f, 1f, 0f)
            val facesRight = dx * kotlin.math.cos(cameraYaw) - dz * kotlin.math.sin(cameraYaw) >= 0
            Matrix.scaleM(fighterTransform, 0,
                if (facesRight) -scale else scale,
                scale, scale)
            val root = engine.transformManager.getInstance(fighter.asset.root)
            if (root != 0) engine.transformManager.setTransform(root, fighterTransform)
        }
    }

    private fun animatedPose(fighter: RenderedFighter, requested: String, now: Long): String {
        val alternate = when (requested) {
            "walk" -> "walk2"
            "idle" -> "idle2"
            else -> null
        }
        val frame = if (alternate != null && (now / POSE_FRAME_NANOS) % 2L == 1L) alternate else requested
        return when {
            frame in fighter.poses -> frame
            requested in fighter.poses -> requested
            "idle" in fighter.poses -> "idle"
            else -> fighter.poses.keys.first()
        }
    }

    private fun destroyFighter(id: String) {
        val fighter = renderedFighters.remove(id) ?: return
        runCatching {
            viewer.scene.removeEntities(fighter.asset.entities)
            assetLoader?.destroyAsset(fighter.asset)
        }.onFailure { Log.w(TAG, "Could not release fighter $id", it) }
    }

    fun releaseScene() {
        if (released) return
        released = true
        arenaLoadGeneration++
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        releaseFighters()
        viewer.scene.skybox = null
        engine.destroySkybox(backgroundSkybox)
    }

    override fun onDetachedFromWindow() {
        if (!released) {
            released = true
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            releaseFighters()
            viewer.scene.skybox = null
            engine.destroySkybox(backgroundSkybox)
        }
        super.onDetachedFromWindow()
    }

    private fun releaseFighters() {
        renderedFighters.keys.toList().forEach(::destroyFighter)
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
    private val viewProjection = FloatArray(16)
    private val clipPosition = FloatArray(4)

    private companion object {
        const val TAG = "OfflineBattle3d"
        const val POSE_FRAME_NANOS = 460_000_000L
        const val PROJECTION_NOTIFY_NANOS = 75_000_000L
        const val CAMERA_FOCUS_EPSILON = 0.001
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
