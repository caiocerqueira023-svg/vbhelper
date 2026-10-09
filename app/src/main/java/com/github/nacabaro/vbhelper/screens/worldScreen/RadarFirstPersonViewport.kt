package com.github.nacabaro.vbhelper.screens.worldScreen

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
import androidx.compose.ui.viewinterop.AndroidView
import com.github.nacabaro.vbhelper.rendering.HybridSceneKind
import com.github.nacabaro.vbhelper.rendering.applyHybridSceneProfile
import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import com.github.nacabaro.vbhelper.rendering.sprite3d.SpriteExtrusionGlb
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineArenaManifest
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.createARGBIntArray
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemSnapshot
import com.github.nacabaro.vbhelper.world.ecosystem.displayPosition
import com.github.nacabaro.vbhelper.world.ecosystem.publicInteractionFor
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionType
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionState
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

data class RadarProjection(val individualId: String, val x: Float, val y: Float, val distance: Double, val width: Float = 64f, val height: Float = 64f)

@Composable
internal fun RadarFirstPersonViewport(
    snapshot: EcosystemSnapshot, presentations: List<RadarPresentation>, heading: Float, pitch: Float,
    active: Boolean, motion: Boolean, onProjection: (List<RadarProjection>) -> Unit,
    onFailure: () -> Unit, onReleased: () -> Unit, onCreated: () -> Unit = {}, modifier: Modifier = Modifier
) {
    val backgroundColor = DeepPurpleBgAlt
    AndroidView(modifier = modifier, factory = { context ->
        FrameLayout(context).also { host ->
            val mainHandler=Handler(Looper.getMainLooper())
            runCatching { RadarFirstPersonSceneView(context, backgroundColor) }.onSuccess { scene ->
                host.addView(scene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                scene.onFailure = { mainHandler.post(onFailure) }
                scene.onReleased = { mainHandler.post(onReleased) }
                onCreated()
            }.onFailure { mainHandler.post { onFailure(); onReleased() } }
        }
    }, update = { host ->
        (host.getChildAt(0) as? RadarFirstPersonSceneView)?.update(snapshot, presentations, heading, pitch, active, motion, onProjection)
    }, onRelease = { host ->
        val scene = host.getChildAt(0) as? RadarFirstPersonSceneView
        scene?.releaseScene()
        val attached = scene?.isAttachedToWindow == true
        host.removeAllViews()
        if (!attached) scene?.releaseUnattached()
    })
}

/** Pinned ModelViewer 1.76.1 destroys its Engine synchronously in its detach listener. */
internal class RadarFirstPersonSceneView(
    context: Context,
    backgroundColor: Color = AppTheme.VB_HELPER.palette.backgroundAlt,
) : TextureView(context) {
    private val engine: Engine
    private val viewer: ModelViewer
    private val provider: UbershaderProvider
    private val loader: AssetLoader
    private val resources: ResourceLoader
    private val skybox: Skybox
    private val worker = Executors.newSingleThreadExecutor()
    private var released = false
    private var attachedOnce = false
    private var loaded = false
    private var active = true
    private var motion = true
    private var heading = 0f
    private var pitch = RADAR_DEFAULT_PITCH
    private var snapshot = EcosystemSnapshot()
    private var wanted = emptyList<RadarPresentation>()
    private val pending = linkedMapOf<String, ByteArray>()
    private val requested = mutableSetOf<String>()
    private val failedLoads = mutableSetOf<String>()
    private val meshes = linkedMapOf<String, FilamentAsset>()
    private val cache = object : LinkedHashMap<String, ByteArray>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 24
    }
    private var lastRender = 0L
    private var lastProjection = 0L
    private var project: (List<RadarProjection>) -> Unit = {}
    var onFailure: () -> Unit = {}
    var onReleased: () -> Unit = {}
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (released || !active) return
            if (now - lastRender >= 33_333_333L) {
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
        viewer.view.applyHybridSceneProfile(HybridSceneKind.RADAR_FP)
        provider = UbershaderProvider(engine)
        loader = AssetLoader(engine, provider, EntityManager.get())
        resources = ResourceLoader(engine)
        val color = Colors.toLinear(Colors.RgbType.SRGB, backgroundColor.red, backgroundColor.green, backgroundColor.blue)
        skybox = Skybox.Builder().color(color[0], color[1], color[2], 1f).build(engine)
        viewer.scene.skybox = skybox
        isOpaque = false
        isFocusable = false // Accessible Compose hit targets/list own selection; no camera gestures.
        worker.execute {
            val result = runCatching {
                val manifest = OfflineArenaManifest.read(context, OfflineArenaManifest.RADAR_MANIFEST_PATH)
                manifest to context.assets.open(manifest.assetPath).use { it.readBytes() }
            }
            post {
                if (released) return@post
                result.onSuccess { (manifest, bytes) ->
                    runCatching {
                        viewer.loadModelGlb(buffer(bytes)); viewer.clearRootTransform()
                        val asset = checkNotNull(viewer.asset)
                        val rootTransform = FloatArray(16)
                        Matrix.setIdentityM(rootTransform, 0)
                        Matrix.scaleM(rootTransform, 0, manifest.visualScale, manifest.visualScale, manifest.visualScale)
                        engine.transformManager.setTransform(engine.transformManager.getInstance(asset.root), rootTransform)
                        listOf("Sphere001", "battle_boundary").forEach { name ->
                            val entity = asset.getFirstEntityByName(name)
                            val instance = engine.renderableManager.getInstance(entity)
                            if (instance != 0) engine.renderableManager.setLayerMask(instance, 0xFF, 0)
                        }
                        loaded = true
                    }.onFailure { onFailure() }
                }.onFailure { onFailure() }
            }
        }
        Choreographer.getInstance().postFrameCallback(frame)
    }

    fun update(snapshot: EcosystemSnapshot, presentations: List<RadarPresentation>, heading: Float, pitch: Float, active: Boolean, motion: Boolean,
        project: (List<RadarProjection>) -> Unit) {
        if (released) return
        this.snapshot = snapshot; wanted = presentations; this.heading = heading; this.pitch = pitch; this.motion = motion; this.project = project
        if (this.active != active) {
            this.active = active
            Choreographer.getInstance().removeFrameCallback(frame)
            if (active) Choreographer.getInstance().postFrameCallback(frame)
        }
        val ids = presentations.map { it.individual.individualId }.toSet()
        requested.retainAll(ids)
        failedLoads.retainAll(ids)
        if (!active) return
        presentations.forEach { item ->
            val id = item.individual.individualId
            if (id in meshes || id in failedLoads || !requested.add(id)) return@forEach
            val art = item.assets
            val key = "${art.cardCharacterId}:${art.spriteIdle.contentHashCode()}:${art.spriteWalk.contentHashCode()}"
            worker.execute {
                val model = runCatching {
                    synchronized(cache) { cache[key] } ?: SpriteExtrusionGlb.build(mapOf(
                        "idle" to art.spriteIdle, "idle2" to art.spriteIdle2, "walk" to art.spriteWalk, "walk2" to art.spriteWalk2
                    ).filterValues { it.isNotEmpty() }.mapValues { (_, bytes) ->
                        ResidentFrameImage(BitmapData(bytes, art.spriteWidth.coerceAtLeast(1), art.spriteHeight.coerceAtLeast(1)).createARGBIntArray(),
                            art.spriteWidth.coerceAtLeast(1), art.spriteHeight.coerceAtLeast(1))
                    }).also { synchronized(cache) { cache[key] = it } }
                }
                post {
                    if (!released && wanted.any { it.individual.individualId == id }) {
                        model.onSuccess { pending[id] = it }.onFailure { requested.remove(id); failedLoads.add(id) }
                    } else requested.remove(id)
                }
            }
        }
    }

    private fun render(now: Long) {
        if (!loaded || width <= 0 || height <= 0) return
        viewer.cameraFocalLength = 24f; viewer.cameraNear = 0.05f; viewer.cameraFar = 240f
        val camera = radarFirstPersonCamera(heading, pitch)
        viewer.camera.lookAt(0.0, camera.eyeHeight, 0.0, camera.targetX, camera.targetY, camera.targetZ, 0.0, 1.0, 0.0)
        val ids = wanted.map { it.individual.individualId }.toSet()
        pending.keys.removeAll { it !in ids }
        meshes.keys.filter { it !in ids }.forEach { id -> meshes.remove(id)?.let { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }; requested.remove(id) }
        pending.entries.firstOrNull { it.key in ids }?.let { (id, bytes) ->
            val asset = checkNotNull(loader.createAsset(buffer(bytes)))
            resources.loadResources(asset); asset.releaseSourceData(); viewer.scene.addEntities(asset.entities)
            meshes[id] = asset; pending.remove(id) // At most one GL upload per frame.
        }
        val player = radarPlayerPosition(snapshot) ?: return
        wanted.forEach { item ->
            val actor = item.individual
            val asset = meshes[actor.individualId] ?: return@forEach
            val geographic = actor.displayPosition(now, motion)
            val relative = RadarWorldGeometry.relative(player, geographic)
            val event=snapshot.publicInteractionFor(actor.individualId)
            val slot=event?.participantIds?.sorted()?.indexOf(actor.individualId) ?: 0
            val lunge=if(event?.type==InteractionType.BATTLE && event.state==InteractionState.ACTIVE)
                radarActivityLunge(now,slot,motion) else 0f
            val point = relative.activityDisplayPoint(slot,event?.participantIds?.size ?: 1,lunge)
            val scale = radarFirstPersonSpriteScale(item.assets.stage)
            val billboard = radarFirstPersonBillboard(point,scale,camera.eyeHeight)
            val transform = FloatArray(16); Matrix.setIdentityM(transform, 0)
            Matrix.translateM(transform, 0, point.x, billboard.centerY, point.z)
            Matrix.rotateM(transform, 0, Math.toDegrees(billboard.yawRadians.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.rotateM(transform, 0, Math.toDegrees(billboard.pitchRadians.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.scaleM(transform, 0, scale, scale, scale)
            Matrix.translateM(transform, 0, 0f, -0.5f, 0f) // Rotate about the sprite center, not its feet.
            engine.transformManager.setTransform(engine.transformManager.getInstance(asset.root), transform)
            val availablePoses = listOf("idle", "idle2", "walk", "walk2").filter { asset.getFirstEntityByName("pose_$it") != 0 }.toSet()
            val framePose = radarSpritePose(lunge>0f || actor.movementState != com.github.nacabaro.vbhelper.domain.world.WorldMovementState.HOME,
                motion, (now / 460_000_000L) % 2L == 1L, availablePoses)
            availablePoses.forEach { name ->
                val entity = asset.getFirstEntityByName("pose_$name")
                val instance = engine.renderableManager.getInstance(entity)
                if (instance != 0) engine.renderableManager.setLayerMask(instance, 0xFF, if (name == framePose) 1 else 0)
            }
        }
        viewer.render(now)
        if (now - lastProjection >= 100_000_000L) {
            lastProjection = now
            val view = viewer.camera.getViewMatrix(FloatArray(16))
            val projection = viewer.camera.getProjectionMatrix(DoubleArray(16)).map { it.toFloat() }.toFloatArray()
            val vp = FloatArray(16); Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
            project(wanted.mapNotNull { item ->
                val relative = RadarWorldGeometry.relative(player, item.individual.displayPosition(now, motion))
                val event=snapshot.publicInteractionFor(item.individual.individualId)
                val slot=event?.participantIds?.sorted()?.indexOf(item.individual.individualId) ?: 0
                val lunge=if(event?.type==InteractionType.BATTLE && event.state==InteractionState.ACTIVE) radarActivityLunge(now,slot,motion) else 0f
                val p = relative.activityDisplayPoint(slot,event?.participantIds?.size ?: 1,lunge)
                val spriteHeight=radarFirstPersonSpriteScale(item.assets.stage)
                val billboard=radarFirstPersonBillboard(p,spriteHeight,camera.eyeHeight)
                val halfWidth=spriteHeight*(item.assets.spriteWidth.toFloat()/item.assets.spriteHeight.coerceAtLeast(1)).coerceIn(0.5f,1.8f)/2
                val corners=listOf(-1f to -1f,1f to -1f,-1f to 1f,1f to 1f).mapNotNull { (side,vertical) ->
                    val world=billboard.worldPoint(side*halfWidth,vertical*spriteHeight/2)
                    val clip=FloatArray(4)
                    Matrix.multiplyMV(clip,0,vp,0,floatArrayOf(world[0],world[1],world[2],1f),0)
                    if(clip[3]<=0f) null else ((clip[0]/clip[3]+1)*width/2) to ((1-clip[1]/clip[3])*height/2)
                }
                if(corners.size!=4) return@mapNotNull null
                val left=corners.minOf { it.first }.coerceAtLeast(0f)
                val right=corners.maxOf { it.first }.coerceAtMost(width.toFloat())
                val top=corners.minOf { it.second }.coerceAtLeast(0f)
                val bottom=corners.maxOf { it.second }.coerceAtMost(height.toFloat())
                if(right<=left || bottom<=top) null else RadarProjection(item.individual.individualId,(left+right)/2,top,
                    relative.distanceMeters,right-left,bottom-top)
            })
        }
    }
    fun releaseScene() {
        if (released) return
        released = true; active = false; worker.shutdownNow()
        Choreographer.getInstance().removeFrameCallback(frame)
        meshes.values.forEach { viewer.scene.removeEntities(it.entities); loader.destroyAsset(it) }; meshes.clear()
        loader.destroy(); resources.destroy(); provider.destroyMaterials(); provider.destroy()
        viewer.scene.skybox = null; engine.destroySkybox(skybox)
    }
    fun releaseUnattached() {
        if (!attachedOnce) { viewer.destroy(); onReleased() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); attachedOnce = true }
    override fun onDetachedFromWindow() {
        releaseScene()
        super.onDetachedFromWindow()
        // ModelViewer's attach listener runs after this override; post acknowledges its Engine destruction.
        Handler(Looper.getMainLooper()).post { onReleased() }
    }
    private fun buffer(bytes: ByteArray) = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
}
