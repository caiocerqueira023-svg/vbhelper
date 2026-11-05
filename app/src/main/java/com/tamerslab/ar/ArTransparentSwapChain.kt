package com.tamerslab.ar

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Engine
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Viewport
import com.google.android.filament.utils.ModelViewer
import timber.log.Timber
import java.lang.reflect.Field

/**
 * Hosts a Filament [ModelViewer] on a transparent swap chain so the rendered scene can be
 * composited over the AR camera feed.
 *
 * Why this exists: Filament 1.76.1 `ModelViewer` always creates its swap chain with
 * `Engine.createSwapChain(surface)` (opaque, `CONFIG_DEFAULT`): `UiHelper.setOpaque(false)` is
 * ignored because `ModelViewer$SurfaceCallback` creates the chain itself. An opaque swap chain
 * gives the `TextureView` no alpha channel, so it covers the camera with black regardless of
 * `View.BlendMode.TRANSLUCENT` or the renderer clear colour.
 *
 * A second EGL window surface cannot be opened on a `SurfaceTexture` that `ModelViewer` already
 * attached (EGL_BAD_SURFACE and a native crash). The supported pattern is therefore:
 *
 * 1. Build the viewer with the *unattached* constructor
 *    `ModelViewer(engine, uiHelper)` (no view, so it never opens a surface);
 * 2. call [start] with the AR `TextureView`: this class owns the `SurfaceTextureListener`,
 *    creates one `CONFIG_TRANSPARENT` swap chain on the view's `SurfaceTexture`, installs it into
 *    `ModelViewer`'s private `swapChain` field and keeps the view viewport in sync;
 * 3. call [isReady] before `viewer.render(...)` (skip the frame while there is no chain);
 * 4. call [stop] when the view is torn down (flushes the engine before destroying the chain).
 *
 * Use one instance per viewer. Only valid for the detached-viewer construction; calling it on a
 * view-attached `ModelViewer` is refused (`ensure` returns false) instead of crashing.
 */
class ArTransparentSwapChainInstaller(private val engine: Engine) {

    private companion object {
        const val TAG = "ArTransparentSwapChain"
        val swapChainField: Field by lazy {
            ModelViewer::class.java.getDeclaredField("swapChain").apply { isAccessible = true }
        }
        val uiHelperField: Field by lazy {
            ModelViewer::class.java.getDeclaredField("uiHelper").apply { isAccessible = true }
        }
        val hasSwapChainField: Field by lazy {
            com.google.android.filament.android.UiHelper::class.java
                .getDeclaredField("mHasSwapChain")
                .apply { isAccessible = true }
        }
    }

    private var viewer: ModelViewer? = null
    private var textureView: TextureView? = null
    private var surface: Surface? = null
    private var swapChain: SwapChain? = null
    private var released = false

    private val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
            createChain()
        }

        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
            updateViewport()
        }

        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            destroyChain()
            return true
        }

        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
    }

    /** Prepares the transparent host for [textureView] and the unattached [viewer]. */
    fun start(viewer: ModelViewer, textureView: TextureView) {
        if (released) return
        if (this.viewer === viewer && this.textureView === textureView) return
        this.viewer = viewer
        this.textureView = textureView
        // The detached viewer has no swap chain of its own; refuse to fight an attached one.
        if (runCatching { viewer.swapChain }.getOrNull() != null) {
            Timber.tag(TAG).w("Viewer already owns a swap chain; transparent host disabled")
            return
        }
        textureView.surfaceTextureListener = listener
        if (textureView.isAvailable) {
            createChain()
        } else {
            updateViewport()
        }
    }

    /**
     * Per-frame call-site helper: starts the host lazily and reports whether
     * `viewer.render(...)` may run. Safe to call every frame; the return value should gate the
     * render call so a missing swap chain never reaches Filament.
     */
    fun ensure(viewer: ModelViewer, textureView: TextureView): Boolean {
        if (released) return false
        if (this.viewer !== viewer || this.textureView !== textureView) start(viewer, textureView)
        return isReady()
    }

    /** `true` when a transparent swap chain is installed and `viewer.render(...)` is safe. */
    fun isReady(): Boolean = swapChain != null

    /** Detaches the listener, destroys the chain and releases the surface. Idempotent. */
    fun stop() {
        if (released) return
        released = true
        textureView?.surfaceTextureListener = null
        destroyChain()
        viewer = null
        textureView = null
    }

    private fun createChain() {
        if (released) return
        val currentViewer = viewer ?: return
        val currentTextureView = textureView ?: return
        if (swapChain != null && runCatching { currentViewer.swapChain }.getOrNull() === swapChain) {
            updateViewport()
            return
        }
        val st = currentTextureView.surfaceTexture ?: return
        destroyChain(keepViewer = true)
        val newSurface = Surface(st)
        val created = runCatching {
            engine.createSwapChain(newSurface, SwapChainFlags.CONFIG_TRANSPARENT)
        }.getOrNull()
        if (created == null) {
            Timber.tag(TAG).w("Could not create a transparent swap chain")
            newSurface.release()
            return
        }
        install(currentViewer, created)
        surface = newSurface
        swapChain = created
        updateViewport()
    }

    private fun updateViewport() {
        val currentViewer = viewer ?: return
        val width = textureView?.width ?: 0
        val height = textureView?.height ?: 0
        if (width <= 0 || height <= 0) return
        runCatching { currentViewer.view.viewport = Viewport(0, 0, width, height) }
    }

    private fun destroyChain(keepViewer: Boolean = false) {
        val chain = swapChain
        swapChain = null
        val currentViewer = viewer
        if (currentViewer != null && chain != null) {
            runCatching {
                engine.flushAndWait()
                if (currentViewer.swapChain === chain) install(currentViewer, null)
            }
        }
        if (chain != null) {
            runCatching { engine.destroySwapChain(chain) }
        }
        surface?.release()
        surface = null
        if (!keepViewer && released) viewer = null
    }

    private fun install(currentViewer: ModelViewer, chain: SwapChain?) {
        runCatching { swapChainField.set(currentViewer, chain) }
            .onFailure { Timber.tag(TAG).e(it, "Could not install the transparent swap chain") }
        // `ModelViewer.render()` early-returns while `UiHelper.isReadyToRender()` is false, and a
        // detached ModelViewer never attaches its UiHelper. We own the swap chain, so mirror its
        // existence into the helper's ready flag instead of attaching a second EGL surface.
        runCatching {
            val uiHelper = uiHelperField.get(currentViewer)
            hasSwapChainField.setBoolean(uiHelper, chain != null)
        }.onFailure { Timber.tag(TAG).w(it, "Could not update UiHelper ready state") }
    }
}
