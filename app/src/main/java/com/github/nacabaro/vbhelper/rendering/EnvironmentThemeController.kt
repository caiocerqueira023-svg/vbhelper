package com.github.nacabaro.vbhelper.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.FilamentAsset
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.exp

/** Preserve the dark-theme envelope; smoothly compress HDR peaks on light surfaces. */
internal fun sceneEmissionStrength(theme: AppTheme, strength: Float): Float =
    if (theme.palette.isDark || strength <= 1f) strength
    else 1f + .15f * (1.0 - exp(1.0 - strength)).toFloat()

/** Scene-owned GPU resources. Decode on a worker; bind only on Filament's owning/main thread. */
internal class EnvironmentThemeController(context: Context, private val engine: Engine, initialTheme: AppTheme) {
    private val assets = context.applicationContext.assets
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var job: Future<*>? = null
    private var generation = 0
    private var released = false
    private var resourcesReady = false
    private var assetPath: String? = null
    private var requestedTheme = initialTheme
    private var materialInstances: List<MaterialInstance> = emptyList()
    private var activeVariants: Map<Long, EnvironmentMaterialVariant> = emptyMap()
    private val ownedTextures = linkedSetOf<Texture>()
    private var overridesInstalled = false
    private var pending: PreparedTheme? = null

    var appliedTheme: AppTheme? = null
        private set
    var failure: String? = null
        private set
    val ready get() = appliedTheme == requestedTheme && failure == null
    val boundMaterialNames get() = materialInstances.filter { it.nativeObject in activeVariants }.map { it.name }.toSet()
    var bindCount = 0
        private set

    private data class PreparedTheme(val theme: AppTheme, val scene: EnvironmentSceneTheme?, val images: Map<String, Bitmap>) {
        fun discard() = images.values.forEach { if (!it.isRecycled) it.recycle() }
    }

    fun setTheme(theme: AppTheme) {
        if (released || requestedTheme == theme) return
        requestedTheme = theme
        appliedTheme = null
        prepare()
    }

    fun bind(asset: FilamentAsset, path: String) {
        if (released) return
        clearEnvironment()
        assetPath = path
        val manager = engine.renderableManager
        materialInstances = asset.entities.filter { manager.hasComponent(it) }.flatMap { entity ->
            val instance = manager.getInstance(entity)
            (0 until manager.getPrimitiveCount(instance)).map { manager.getMaterialInstanceAt(instance, it) }
        }.distinctBy { it.nativeObject }
        bindCount++
        prepare()
    }

    /** ResourceLoader may assign authored textures asynchronously; override after it finishes. */
    fun onFrame(loaded: Boolean) {
        if (released) return
        resourcesReady = loaded
        applyPending()
    }

    /** The animation owns only its envelope; the active theme owns emission RGB and gain. */
    fun updateEmission(material: MaterialInstance, strength: Float): Boolean {
        if (released) return false
        val variant = activeVariants[material.nativeObject] ?: return false
        val e = variant.emission
        val envelope = sceneEmissionStrength(appliedTheme ?: requestedTheme, strength)
        material.setParameter("emissiveFactor", e[0] * envelope, e[1] * envelope, e[2] * envelope)
        return true
    }

    private fun prepare() {
        val path = assetPath ?: return
        val token = ++generation
        val theme = requestedTheme
        val needOriginalTextures = overridesInstalled
        failure = null
        job?.cancel(true)
        pending?.discard()
        pending = null
        job = worker.submit {
            val images = LinkedHashMap<String, Bitmap>()
            val prepared = runCatching {
                val catalog = assets.open(EnvironmentThemeCatalog.ASSET).bufferedReader().use { EnvironmentThemeCatalog.parse(it.readText()) }
                val scene = catalog.sceneForAsset(path)
                if (theme != AppTheme.VB_HELPER || needOriginalTextures) {
                    scene?.materials?.flatMap { it.variant(theme).textures.values }?.distinctBy { it.asset }?.forEach { texture ->
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val bitmap = assets.open(texture.asset).use {
                            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                inPremultiplied = false
                            })
                        }
                        images[texture.asset] = checkNotNull(bitmap) { "Cannot decode ${texture.asset}" }
                    }
                }
                PreparedTheme(theme, scene, images)
            }
            handler.post {
                if (released || token != generation) {
                    images.values.forEach { it.recycle() }
                    return@post
                }
                prepared.onSuccess { pending = it; applyPending() }.onFailure {
                    images.values.forEach { bitmap -> bitmap.recycle() }
                    failure = it.message ?: "Environment palette preparation failed"
                    Timber.tag(TAG).e(it, "Could not prepare %s for %s", path, theme.preferenceValue)
                }
            }
        }
    }

    private fun applyPending() {
        val prepared = pending ?: return
        if (!resourcesReady || released) return
        pending = null
        val created = LinkedHashMap<String, Texture>()
        val submitted = mutableSetOf<Bitmap>()
        try {
            val definitions = prepared.scene?.materials.orEmpty().associateBy { it.name }
            val bindings = materialInstances.mapNotNull { instance ->
                definitions[instance.name]?.let { instance to it.variant(prepared.theme) }
            }
            check(prepared.scene == null || definitions.keys == bindings.map { it.first.name }.toSet()) {
                "Environment material names changed: expected ${definitions.keys}, found ${materialInstances.map { it.name }}"
            }
            for ((instance, variant) in bindings) for (parameter in variant.textures.keys) {
                check(instance.material.hasParameter(parameter)) { "${instance.name} has no $parameter" }
            }
            for ((path, bitmap) in prepared.images) {
                val texture = Texture.Builder().width(bitmap.width).height(bitmap.height).levels(0xff)
                    .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8)
                    .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE).build(engine)
                created[path] = texture
                ownedTextures += texture
                TextureHelper.setBitmap(engine, texture, 0, bitmap, handler, Runnable { bitmap.recycle() })
                submitted += bitmap
                texture.generateMipmaps(engine)
            }
            for ((instance, variant) in bindings) {
                for ((parameter, spec) in variant.textures) {
                    val texture = created[spec.asset] ?: continue // First Helper load retains gltfio's exact originals.
                    instance.setParameter(parameter, texture, sampler(spec.sampler))
                }
                val b = variant.baseColor
                if (instance.material.hasParameter("baseColorFactor")) instance.setParameter("baseColorFactor", b[0], b[1], b[2], b[3])
                val e = variant.emission
                if (instance.material.hasParameter("emissiveFactor")) instance.setParameter("emissiveFactor", e[0], e[1], e[2])
            }
            activeVariants = bindings.associate { it.first.nativeObject to it.second }
            val previous = ownedTextures - created.values.toSet()
            previous.forEach { engine.destroyTexture(it) }
            ownedTextures.removeAll(previous)
            overridesInstalled = created.isNotEmpty()
            appliedTheme = prepared.theme
        } catch (error: Exception) {
            // Keep any partially bound textures alive until model teardown; never leave a native dangling sampler.
            prepared.images.values.filter { it !in submitted }.forEach { it.recycle() }
            failure = error.message ?: "Environment palette binding failed"
            Timber.tag(TAG).e(error, "Could not apply environment theme")
        }
    }

    fun clearEnvironment() {
        generation++
        job?.cancel(true)
        pending?.discard(); pending = null
        materialInstances = emptyList(); activeVariants = emptyMap()
        ownedTextures.forEach { engine.destroyTexture(it) }; ownedTextures.clear()
        appliedTheme = null; failure = null; resourcesReady = false; overridesInstalled = false; assetPath = null
    }

    fun release() {
        if (released) return
        clearEnvironment()
        released = true
        worker.shutdownNow()
    }

    private fun sampler(spec: EnvironmentSampler): TextureSampler = TextureSampler(
        when (spec.minFilter) {
            9728 -> TextureSampler.MinFilter.NEAREST
            9729 -> TextureSampler.MinFilter.LINEAR
            9984 -> TextureSampler.MinFilter.NEAREST_MIPMAP_NEAREST
            9985 -> TextureSampler.MinFilter.LINEAR_MIPMAP_NEAREST
            9986 -> TextureSampler.MinFilter.NEAREST_MIPMAP_LINEAR
            else -> TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR
        }, if (spec.magFilter == 9728) TextureSampler.MagFilter.NEAREST else TextureSampler.MagFilter.LINEAR,
        wrap(spec.wrapS), wrap(spec.wrapT), TextureSampler.WrapMode.REPEAT,
    )

    private fun wrap(value: Int) = when (value) {
        33071 -> TextureSampler.WrapMode.CLAMP_TO_EDGE
        33648 -> TextureSampler.WrapMode.MIRRORED_REPEAT
        else -> TextureSampler.WrapMode.REPEAT
    }

    private companion object { const val TAG = "EnvironmentTheme" }
}
