package com.tamerslab.world.ui

import android.content.Context
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
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
import com.tamerslab.battle.ui.offline.OfflineArenaManifest
import com.tamerslab.rendering.EnvironmentThemeController
import com.tamerslab.rendering.HybridSceneKind
import com.tamerslab.rendering.applyHybridSceneProfile
import com.tamerslab.rendering.approachSceneAxis
import com.tamerslab.rendering.sprite3d.ResidentFrameImage
import com.tamerslab.rendering.sprite3d.SpriteExtrusionGlb
import com.tamerslab.ui.theme.AppTheme
import com.tamerslab.ui.theme.LocalAppTheme
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.createARGBIntArray
import com.tamerslab.world.GeoPoint
import com.tamerslab.world.RadarWorldGeometry
import com.tamerslab.world.ecosystem.EcosystemSnapshot
import com.tamerslab.world.ecosystem.InteractionState
import com.tamerslab.world.ecosystem.InteractionType
import com.tamerslab.world.ecosystem.displayPosition
import com.tamerslab.world.ecosystem.publicInteractionFor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One projected metre-ring label: viewport pixels for the text centre-top, 0..1 of the viewport. */
internal data class RadarRingLabel(val meters: Int, val x: Float, val y: Float)

/**
 * Process-wide cache of the extruded sprite GLB bytes.
 *
 * The radar place can be left and re-entered at any time; rebuilding a Digimon's extrusion on every
 * entry was the slowest part of the first render. The bytes depend only on the sprite artwork, so
 * they survive scene teardown and the walk back from Home. A bounded LRU keeps a whole 20-Digimon
 * roster plus the partner resident without growing with the species pool.
 */
internal object RadarSpriteGlbCache {
    private const val MAX_ENTRIES = 64
    private val models = object : LinkedHashMap<String, ByteArray>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) =
            size > MAX_ENTRIES
    }

    fun get(key: String): ByteArray? = synchronized(models) { models[key] }

    fun put(key: String, bytes: ByteArray) {
        synchronized(models) { models[key] = bytes }
    }
}

/**
 * Full-bleed isometric radar world.
 *
 * The Filament scene owns the authored radar plate (`Arena/Radar/radar_grid.glb`), one extruded
 * sprite GLB per wild Digimon and the active partner's extruded token at the plate centre. Compose
 * draws every crisp layer above it (compass ring, metre rings, labels, chrome) and positions its hit
 * targets with the same camera through [onProjection], exactly like [RadarFirstPersonViewport].
 *
 * The renderer owns one [Engine] at a time; it is created in the AndroidView factory and destroyed
 * by Filament's own detach listener when Compose releases the view. Callers must run the
 * [RadarRendererOwnership] handshake and only compose this viewport while it owns the renderer slot.
 *
 * @param active false pauses the frame loop (surface and engine stay alive); the loop restarts when
 *   the host becomes active again.
 * @param motion false freezes pose cycling, lunges and the zoom ease.
 * @param zoom shared zoom value: the visible radius is `200 / zoom` metres clamped to `0.5..4`.
 * @param onProjection receives wild-Digimon billboard rectangles in viewport pixels every ~100 ms;
 *   `x` is the billboard centre, `y` its top edge (the same contract as the first-person viewport).
 * @param pitchDegrees fixed camera elevation over the plate (the radar exposes no tilt gesture).
 * @param rings real-world metre rings drawn on the plate; their labels are projected through the same
 *   camera and reported through [onRingLabels].
 */
@Composable
internal fun RadarIsometricViewport(
    snapshot: EcosystemSnapshot,
    presentations: List<RadarPresentation>,
    playerSprite: RadarPlayerSprite?,
    heading: Float,
    zoom: Float,
    active: Boolean,
    motion: Boolean,
    onProjection: (List<RadarProjection>) -> Unit,
    onFailure: () -> Unit,
    onReleased: () -> Unit,
    onCreated: () -> Unit = {},
    pitchDegrees: Float = RADAR_ISO_PITCH_DEGREES,
    /** When set, the camera eases in on this wild Digimon for an in-world conversation. */
    conversationFocusId: String? = null,
    /** Optional pitch override for the conversation framing; defaults to a low eye-level angle. */
    conversationPitchDegrees: Float? = RADAR_ISO_CONVERSATION_PITCH_DEGREES,
    rings: List<RadarRingMark> = emptyList(),
    onRingLabels: (List<RadarRingLabel>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val appTheme = LocalAppTheme.current
    val backgroundColor = appTheme.palette.backgroundAlt
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).also { host ->
                val mainHandler = Handler(Looper.getMainLooper())
                runCatching { RadarIsometricSceneView(context, backgroundColor, appTheme) }
                    .onSuccess { scene ->
                        host.addView(
                            scene,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        scene.onFailure = { mainHandler.post(onFailure) }
                        scene.onReleased = { mainHandler.post(onReleased) }
                        onCreated()
                    }
                    .onFailure { mainHandler.post { onFailure(); onReleased() } }
            }
        },
        update = { host ->
            (host.getChildAt(0) as? RadarIsometricSceneView)?.apply {
                setTheme(appTheme)
                update(
                    snapshot = snapshot,
                    presentations = presentations,
                    playerSprite = playerSprite,
                    heading = heading,
                    zoom = zoom,
                    active = active,
                    motion = motion,
                    pitchDegrees = pitchDegrees,
                    conversationFocusId = conversationFocusId,
                    conversationPitchDegrees = conversationPitchDegrees,
                    rings = rings,
                    project = onProjection,
                    ringProject = onRingLabels,
                )
            }
        },
        onRelease = { host ->
            val scene = host.getChildAt(0) as? RadarIsometricSceneView
            scene?.releaseScene()
            val attached = scene?.isAttachedToWindow == true
            host.removeAllViews()
            if (!attached) scene?.releaseUnattached()
        },
    )
}

/**
 * The isometric radar's TextureView.
 *
 * Pinned ModelViewer 1.76.1 destroys its Engine synchronously in its own detach listener, so every
 * resource this class created must be released *before* the view detaches (or explicitly in
 * [releaseUnattached] when it never attached). The Choreographer loop runs on the main thread and is
 * capped at 30 fps; GL uploads are also main-thread and limited to one asset per frame.
 */
internal class RadarIsometricSceneView(
    context: Context,
    backgroundColor: Color = AppTheme.VB_HELPER.palette.backgroundAlt,
    initialTheme: AppTheme = AppTheme.VB_HELPER,
) : TextureView(context) {
    private val engine: Engine
    private val viewer: ModelViewer
    private val environmentThemes: EnvironmentThemeController
    private var sceneTheme = initialTheme
    private val provider: UbershaderProvider
    private val loader: AssetLoader
    private val resources: ResourceLoader
    private val skybox: Skybox
    // Three builders feed one main-thread upload stream: a full roster is extruded in parallel and
    // pushed to the GPU at most two models per frame, so the nearest Digimon appear within a frame
    // or two of entering the place instead of after a single-threaded queue of twenty builds.
    private val worker = Executors.newFixedThreadPool(3) { runnable ->
        Thread(runnable, "radar-iso-worker").apply { isDaemon = true }
    }

    private var released = false
    private var attachedOnce = false
    private var ambientLoaded = false
    private var ambientAsset: FilamentAsset? = null
    private var ambientRootInstance = 0
    private val ambientTransform = FloatArray(16)
    private var lastAmbientScale = Float.NaN
    private var lastAmbientHeading = Float.NaN
    // Ground scroll: the partner token stays at the plate centre, so walking is only visible if the
    // authored ground slides the other way, exactly like the legacy 2D radar's grid. The phase is
    // anchored to the first player fix and folded to half a grid cell, so the finite plate never
    // slides out from under the camera and a long walk cannot run out of ground.
    private var groundAnchor: GeoPoint? = null
    private var groundSmoothedMeters: RadarScenePoint? = null
    private var groundPhaseMeters = RadarScenePoint(0f, 0f)
    private var lastGroundOffsetX = Float.NaN
    private var lastGroundOffsetZ = Float.NaN

    private var active = true
    private var motion = true
    private var heading = 0f
    private var pitchDegrees = RADAR_ISO_PITCH_DEGREES
    private var targetVisibleRadius = RADAR_ISO_VISIBLE_RADIUS_AT_ZOOM_ONE_METERS
    private var displayedVisibleRadius = RADAR_ISO_VISIBLE_RADIUS_AT_ZOOM_ONE_METERS
    private var lastFrameNanos = 0L
    private var cameraPose = radarIsometricCamera(RADAR_ISO_PLATE_RADIUS_UNITS)
    private var cameraAspect = RADAR_ISO_REFERENCE_ASPECT_RATIO

    // In-world conversation framing: the camera eases from the radar overview to a close, low-angle
    // shot of one wild Digimon, which stands facing the camera. Kept separate from cameraPose so the
    // regular projection/culling math stays untouched.
    private var conversationFocusId: String? = null
    private var conversationPitchDegrees: Float = RADAR_ISO_CONVERSATION_PITCH_DEGREES
    private var conversationFocusPoint: RadarScenePoint? = null
    private var lastFrameDeltaSeconds = 1f / 60f
    private var cameraEyeX = Float.NaN
    private var cameraEyeY = Float.NaN
    private var cameraEyeZ = Float.NaN
    private var cameraTargetX = Float.NaN
    private var cameraTargetZ = Float.NaN

    private var snapshot = EcosystemSnapshot()
    private var wanted = emptyList<RadarPresentation>()
    private var requestedPlayerKey: String? = null
    private var playerStage = 0
    private var playerAsset: FilamentAsset? = null

    private val pending = linkedMapOf<String, ByteArray>()
    private val pendingDistances = mutableMapOf<String, Double>()
    private var pendingPlayer: ByteArray? = null
    private val requested = mutableSetOf<String>()
    private val failedLoads = mutableSetOf<String>()
    private val meshes = linkedMapOf<String, FilamentAsset>()
    private val actorTracks = mutableMapOf<String, ActorTrack>()

    // Metre rings are a ground decal in the 3D scene, not a Compose circle: they foreshorten with the
    // plate. The decal is rebuilt only when the zoom mapping or the palette ink changes.
    private var rings: List<RadarRingMark> = emptyList()
    private var ringGroundAsset: FilamentAsset? = null
    private var pendingRingGround: ByteArray? = null
    private var ringGroundRequestedKey: String? = null
    // A large theme-coloured ground plane under the authored plate: at a low tilt or a wide zoom the
    // authored floor's far edge would otherwise appear as the end of the world.
    private var backdropAsset: FilamentAsset? = null
    private var pendingBackdrop: ByteArray? = null
    private var backdropColorKey = Int.MIN_VALUE

    private val tmpMatrix = FloatArray(16)
    private var lastRender = 0L
    private var lastProjection = 0L
    /** Ids whose projected billboard is actually on screen this projection pass (zoom-safe culling). */
    private var onScreenIds: Set<String> = emptySet()
    private var project: (List<RadarProjection>) -> Unit = {}
    private var ringProject: (List<RadarRingLabel>) -> Unit = {}
    var onFailure: () -> Unit = {}
    var onReleased: () -> Unit = {}

    /**
     * Per-Digimon cardboard state, mirroring the Digifarm's resident state machine. Travel is
     * measured from the real-world position, so neither the compass rotation nor the zoom animation
     * can be mistaken for the Digimon changing direction.
     */
    private class ActorTrack(
        var worldPosition: GeoPoint? = null,
        /** False until this Digimon actually walks: standing sprites face the camera like the farm. */
        var hasTravel: Boolean = false,
        var headingBearing: Float = 0f,
        var desiredBearing: Float = 0f,
        var mirror: Boolean = false,
        /** Smoothed real-world speed: pose changes use hysteresis so frames cannot flicker. */
        var speedMetersPerSecond: Float = 0f,
        var walking: Boolean = false,
        var lastFrameNanos: Long = 0L,
        var animationMode: String = "idle",
        var animationFrame: Int = 0,
        var lastAnimationFrameNanos: Long = 0L,
    )

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (released || !active) return
            if (now - lastRender >= FRAME_INTERVAL_NANOS) {
                lastRender = now
                runCatching { render(now) }.onFailure { releaseScene(); onFailure() }
            }
            if (!released && active) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        Filament.init()
        System.loadLibrary("gltfio-jni")
        engine = Engine.create()
        viewer = ModelViewer(this, engine, UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK), null)
        environmentThemes = EnvironmentThemeController(context, engine, initialTheme)
        viewer.view.applyHybridSceneProfile(HybridSceneKind.RADAR_ISO)
        provider = UbershaderProvider(engine)
        loader = AssetLoader(engine, provider, EntityManager.get())
        resources = ResourceLoader(engine)
        val color = Colors.toLinear(
            Colors.RgbType.SRGB,
            backgroundColor.red,
            backgroundColor.green,
            backgroundColor.blue,
        )
        skybox = Skybox.Builder().color(color[0], color[1], color[2], 1f).build(engine)
        viewer.scene.skybox = skybox
        isOpaque = false
        isFocusable = false // Compose hit targets own selection; the camera never orbits.
        worker.execute {
            val result = runCatching {
                val manifest = OfflineArenaManifest.read(context, OfflineArenaManifest.RADAR_PLATE_MANIFEST_PATH)
                manifest to context.assets.open(manifest.assetPath).use { it.readBytes() }
            }
            post {
                if (released) return@post
                result.onSuccess { (manifest, bytes) ->
                    runCatching {
                        // `radar_plate.glb` is the authored radar arena with the battle boundary, the
                        // floating voxel props and the sky dome already stripped, so the radar world
                        // only shows the floor and its world grid (still theme-bound below).
                        viewer.loadModelGlb(buffer(bytes))
                        viewer.clearRootTransform()
                        val asset = checkNotNull(viewer.asset)
                        ambientAsset = asset
                        ambientRootInstance = engine.transformManager.getInstance(asset.root)
                        applyAmbientTransform(force = true)
                        environmentThemes.bind(asset, manifest.assetPath)
                        ambientLoaded = true
                    }.onFailure { onFailure() }
                }.onFailure { onFailure() }
            }
        }
        Choreographer.getInstance().postFrameCallback(frame)
    }

    /**
     * Pushes the latest Compose inputs. Cheap and idempotent: meshes are only requested once per
     * individual, and activating/deactivating restarts the frame loop without rebuilding the engine.
     */
    fun update(
        snapshot: EcosystemSnapshot,
        presentations: List<RadarPresentation>,
        playerSprite: RadarPlayerSprite?,
        heading: Float,
        zoom: Float,
        active: Boolean,
        motion: Boolean,
        pitchDegrees: Float,
        conversationFocusId: String? = null,
        conversationPitchDegrees: Float? = RADAR_ISO_CONVERSATION_PITCH_DEGREES,
        rings: List<RadarRingMark>,
        project: (List<RadarProjection>) -> Unit,
        ringProject: (List<RadarRingLabel>) -> Unit,
    ) {
        if (released) return
        this.snapshot = snapshot
        wanted = presentations
        this.heading = heading
        this.motion = motion
        this.project = project
        this.ringProject = ringProject
        this.rings = rings
        this.conversationFocusId = conversationFocusId
        this.conversationPitchDegrees = (conversationPitchDegrees ?: RADAR_ISO_CONVERSATION_PITCH_DEGREES)
            .coerceIn(20f, RADAR_ISO_MAX_PITCH_DEGREES)
        this.pitchDegrees = pitchDegrees.coerceIn(RADAR_ISO_MIN_PITCH_DEGREES, RADAR_ISO_MAX_PITCH_DEGREES)
        cameraPose = radarIsometricCamera(
            plateRadiusUnits = RADAR_ISO_PLATE_RADIUS_UNITS,
            pitchDegrees = this.pitchDegrees,
            aspectRatio = cameraAspect,
        )
        targetVisibleRadius = radarIsometricVisibleRadiusMeters(zoom)
        requestPlayerSprite(playerSprite)
        requestRingDecals()
        requestRingGround()
        requestBackdrop()
        if (this.active != active) {
            this.active = active
            Choreographer.getInstance().removeFrameCallback(frame)
            if (active) {
                lastFrameNanos = 0L
                Choreographer.getInstance().postFrameCallback(frame)
            }
        }
        val ids = presentations.map { it.individual.individualId }.toSet()
        requested.retainAll(ids)
        failedLoads.retainAll(ids)
        pending.keys.retainAll { it in ids }
        pendingDistances.keys.retainAll { it in ids }
        if (!active) return
        // Nearest first: the closest Digimon become visible almost immediately even when the roster
        // has to extrude twenty models. The distance is the same radial curve the plate uses.
        val player = radarPlayerPosition(snapshot)
        val ordered = if (player == null) presentations else presentations.sortedBy { item ->
            RadarWorldGeometry.relative(player, item.individual.position).distanceMeters
        }
        ordered.forEach { item ->
            val id = item.individual.individualId
            if (id in meshes || id in failedLoads || !requested.add(id)) return@forEach
            val art = item.assets
            val key = "${art.cardCharacterId}:${art.spriteIdle.contentHashCode()}:" +
                "${art.spriteIdle2.contentHashCode()}:${art.spriteWalk.contentHashCode()}:" +
                art.spriteWalk2.contentHashCode()
            val distance = player?.let {
                RadarWorldGeometry.relative(it, item.individual.position).distanceMeters
            } ?: 0.0
            worker.execute {
                val model = runCatching {
                    RadarSpriteGlbCache.get(key) ?: buildExtrudedSprite(
                        mapOf(
                            "idle" to art.spriteIdle,
                            "idle2" to art.spriteIdle2,
                            "walk" to art.spriteWalk,
                            "walk2" to art.spriteWalk2,
                        ).filterValues { it.isNotEmpty() },
                        art.spriteWidth,
                        art.spriteHeight,
                    ).also { RadarSpriteGlbCache.put(key, it) }
                }
                post {
                    if (!released && wanted.any { it.individual.individualId == id }) {
                        model.onSuccess { bytes ->
                            pending[id] = bytes
                            pendingDistances[id] = distance
                        }.onFailure { requested.remove(id); failedLoads.add(id) }
                    } else {
                        requested.remove(id)
                    }
                }
            }
        }
    }

    /** Rebuilds the skybox and the authored plate materials for the current app palette. */
    fun setTheme(theme: AppTheme) {
        if (released || theme == sceneTheme) return
        sceneTheme = theme
        val color = theme.palette.backgroundAlt
        val linear = Colors.toLinear(Colors.RgbType.SRGB, color.red, color.green, color.blue)
        skybox.setColor(linear[0], linear[1], linear[2], 1f)
        environmentThemes.setTheme(theme)
        requestRingDecals()
        requestRingGround()
        requestBackdrop()
    }

    /** Theme-coloured ground that continues past the authored floor's edge. */
    private fun requestBackdrop() {
        if (released) return
        val color = backdropColorArgb(sceneTheme)
        if (color == backdropColorKey && (backdropAsset != null || pendingBackdrop != null)) return
        backdropColorKey = color
        worker.execute {
            runCatching { buildBackdropGlb(color) }.onSuccess { bytes ->
                post { if (!released && backdropColorKey == color) pendingBackdrop = bytes }
            }
        }
    }

    /**
     * Rebuilds the ground ring decal when the zoom mapping (ring fractions) or the palette ink
     * changes. Runs on the worker pool; the GPU upload is queued on the main thread.
     */
    private fun requestRingGround() {
        if (released) return
        // Owner decision (2026-11-04): the Radar place draws no distance rings. The capability is
        // retained for a future re-enable; an empty ring list simply clears any ground decal.
        if (rings.isEmpty()) {
            ringGroundAsset?.let { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }
            ringGroundAsset = null
            pendingRingGround = null
            ringGroundRequestedKey = null
            return
        }
        val ink = ringGroundInkArgb(sceneTheme)
        // The decal only depends on the fixed ground fractions and the palette ink, never on zoom:
        // the rings are painted on the plate and stay put while the camera zooms.
        val key = buildString {
            append(ink).append(':')
            rings.forEach { ring -> append(ring.fraction).append(';') }
        }
        if (key == ringGroundRequestedKey) return
        ringGroundRequestedKey = key
        val snapshotRings = rings.toList()
        val darkTheme = sceneTheme.palette.isDark
        worker.execute {
            runCatching { buildRingGroundGlb(snapshotRings, ink, darkTheme) }
                .onSuccess { bytes ->
                    post { if (!released && ringGroundRequestedKey == key) pendingRingGround = bytes }
                }
        }
    }

    private fun requestPlayerSprite(sprite: RadarPlayerSprite?) {
        if (released) return
        val key = sprite?.let(::playerSpriteContentKey)
        if (key == requestedPlayerKey) return
        requestedPlayerKey = key
        if (sprite == null || sprite.frames.isEmpty()) {
            destroyPlayerAsset()
            pendingPlayer = null
            return
        }
        playerStage = sprite.stage
        val width = sprite.width
        val height = sprite.height
        val frames = sprite.frames
        worker.execute {
            val model = runCatching { buildExtrudedSprite(frames, width, height) }
            post {
                if (!released && requestedPlayerKey == key) {
                    model.onSuccess { pendingPlayer = it }
                }
            }
        }
    }

    private fun render(now: Long) {
        if (!ambientLoaded || width <= 0 || height <= 0) return
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        }
        lastFrameNanos = now
        lastFrameDeltaSeconds = deltaSeconds
        displayedVisibleRadius = if (motion) {
            approachSceneAxis(displayedVisibleRadius, targetVisibleRadius, deltaSeconds.toDouble(), 9.0)
        } else {
            targetVisibleRadius
        }
        val player = radarPlayerPosition(snapshot)
        // Resolve the conversation subject every frame so the camera tracks it even if it drifts.
        conversationFocusPoint = conversationFocusId?.let { focusId ->
            player?.let { origin ->
                wanted.firstOrNull { it.individual.individualId == focusId }
                    ?.let { wildPlacement(it, origin, now).point }
            }
        }
        updateGroundPhase(player, snapshot.playerFix?.accuracyMeters, deltaSeconds)
        applyCamera()
        applyAmbientTransform(force = false)
        loadNextAsset()
        val ids = wanted.map { it.individual.individualId }.toSet()
        actorTracks.keys.retainAll(ids)
        meshes.keys.filter { it !in ids }.forEach { id ->
            meshes.remove(id)?.let { asset ->
                viewer.scene.removeEntities(asset.entities)
                loader.destroyAsset(asset)
            }
            requested.remove(id)
        }
        if (now - lastProjection >= PROJECTION_INTERVAL_NANOS) {
            lastProjection = now
            projectActors(now)
        }
        if (player != null) {
            wanted.forEach { item ->
                val asset = meshes[item.individual.individualId] ?: return@forEach
                val isConversationFocus = item.individual.individualId == conversationFocusId
                // Visibility is decided by the projected billboard, not by the visible-radius value:
                // while zooming (or in the viewport corners) a Digimon can still be on screen even
                // when its radial distance is already outside the current radius. The conversation
                // subject is always placed: the cinematic camera frames it directly.
                if (!isConversationFocus && item.individual.individualId !in onScreenIds) {
                    hideWildActor(asset)
                } else {
                    val placement = wildPlacement(item, player, now)
                    placeWildActor(item.individual.individualId, asset, placement, now, deltaSeconds)
                }
            }
        } else {
            meshes.values.forEach(::hideWildActor)
            onScreenIds = emptySet()
        }
        placePlayerToken(now)
        placeRingGround()
        placeBackdrop()
        placeRingDecals()
        viewer.render(now)
        environmentThemes.onFrame(viewer.progress >= 1f)
    }

    private fun applyCamera() {
        viewer.cameraFocalLength = RADAR_ISO_FOCAL_MM
        // The enlarged theme floor reaches far past the plate so no edge can be seen at the lowest
        // angle; the far plane must cover it instead of clipping the ground near the horizon.
        viewer.cameraNear = RADAR_ISO_CAMERA_NEAR
        viewer.cameraFar = RADAR_ISO_CAMERA_FAR

        val focusPoint = conversationFocusPoint.takeIf { conversationFocusId != null }
        val desired = if (focusPoint != null) {
            val focusPose = radarIsometricCamera(
                plateRadiusUnits = RADAR_ISO_FOCUS_PLATE_RADIUS_UNITS,
                pitchDegrees = conversationPitchDegrees,
                aspectRatio = cameraAspect,
            )
            floatArrayOf(
                focusPoint.x,
                focusPose.eyeY,
                focusPoint.z + focusPose.eyeZ,
                focusPoint.x,
                focusPoint.z,
            )
        } else {
            val pose = radarIsometricCamera(
                plateRadiusUnits = RADAR_ISO_PLATE_RADIUS_UNITS,
                pitchDegrees = pitchDegrees,
                aspectRatio = cameraAspect,
            )
            floatArrayOf(0f, pose.eyeY, pose.eyeZ, 0f, 0f)
        }

        if (cameraEyeX.isNaN()) {
            cameraEyeX = desired[0]
            cameraEyeY = desired[1]
            cameraEyeZ = desired[2]
            cameraTargetX = desired[3]
            cameraTargetZ = desired[4]
        } else {
            // Exponential ease so entering/leaving the conversation is a camera move, not a cut.
            val alpha = (1.0 - exp(-lastFrameDeltaSeconds.coerceIn(0f, 0.1f) / 0.15f)).toFloat()
                .coerceIn(0f, 1f)
            cameraEyeX += (desired[0] - cameraEyeX) * alpha
            cameraEyeY += (desired[1] - cameraEyeY) * alpha
            cameraEyeZ += (desired[2] - cameraEyeZ) * alpha
            cameraTargetX += (desired[3] - cameraTargetX) * alpha
            cameraTargetZ += (desired[4] - cameraTargetZ) * alpha
        }

        viewer.camera.lookAt(
            cameraEyeX.toDouble(),
            cameraEyeY.toDouble(),
            cameraEyeZ.toDouble(),
            cameraTargetX.toDouble(),
            0.0,
            cameraTargetZ.toDouble(),
            0.0,
            1.0,
            0.0,
        )
    }

    /**
     * Rotates and scales the authored plate and slides it opposite to the player's travel, the way the
     * legacy 2D radar scrolls its grid under the centred marker. Rotation follows the compass so the
     * grid turns with the cardinal ring; scale follows the (eased) visible radius so one authored grid
     * cell always reads as [RADAR_ISO_GRID_CELL_METERS] real metres.
     */
    private fun applyAmbientTransform(force: Boolean) {
        val asset = ambientAsset ?: return
        if (ambientRootInstance == 0) return
        val scale = radarIsometricPlateAssetScale(displayedVisibleRadius, RADAR_ISO_PLATE_RADIUS_UNITS)
        val rotation = radarIsometricWorldRotation(heading)
        // The ground offset is kept as a fraction of one real grid cell, then expressed in the current
        // plate units: that keeps its phase correct while the plate re-scales for zoom.
        val unitsPerMeter = radarIsometricPlateUnitsPerMeter(displayedVisibleRadius)
        val northUpOffset = RadarScenePoint(
            x = groundPhaseMeters.x * unitsPerMeter,
            z = groundPhaseMeters.z * unitsPerMeter,
        )
        val ground = radarIsometricRotate(northUpOffset, heading)
        if (!force && scale == lastAmbientScale && rotation == lastAmbientHeading &&
            ground.x == lastGroundOffsetX && ground.z == lastGroundOffsetZ
        ) {
            return
        }
        lastAmbientScale = scale
        lastAmbientHeading = rotation
        lastGroundOffsetX = ground.x
        lastGroundOffsetZ = ground.z
        Matrix.setIdentityM(ambientTransform, 0)
        // Translation last so it lands in scene units after the plate's own rotate + scale.
        Matrix.translateM(ambientTransform, 0, ground.x, 0f, ground.z)
        Matrix.rotateM(ambientTransform, 0, rotation, 0f, 1f, 0f)
        Matrix.scaleM(ambientTransform, 0, scale, scale, scale)
        engine.transformManager.setTransform(ambientRootInstance, ambientTransform)
    }

    /**
     * Keeps the ground phase anchored to the player's first fix and moves it opposite to their real
     * travel, with a low-pass filter that scales with the reported fix accuracy.
     *
     * A GPS fix wanders by its accuracy when the player stands still, and the isometric plate is a
     * real-metre reference, so feeding raw fixes to it turns a standing player into a shaking grid.
     * The filter has a 0.35 s base time constant plus 0.08 s per metre of reported accuracy (a 10 m
     * fix follows with ~1.15 s), which averages the wander away while walking still reads within a
     * step or two. Fixes coarser than [GROUND_HOLD_ACCURACY_METERS] hold the grid entirely instead of
     * pretending to fine movement; the geometry layer then folds the smoothed travel to half a cell.
     */
    private fun updateGroundPhase(player: GeoPoint?, accuracyMeters: Float?, deltaSeconds: Float) {
        if (player == null) return
        val anchor = groundAnchor ?: player.also { groundAnchor = it }
        val relative = RadarWorldGeometry.relative(anchor, player)
        val target = RadarScenePoint(
            x = (-relative.eastMeters).toFloat(),
            z = relative.northMeters.toFloat(),
        )
        val smoothed = groundSmoothedMeters ?: target.also { groundSmoothedMeters = it }
        val accuracy = (accuracyMeters ?: 0f).coerceAtLeast(0f)
        if (accuracy > GROUND_HOLD_ACCURACY_METERS) {
            groundPhaseMeters = foldGroundPhase(smoothed)
            return
        }
        val tau = GROUND_FOLLOW_TAU_SECONDS + accuracy * GROUND_TAU_PER_ACCURACY_METER
        val alpha = (1.0 - exp(-deltaSeconds.coerceIn(0f, 0.1f) / tau)).toFloat().coerceIn(0f, 1f)
        val next = RadarScenePoint(
            x = smoothed.x + (target.x - smoothed.x) * alpha,
            z = smoothed.z + (target.z - smoothed.z) * alpha,
        )
        groundSmoothedMeters = next
        groundPhaseMeters = foldGroundPhase(next)
    }

    private fun foldGroundPhase(smoothed: RadarScenePoint): RadarScenePoint {
        val cell = RADAR_ISO_GRID_CELL_METERS
        return RadarScenePoint(
            x = foldToHalfCell(smoothed.x.toDouble(), cell),
            z = foldToHalfCell(smoothed.z.toDouble(), cell),
        )
    }

    /** Uploads the player token first, then the nearest pending Digimon. */
    private fun loadNextAsset() {
        pendingPlayer?.let { bytes ->
            pendingPlayer = null
            uploadPlayer(bytes)
            return
        }
        if (pendingRingDecalBytes.isNotEmpty() || pendingRingDecalAssets.isNotEmpty()) {
            uploadNextRingDecal()
            return
        }
        pendingRingGround?.let { bytes ->
            pendingRingGround = null
            uploadRingGround(bytes)
            return
        }
        pendingBackdrop?.let { bytes ->
            pendingBackdrop = null
            uploadBackdrop(bytes)
            return
        }
        val ids = wanted.map { it.individual.individualId }.toSet()
        pending.keys.retainAll { it in ids }
        pendingDistances.keys.retainAll { it in ids }
        repeat(MAX_UPLOADS_PER_FRAME) {
            if (pending.isEmpty()) return
            val nearest = pending.keys.minByOrNull { id -> pendingDistances[id] ?: Double.MAX_VALUE } ?: return
            val bytes = pending.remove(nearest) ?: return
            pendingDistances.remove(nearest)
            val asset = loader.createAsset(buffer(bytes)) ?: return@repeat
            resources.loadResources(asset)
            asset.releaseSourceData()
            viewer.scene.addEntities(asset.entities)
            meshes[nearest] = asset
        }
    }

    private fun uploadPlayer(bytes: ByteArray) {
        destroyPlayerAsset()
        val asset = loader.createAsset(buffer(bytes)) ?: return
        resources.loadResources(asset)
        asset.releaseSourceData()
        viewer.scene.addEntities(asset.entities)
        playerAsset = asset
    }

    private fun destroyPlayerAsset() {
        playerAsset?.let { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }
        playerAsset = null
    }

    private data class WildPlacement(
        val relativeDistanceMeters: Double,
        /** Real-world position of this Digimon for this frame; travel direction comes from here. */
        val worldPosition: GeoPoint,
        val point: RadarScenePoint,
        val scale: Float,
        val lunge: Float,
    )

    /**
     * Maps one wild Digimon to its standing pose on the plate: north-up bearing/distance through the
     * shared radial curve, meeting-participant separation, battle lunge, then the compass rotation.
     */
    private fun wildPlacement(item: RadarPresentation, player: GeoPoint, now: Long): WildPlacement {
        val actor = item.individual
        val displayPosition = actor.displayPosition(now, motion)
        val relative = RadarWorldGeometry.relative(player, displayPosition)
        val event = snapshot.publicInteractionFor(actor.individualId)
        val slot = event?.participantIds?.sorted()?.indexOf(actor.individualId) ?: 0
        val count = event?.participantIds?.size ?: 1
        val lunge = if (event?.type == InteractionType.BATTLE && event.state == InteractionState.ACTIVE) {
            radarActivityLunge(now, slot, motion)
        } else {
            0f
        }
        // Unclamped radial placement: a Digimon beyond the visible radius keeps its true (oversized)
        // radial position instead of collapsing onto the plate rim. Visibility is then decided by the
        // projected billboard, so zooming never hides an actor that is still on screen and never piles
        // every distant actor onto the rim.
        val visiblePresentation = radarPresentationDistance(displayedVisibleRadius)
        val radialFraction = if (visiblePresentation > 0.0) {
            radarPresentationDistance(relative.distanceMeters) / visiblePresentation
        } else {
            0.0
        }
        val radiusUnits = (radialFraction * RADAR_ISO_PLATE_RADIUS_UNITS).toFloat()
        val base = RadarScenePoint(
            x = (radiusUnits * sin(Math.toRadians(relative.bearingDegrees))).toFloat(),
            z = (-radiusUnits * cos(Math.toRadians(relative.bearingDegrees))).toFloat(),
        )
        val bearing = Math.toRadians(relative.bearingDegrees)
        var tangential = radarInteractionOffset(slot, count, RADAR_ISO_ACTIVITY_SPACING_UNITS)
        if (count == 2) tangential += (if (tangential < 0f) 1f else -1f) * lunge * RADAR_ISO_LUNGE_UNITS
        // Dens place several residents within a few metres, which reads as one sprite pile at radar
        // scale. A small deterministic fan-out keeps each Digimon tappable and readable without
        // falsifying the reported bearing/distance by more than a token amount.
        val spreadAngle = Math.toRadians(((item.individual.individualId.hashCode() % 360 + 360) % 360).toDouble())
        val northUp = RadarScenePoint(
            x = (base.x + cos(bearing) * tangential + cos(spreadAngle) * CARDROARD_SPREAD_UNITS).toFloat(),
            z = (base.z + sin(bearing) * tangential + sin(spreadAngle) * CARDROARD_SPREAD_UNITS).toFloat(),
        )
        return WildPlacement(
            relativeDistanceMeters = relative.distanceMeters,
            worldPosition = displayPosition,
            point = radarIsometricRotate(northUp, heading),
            scale = radarIsometricSpriteScale(item.assets.stage),
            lunge = lunge,
        )
    }

    private fun placeWildActor(
        id: String,
        asset: FilamentAsset,
        placement: WildPlacement,
        now: Long,
        deltaSeconds: Float,
    ) {
        // Same cardboard system as the Digifarm (Digifarm3dViewport.updateResidentPoses /
        // updateResidentTransforms). Travel comes from the real-world position: the plate rotates with
        // the compass and zooms with the camera, so measuring display motion made every compass tick
        // or pinch look like a direction change and flipped the sprites constantly.
        val track = actorTracks[id] ?: ActorTrack().also { actorTracks[id] = it }
        val movement = track.worldPosition?.let { previous ->
            RadarWorldGeometry.relative(previous, placement.worldPosition)
        }
        val movedMeters = movement?.distanceMeters ?: 0.0
        // Smoothed speed with start/stop hysteresis: the ecosystem interpolates positions in 1 s
        // segments, so a raw per-frame threshold flickered between walk and idle poses. The farm's
        // interpolation segment stays active for the whole step, which this EMA approximates.
        val frameDt = if (track.lastFrameNanos == 0L) {
            deltaSeconds.coerceIn(0f, 0.1f)
        } else {
            ((now - track.lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0)
                .toFloat().coerceIn(1f / 240f, 0.1f)
        }
        track.lastFrameNanos = now
        val instantSpeed = if (frameDt > 0f) (movedMeters / frameDt).toFloat() else 0f
        track.speedMetersPerSecond += (instantSpeed - track.speedMetersPerSecond) *
            CARDROARD_SPEED_SMOOTHING.coerceAtMost(1f)
        if (!track.walking && track.speedMetersPerSecond >= CARDROARD_WALK_START_SPEED) {
            track.walking = true
        } else if (track.walking && track.speedMetersPerSecond <= CARDROARD_WALK_STOP_SPEED) {
            track.walking = false
        }
        if (movement != null && movedMeters >= CARDROARD_MIN_WORLD_MOVEMENT_METERS) {
            track.hasTravel = true
            // World bearing of the travel: 0 = north, clockwise positive (the radar's convention).
            track.desiredBearing = Math.toRadians(movement.bearingDegrees).toFloat()
            val turn = atan2(
                sin(track.desiredBearing - track.headingBearing),
                cos(track.desiredBearing - track.headingBearing),
            )
            if (abs(turn) > Math.PI.toFloat() / 2f) {
                // A reversal flips the cardboard immediately; a gradual turn eases at the farm rate.
                track.headingBearing = track.desiredBearing
            } else {
                val maxTurn = CARDROARD_TURN_SPEED * frameDt.coerceAtMost(0.1f)
                track.headingBearing = normalizeRadians(track.headingBearing + turn.coerceIn(-maxTurn, maxTurn))
            }
        }
        track.worldPosition = placement.worldPosition
        // During an overworld conversation the subject stands and faces the camera, like the farm's
        // idle residents: travel bearings would otherwise leave it turned away from the player.
        val facingCamera = id == conversationFocusId
        val orientation = radarCardboardOrientation(
            worldBearingDegrees = Math.toDegrees(track.headingBearing.toDouble()).toFloat(),
            hasTravel = track.hasTravel && !facingCamera,
            compassHeadingDegrees = heading,
            previousMirror = track.mirror,
            flipThreshold = CARDROARD_FACING_FLIP_THRESHOLD,
            maxRelativeYaw = CARDROARD_CAMERA_ASSIST_YAW,
        )
        track.mirror = orientation.mirror
        val visibleYaw = orientation.visibleYaw
        val manager = engine.transformManager
        val rootInstance = manager.getInstance(asset.root)
        if (rootInstance != 0) {
            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, placement.point.x, 0f, placement.point.z)
            Matrix.rotateM(
                tmpMatrix, 0,
                Math.toDegrees(visibleYaw.toDouble()).toFloat(), 0f, 1f, 0f,
            )
            val xScale = if (track.mirror) -placement.scale else placement.scale
            Matrix.scaleM(tmpMatrix, 0, xScale, placement.scale, placement.scale)
            manager.setTransform(rootInstance, tmpMatrix)
        }
        setRenderableLayer(asset, "contact_shadow", 1)
        val available = availablePoses(asset)
        val pose = updateWildAnimation(track, track.walking || placement.lunge > 0f, now, available)
        available.forEach { name ->
            setRenderableLayer(asset, "pose_$name", if (name == pose) 1 else 0)
        }
    }

    /**
     * Frame pacing copied from the farm's island animation: walking flips every 250 ms, idling every
     * 950 ms, reduced motion holds the first frame, and a missing `*2` frame falls back cleanly.
     */
    private fun updateWildAnimation(
        track: ActorTrack,
        walking: Boolean,
        now: Long,
        available: Set<String>,
    ): String? {
        val mode = if (walking) "walk" else "idle"
        if (track.animationMode != mode) {
            track.animationMode = mode
            track.animationFrame = 0
            track.lastAnimationFrameNanos = now
        } else {
            val frameDuration = if (mode == "walk") CARDROARD_WALK_FRAME_NANOS else CARDROARD_IDLE_FRAME_NANOS
            val elapsed = now - track.lastAnimationFrameNanos
            if (motion && elapsed >= frameDuration) {
                val steps = (elapsed / frameDuration).toInt()
                track.animationFrame = (track.animationFrame + steps) % 2
                track.lastAnimationFrameNanos += steps * frameDuration
            }
        }
        val alternate = if (motion && track.animationFrame == 1) mode + "2" else mode
        return listOfNotNull(alternate, mode, "idle", "idle2").firstOrNull { it in available }
    }

    private fun normalizeRadians(radians: Float): Float {
        val wrapped = radians % (2f * Math.PI.toFloat())
        return if (wrapped < -Math.PI.toFloat()) wrapped + (2f * Math.PI.toFloat()) else wrapped
    }

    private fun hideWildActor(asset: FilamentAsset) {
        availablePoses(asset).forEach { name -> setRenderableLayer(asset, "pose_$name", 0) }
        setRenderableLayer(asset, "contact_shadow", 0)
    }

    private fun availablePoses(asset: FilamentAsset): Set<String> =
        RADAR_ISO_POSE_NAMES.filter { asset.getFirstEntityByName("pose_$it") != 0 }.toSet()

    private fun setRenderableLayer(asset: FilamentAsset, entityName: String, layer: Int) {
        val entity = asset.getFirstEntityByName(entityName)
        if (entity == 0) return
        val instance = engine.renderableManager.getInstance(entity)
        if (instance != 0) engine.renderableManager.setLayerMask(instance, 0xFF, layer)
    }

    private fun placePlayerToken(now: Long) {
        val asset = playerAsset ?: return
        val scale = radarIsometricSpriteScale(playerStage) * RADAR_ISO_PLAYER_SCALE
        val manager = engine.transformManager
        val rootInstance = manager.getInstance(asset.root)
        if (rootInstance != 0) {
            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.scaleM(tmpMatrix, 0, scale, scale, scale)
            manager.setTransform(rootInstance, tmpMatrix)
        }
        setRenderableLayer(asset, "contact_shadow", 1)
        val available = availablePoses(asset)
        val alternate = motion && (now / PLAYER_IDLE_FRAME_NANOS) % 2L == 1L
        val framePose = radarIsometricPose(
            walking = false,
            motion = motion,
            alternate = alternate,
            available = available,
        )
        available.forEach { name ->
            setRenderableLayer(asset, "pose_$name", if (name == framePose) 1 else 0)
        }
    }

    /**
     * Radius of the interaction-proximity ring in plate units for the current zoom: the same radial
     * fraction the renderer gives a Digimon exactly [RadarWorldGeometry.INTERACTION_RANGE_METERS]
     * metres away, so anything inside the ring is inside the range the Radar uses for chatting,
     * battling and quest interactions.
     */
    private fun interactionRingRadiusUnits(): Float =
        radarIsometricRadialFraction(
            RadarWorldGeometry.INTERACTION_RANGE_METERS,
            displayedVisibleRadius,
        ) * RADAR_ISO_PLATE_RADIUS_UNITS

    /**
     * The interaction-proximity ring, drawn as a real 3D ground decal so the normal depth buffer
     * makes Digimon occlude it instead of a Compose overlay painting over them. Thickness stays
     * constant on screen without rebuilding during a pinch: a small ladder of decals is baked once
     * per palette (each calibrated for a different ring radius) and their alpha is cross-faded as the
     * ring radius changes, so the width follows the zoom smoothly and never jumps.
     */
    private class RingDecal(val asset: FilamentAsset, val radiusUnits: Float) {
        val materials = mutableListOf<com.google.android.filament.MaterialInstance>()
    }

    private val ringDecals = mutableListOf<RingDecal>()
    private val pendingRingDecalAssets = mutableListOf<RingDecal>()
    private val pendingRingDecalBytes = linkedMapOf<Float, ByteArray>()
    private var ringDecalColorKey = Int.MIN_VALUE
    private var ringDecalExpected = 0
    private var ringDecalBuilding = false

    private fun ringDecalRadii(): List<Float> = RING_DECAL_VISIBLE_RADII.map { visibleRadius ->
        (radarIsometricRadialFraction(
            RadarWorldGeometry.INTERACTION_RANGE_METERS,
            visibleRadius,
        ) * RADAR_ISO_PLATE_RADIUS_UNITS)
    }

    private fun requestRingDecals() {
        if (released) return
        val color = ringColorArgb(sceneTheme)
        if (color == ringDecalColorKey && (ringDecals.isNotEmpty() || pendingRingDecalBytes.isNotEmpty())) return
        if (ringDecalBuilding) return
        ringDecalColorKey = color
        ringDecalBuilding = true
        val radii = ringDecalRadii()
        ringDecalExpected = radii.size
        // A rebuild can start while a previous ladder is still uploading; those assets are already in
        // the scene, so destroy them before dropping the references (Filament panics if material
        // instances outlive their provider).
        destroyRingDecalAssets(pendingRingDecalAssets)
        worker.execute {
            val built = radii.associateWith { radius ->
                runCatching { buildRingDecalGlb(color, radius) }.getOrNull()
            }
            post {
                ringDecalBuilding = false
                if (released || ringDecalColorKey != color) return@post
                val ordered = radii.mapNotNull { radius -> built[radius]?.let { radius to it } }
                pendingRingDecalBytes.clear()
                ordered.forEach { (radius, bytes) -> pendingRingDecalBytes[radius] = bytes }
            }
        }
    }

    /** One decal upload per frame; the old ladder stays visible until the new one is complete. */
    private fun uploadNextRingDecal() {
        if (pendingRingDecalBytes.isEmpty()) {
            if (pendingRingDecalAssets.size >= ringDecalExpected && ringDecalExpected > 0 &&
                ringDecals.size != ringDecalExpected
            ) {
                destroyRingDecals()
                ringDecals.addAll(pendingRingDecalAssets)
                pendingRingDecalAssets.clear()
            }
            return
        }
        val first = pendingRingDecalBytes.entries.first()
        pendingRingDecalBytes.remove(first.key)
        val asset = loader.createAsset(buffer(first.value)) ?: return
        resources.loadResources(asset)
        asset.releaseSourceData()
        viewer.scene.addEntities(asset.entities)
        val decal = RingDecal(asset, first.key)
        for (entity in asset.entities) {
            val instance = engine.renderableManager.getInstance(entity)
            if (instance == 0) continue
            // The authored world grid is a translucent mesh too, so Filament depth-sorts the two. Now
            // that the plate slides under the ring, that sort key flips with the walking direction; a
            // late render priority keeps the ring above the grid no matter where the plate has moved.
            engine.renderableManager.setPriority(instance, RING_DECAL_PRIORITY)
            for (primitive in 0 until engine.renderableManager.getPrimitiveCount(instance)) {
                decal.materials += engine.renderableManager.getMaterialInstanceAt(instance, primitive)
            }
        }
        pendingRingDecalAssets += decal
    }

    private fun destroyRingDecalAssets(assets: MutableList<RingDecal>) {
        assets.forEach { decal ->
            viewer.scene.removeEntities(decal.asset.entities)
            loader.destroyAsset(decal.asset)
        }
        assets.clear()
    }

    private fun destroyRingDecals() = destroyRingDecalAssets(ringDecals)

    private fun placeRingDecals() {
        if (ringDecals.isEmpty()) return
        val radius = interactionRingRadiusUnits()
        val sorted = ringDecals.sortedBy { it.radiusUnits }
        var lower = sorted.first()
        var upper = sorted.last()
        if (sorted.size > 1) {
            for (index in 0 until sorted.size - 1) {
                if (radius >= sorted[index].radiusUnits && radius <= sorted[index + 1].radiusUnits) {
                    lower = sorted[index]
                    upper = sorted[index + 1]
                    break
                }
            }
        }
        val span = (upper.radiusUnits - lower.radiusUnits).coerceAtLeast(0.0001f)
        val blend = ((radius - lower.radiusUnits) / span).coerceIn(0f, 1f)
        // The authored grid sits above the floor and scales with the ambient plate, so the ring must
        // clear it at every zoom instead of using a fixed scene-space height.
        val gridY = RADAR_ISO_AUTHORED_GRID_HEIGHT_UNITS *
            radarIsometricPlateAssetScale(displayedVisibleRadius, RADAR_ISO_PLATE_RADIUS_UNITS)
        val ringY = gridY + RING_DECAL_LIFT_UNITS
        val manager = engine.transformManager
        for (decal in sorted) {
            val weight = when {
                decal === lower && decal === upper -> 1f
                decal === lower -> 1f - blend
                decal === upper -> blend
                else -> 0f
            }
            decal.materials.forEach { material ->
                material.setParameter("baseColorFactor", 1f, 1f, 1f, weight * RING_DECAL_ALPHA)
            }
            val rootInstance = manager.getInstance(decal.asset.root)
            if (rootInstance == 0) continue
            // Every decal is re-scaled to the live ring radius; its baked band width then reads as
            // (calibrated width x current/calibrated radius), and the alpha ladder interpolates it.
            val diameter = radius * 2f / RING_BAND_RADIUS
            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, 0f, ringY, 0f)
            Matrix.rotateM(tmpMatrix, 0, -90f, 1f, 0f, 0f)
            Matrix.scaleM(tmpMatrix, 0, diameter, diameter, diameter)
            manager.setTransform(rootInstance, tmpMatrix)
        }
    }

    private fun buildRingDecalGlb(argbColor: Int, referenceRadiusUnits: Float): ByteArray {
        val size = if (referenceRadiusUnits > 2f) RING_DECAL_TEXTURE_SIZE else RING_DECAL_TEXTURE_SIZE / 2
        val planeHalfUnits = (referenceRadiusUnits / RING_BAND_RADIUS).coerceAtLeast(0.001f)
        val minSigma = RING_DECAL_MIN_SIGMA_PX / (size / 2f)
        val sigma = (RING_DECAL_LINE_UNITS / planeHalfUnits).coerceAtLeast(minSigma)
        val red = (argbColor ushr 16) and 0xFF
        val green = (argbColor ushr 8) and 0xFF
        val blue = argbColor and 0xFF
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val nx = (x + 0.5f) / size * 2f - 1f
            val ny = (y + 0.5f) / size * 2f - 1f
            val radius = sqrt(nx * nx + ny * ny)
            val offset = radius - RING_BAND_RADIUS
            val alpha = (exp(-(offset / sigma) * (offset / sigma)) * 255f).toInt().coerceIn(0, 255)
            pixels[y * size + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return SpriteExtrusionGlb.buildBillboard(ResidentFrameImage(pixels, size, size))
    }

    /** Lay the metre-ring decal flat on the plate; it turns and re-scales with the radar world. */
    private fun placeRingGround() {
        val asset = ringGroundAsset ?: return
        val manager = engine.transformManager
        val rootInstance = manager.getInstance(asset.root)
        if (rootInstance == 0) return
        val diameter = RADAR_ISO_PLATE_RADIUS_UNITS * 2f
        // Same rule as the interaction ring: clear the authored grid at every zoom, otherwise the
        // metre rings sink under its lines once the plate scales up.
        val gridY = RADAR_ISO_AUTHORED_GRID_HEIGHT_UNITS *
            radarIsometricPlateAssetScale(displayedVisibleRadius, RADAR_ISO_PLATE_RADIUS_UNITS)
        Matrix.setIdentityM(tmpMatrix, 0)
        Matrix.translateM(tmpMatrix, 0, 0f, gridY + RING_DECAL_LIFT_UNITS, 0f)
        Matrix.rotateM(tmpMatrix, 0, radarIsometricWorldRotation(heading), 0f, 1f, 0f)
        Matrix.rotateM(tmpMatrix, 0, -90f, 1f, 0f, 0f)
        Matrix.scaleM(tmpMatrix, 0, diameter, diameter, diameter)
        manager.setTransform(rootInstance, tmpMatrix)
    }

    private fun uploadRingGround(bytes: ByteArray) {
        ringGroundAsset?.let { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }
        ringGroundAsset = null
        val asset = loader.createAsset(buffer(bytes)) ?: return
        resources.loadResources(asset)
        asset.releaseSourceData()
        viewer.scene.addEntities(asset.entities)
        for (entity in asset.entities) {
            val instance = engine.renderableManager.getInstance(entity)
            if (instance != 0) engine.renderableManager.setPriority(instance, RING_GROUND_PRIORITY)
        }
        ringGroundAsset = asset
    }

    /**
     * Bakes every metre ring into one transparent ground texture. The stroke radius is the ring's
     * fraction of the plate radius, so the decal rings line up with the sprites' radial mapping and
     * foreshorten with the plate instead of staying screen-space circles.
     */
    private fun buildRingGroundGlb(rings: List<RadarRingMark>, argbColor: Int, darkTheme: Boolean): ByteArray {
        val size = RING_GROUND_TEXTURE_SIZE
        val red = (argbColor ushr 16) and 0xFF
        val green = (argbColor ushr 8) and 0xFF
        val blue = argbColor and 0xFF
        val haloAlpha = if (darkTheme) 0.11f else 0.16f
        val coreAlpha = if (darkTheme) 0.30f else 0.42f
        val pixels = IntArray(size * size)
        val half = size / 2f
        for (ring in rings) {
            val ringRadius = (ring.fraction.coerceIn(0f, 1f) * half * 0.985f)
            if (ringRadius <= RING_GROUND_HALO_PX) continue
            val outer = ringRadius + RING_GROUND_HALO_PX
            val inner = ringRadius - RING_GROUND_HALO_PX
            val outerSq = outer * outer
            val innerSq = inner * inner
            for (y in 0 until size) {
                val dy = (y + 0.5f) - half
                val dySq = dy * dy
                for (x in 0 until size) {
                    val dx = (x + 0.5f) - half
                    val distanceSq = dx * dx + dySq
                    if (distanceSq > outerSq || distanceSq < innerSq) continue
                    val distance = sqrt(distanceSq)
                    val offset = abs(distance - ringRadius)
                    // Soft ground marking: a wide, low-alpha halo with a quiet core line.
                    val halo = (1f - offset / RING_GROUND_HALO_PX).coerceIn(0f, 1f).pow(2f)
                    val core = (1f - offset / RING_GROUND_CORE_PX).coerceIn(0f, 1f)
                    val alphaF = (halo * haloAlpha + core * coreAlpha).coerceIn(0f, 1f)
                    val alpha = (alphaF * 255f).toInt().coerceIn(0, 255)
                    if (alpha <= pixels[y * size + x] ushr 24) continue
                    pixels[y * size + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
        }
        return SpriteExtrusionGlb.buildBillboard(ResidentFrameImage(pixels, size, size))
    }

    private fun ringGroundInkArgb(theme: AppTheme): Int {
        val color = theme.palette.textPrimary
        val red = (color.red * 255f).toInt().coerceIn(0, 255)
        val green = (color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (color.blue * 255f).toInt().coerceIn(0, 255)
        return (red shl 16) or (green shl 8) or blue
    }

    private fun backdropColorArgb(theme: AppTheme): Int {
        val color = theme.palette.backgroundAlt
        val red = (color.red * 255f).toInt().coerceIn(0, 255)
        val green = (color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (color.blue * 255f).toInt().coerceIn(0, 255)
        return (red shl 16) or (green shl 8) or blue
    }

    private fun buildBackdropGlb(argbColor: Int): ByteArray {
        val red = (argbColor ushr 16) and 0xFF
        val green = (argbColor ushr 8) and 0xFF
        val blue = argbColor and 0xFF
        val pixels = IntArray(2 * 2) { (0xFF shl 24) or (red shl 16) or (green shl 8) or blue }
        return SpriteExtrusionGlb.buildBillboard(ResidentFrameImage(pixels, 2, 2))
    }

    private fun uploadBackdrop(bytes: ByteArray) {
        backdropAsset?.let { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }
        backdropAsset = null
        val asset = loader.createAsset(buffer(bytes)) ?: return
        resources.loadResources(asset)
        asset.releaseSourceData()
        viewer.scene.addEntities(asset.entities)
        backdropAsset = asset
    }

    /** Lays the huge theme ground well below the authored floor, so no edge is ever visible. */
    private fun placeBackdrop() {
        val asset = backdropAsset ?: return
        val manager = engine.transformManager
        val rootInstance = manager.getInstance(asset.root)
        if (rootInstance == 0) return
        Matrix.setIdentityM(tmpMatrix, 0)
        Matrix.translateM(tmpMatrix, 0, 0f, BACKDROP_Y_UNITS, 0f)
        Matrix.rotateM(tmpMatrix, 0, radarIsometricWorldRotation(heading), 0f, 1f, 0f)
        Matrix.rotateM(tmpMatrix, 0, -90f, 1f, 0f, 0f)
        Matrix.scaleM(tmpMatrix, 0, BACKDROP_SIZE_UNITS, BACKDROP_SIZE_UNITS, BACKDROP_SIZE_UNITS)
        manager.setTransform(rootInstance, tmpMatrix)
    }

    /**
     * Projects each live wild billboard through view × projection and reports the clamped screen
     * rectangle. Corners behind the camera are dropped (the same clipping workaround the first-person
     * viewport uses), and rectangles fully outside the viewport are skipped.
     */
    private fun projectActors(now: Long) {
        val player = radarPlayerPosition(snapshot)
        if (player == null) {
            onScreenIds = emptySet()
            project(emptyList())
            ringProject(emptyList())
            return
        }
        val view = viewer.camera.getViewMatrix(FloatArray(16))
        val projectionDouble = viewer.camera.getProjectionMatrix(DoubleArray(16))
        val projectionFloat = FloatArray(16) { projectionDouble[it].toFloat() }
        val viewProjection = FloatArray(16)
        Matrix.multiplyMM(viewProjection, 0, projectionFloat, 0, view, 0)
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val clip = FloatArray(4)
        val world = FloatArray(4)
        val projections = wanted.mapNotNull { item ->
            projectActor(item, player, now, viewProjection, viewWidth, viewHeight, clip, world)
        }
        onScreenIds = projections.mapTo(HashSet()) { it.individualId }
        project(projections)
        ringProject(projectRingLabels(viewProjection, viewWidth, viewHeight))
    }

    /** One clamped billboard rect for [item]; shared by the marker pass and the ring occlusion pass. */
    private fun projectActor(
        item: RadarPresentation,
        player: GeoPoint,
        now: Long,
        viewProjection: FloatArray,
        viewWidth: Float,
        viewHeight: Float,
        clip: FloatArray,
        world: FloatArray,
    ): RadarProjection? {
        val placement = wildPlacement(item, player, now)
        val aspect = (
            item.assets.spriteWidth.toFloat() / item.assets.spriteHeight.coerceAtLeast(1)
            ).coerceIn(0.5f, 1.8f)
        val halfWidth = placement.scale * aspect / 2f
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (corner in 0 until 4) {
            world[0] = placement.point.x + if (corner == 0 || corner == 2) -halfWidth else halfWidth
            world[1] = if (corner < 2) 0f else placement.scale
            world[2] = placement.point.z
            world[3] = 1f
            Matrix.multiplyMV(clip, 0, viewProjection, 0, world, 0)
            if (clip[3] <= 0f) return null
            val px = (clip[0] / clip[3] + 1f) * 0.5f * viewWidth
            val py = (1f - (clip[1] / clip[3] + 1f) * 0.5f) * viewHeight
            if (px < left) left = px
            if (px > right) right = px
            if (py < top) top = py
            if (py > bottom) bottom = py
        }
        val clampedLeft = left.coerceAtLeast(0f)
        val clampedRight = right.coerceAtMost(viewWidth)
        val clampedTop = top.coerceAtLeast(0f)
        val clampedBottom = bottom.coerceAtMost(viewHeight)
        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return null
        return RadarProjection(
            individualId = item.individual.individualId,
            x = (clampedLeft + clampedRight) / 2f,
            y = clampedTop,
            distance = placement.relativeDistanceMeters,
            width = clampedRight - clampedLeft,
            height = clampedBottom - clampedTop,
        )
    }

    /**
     * Projects the metre-ring labels. Each ring is a ground decal, so its label is anchored to a
     * ground point on the same ring and follows the plate's foreshortening instead of floating on a
     * screen-space circle.
     */
    private fun projectRingLabels(
        viewProjection: FloatArray,
        viewWidth: Float,
        viewHeight: Float,
    ): List<RadarRingLabel> {
        if (rings.isEmpty()) return emptyList()
        val clip = FloatArray(4)
        return rings.mapNotNull { ring ->
            // Rings are baked into the plate at fixed fractions: the label rides that same ground
            // fraction, so it never slides when the zoom changes.
            val radius = ring.fraction.coerceIn(0f, 1f) * RADAR_ISO_PLATE_RADIUS_UNITS
            val radians = Math.toRadians(ring.labelBearingDegrees.toDouble())
            val northUp = RadarScenePoint(
                x = (radius * sin(radians)).toFloat(),
                z = (-radius * cos(radians)).toFloat(),
            )
            val rotated = radarIsometricRotate(northUp, heading)
            Matrix.multiplyMV(
                clip, 0, viewProjection, 0,
                floatArrayOf(rotated.x, RING_LABEL_HEIGHT_UNITS, rotated.z, 1f), 0,
            )
            if (clip[3] <= 0f) return@mapNotNull null
            val px = (clip[0] / clip[3] + 1f) * 0.5f * viewWidth
            val py = (1f - (clip[1] / clip[3] + 1f) * 0.5f) * viewHeight
            if (px < 0f || px > viewWidth || py < 0f || py > viewHeight) return@mapNotNull null
            RadarRingLabel(meters = ring.meters, x = px, y = py)
        }
    }

    /** Releases every GPU resource this view created. Safe to call more than once. */
    fun releaseScene() {
        if (released) return
        released = true
        active = false
        worker.shutdownNow()
        Choreographer.getInstance().removeFrameCallback(frame)
        meshes.values.forEach { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }
        meshes.clear()
        pending.clear()
        pendingDistances.clear()
        actorTracks.clear()
        pendingPlayer = null
        pendingRingGround = null
        pendingBackdrop = null
        pendingRingDecalBytes.clear()
        ringDecalBuilding = false
        destroyPlayerAsset()
        destroyRingDecals()
        destroyRingDecalAssets(pendingRingDecalAssets)
        ringGroundAsset?.let { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }
        ringGroundAsset = null
        backdropAsset?.let { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }
        backdropAsset = null
        loader.destroy()
        resources.destroy()
        provider.destroyMaterials()
        provider.destroy()
        environmentThemes.release()
        viewer.scene.skybox = null
        engine.destroySkybox(skybox)
        // ModelViewer registers its own detach listener and owns destruction of the Engine. Marking
        // the view released here prevents another frame while that listener performs the teardown.
    }

    fun releaseUnattached() {
        if (!attachedOnce) {
            viewer.destroy()
            onReleased()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedOnce = true
    }

    override fun onDetachedFromWindow() {
        releaseScene()
        super.onDetachedFromWindow()
        // ModelViewer's attach listener runs after this override; post acknowledges the destruction.
        Handler(Looper.getMainLooper()).post { onReleased() }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) {
            val aspect = (width.toFloat() / height.toFloat()).coerceIn(0.2f, 5f)
            if (aspect != cameraAspect) {
                cameraAspect = aspect
                cameraPose = radarIsometricCamera(
                    plateRadiusUnits = RADAR_ISO_PLATE_RADIUS_UNITS,
                    pitchDegrees = pitchDegrees,
                    aspectRatio = aspect,
                )
            }
        }
    }

    private fun buildExtrudedSprite(
        frames: Map<String, ByteArray>,
        width: Int,
        height: Int,
    ): ByteArray {
        val spriteWidth = width.coerceAtLeast(1)
        val spriteHeight = height.coerceAtLeast(1)
        return SpriteExtrusionGlb.build(
            frames.mapValues { (_, bytes) ->
                ResidentFrameImage(
                    argb = BitmapData(bytes, spriteWidth, spriteHeight).createARGBIntArray(),
                    width = spriteWidth,
                    height = spriteHeight,
                )
            },
        )
    }

    private fun playerSpriteContentKey(sprite: RadarPlayerSprite): String = buildString {
        append(sprite.width).append('x').append(sprite.height).append(':').append(sprite.stage)
        sprite.frames.toSortedMap().forEach { (name, bytes) ->
            append(':').append(name).append('=').append(bytes.contentHashCode())
        }
    }

    private fun ringColorArgb(theme: AppTheme): Int {
        val color = theme.palette.primaryBright
        val red = (color.red * 255f).toInt().coerceIn(0, 255)
        val green = (color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (color.blue * 255f).toInt().coerceIn(0, 255)
        return (red shl 16) or (green shl 8) or blue
    }

    private fun buffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }

    private companion object {
        const val FRAME_INTERVAL_NANOS = 33_333_333L
        const val PROJECTION_INTERVAL_NANOS = 100_000_000L
        const val PLAYER_IDLE_FRAME_NANOS = 900_000_000L
        val RADAR_ISO_POSE_NAMES = listOf("idle", "idle2", "walk", "walk2")

        /** Screen-space outline of the interaction-proximity ground ring. */
        const val RING_DECAL_TEXTURE_SIZE = 512
        const val RING_BAND_RADIUS = 0.86f
        /** Lift above the authored grid so the ring never sinks under the floor lines. */
        const val RING_DECAL_LIFT_UNITS = 0.004f
        /** Authored grid height in the plate GLB; it scales with the ambient asset. */
        const val RADAR_ISO_AUTHORED_GRID_HEIGHT_UNITS = 0.018f
        /** Constant on-screen thickness of the ring line, expressed in plate units. */
        const val RING_DECAL_LINE_UNITS = 0.026f
        const val RING_DECAL_ALPHA = 0.62f
        const val RING_DECAL_MIN_SIGMA_PX = 2.5f
        /** Visible radii the baked decal ladder is calibrated for (THICKEST at the closest zoom). */
        val RING_DECAL_VISIBLE_RADII = listOf(400.0, 260.0, 170.0, 120.0, 80.0, 50.0)

        /** Ground ring decal: one transparent plate-sized texture carrying every metre ring. */
        const val RING_GROUND_TEXTURE_SIZE = 1024
        const val RING_GROUND_HALO_PX = 9f
        const val RING_GROUND_CORE_PX = 1.3f
        /**
         * Filament priorities: 4 is the default and 7 renders last. The authored world grid is a
         * translucent mesh, so the ground markings are given a late priority to stay above it.
         */
        const val RING_GROUND_PRIORITY = 5
        const val RING_DECAL_PRIORITY = 6
        /**
         * Ground-follow low-pass: a GPS fix wanders by its accuracy while the player stands still and
         * the plate is a real-metre reference, so the filter's time constant grows with the reported
         * accuracy instead of turning that wander into a shaking grid.
         */
        const val GROUND_FOLLOW_TAU_SECONDS = 0.35f
        const val GROUND_TAU_PER_ACCURACY_METER = 0.08f
        /** Coarser than this, a fix is not allowed to move the ground at all. */
        const val GROUND_HOLD_ACCURACY_METERS = 30f
        const val RING_LABEL_BEARING_DEGREES = 55.0
        const val RING_LABEL_HEIGHT_UNITS = 0.03f

        /** Large theme ground under the authored plate; hides its far edge at low tilt/wide zoom. */
        const val BACKDROP_SIZE_UNITS = 320f
        const val BACKDROP_Y_UNITS = -0.02f

        /** Camera planes: the derived plate floor reaches far beyond the plate so it is never clipped. */
        const val RADAR_ISO_CAMERA_NEAR = 0.05f
        const val RADAR_ISO_CAMERA_FAR = 4000f

        /** Two upload per frame keeps the nearest first; the player token owns its own slot. */
        const val MAX_UPLOADS_PER_FRAME = 2

        /** Real movement smaller than this is presentation noise: the Digimon is standing still. */
        const val CARDROARD_MIN_WORLD_MOVEMENT_METERS = 0.004
        /** Smoothed real speed: the ecosystem moves in 1 s segments, so pose changes use hysteresis. */
        const val CARDROARD_SPEED_SMOOTHING = 0.22f
        const val CARDROARD_WALK_START_SPEED = 0.35f
        const val CARDROARD_WALK_STOP_SPEED = 0.12f
        /** Radar camera assistance is tighter than the farm's (±15°) so cardboard reads front-facing. */
        const val CARDROARD_CAMERA_ASSIST_YAW = 0.26f
        /** Same easing rate as the Digifarm residents (radians per second). */
        const val CARDROARD_TURN_SPEED = 12f
        /** Facing latch: wider than the farm's 0.16 so a 1° compass step cannot chatter the mirror. */
        const val CARDROARD_FACING_FLIP_THRESHOLD = 0.22f
        /** Same frame durations the farm uses for the distance-interpolated sprite poses. */
        const val CARDROARD_WALK_FRAME_NANOS = 250_000_000L
        const val CARDROARD_IDLE_FRAME_NANOS = 950_000_000L
        /** Deterministic per-Digimon fan-out that separates residents sharing one den. */
        const val CARDROARD_SPREAD_UNITS = 0.30f
    }
}
