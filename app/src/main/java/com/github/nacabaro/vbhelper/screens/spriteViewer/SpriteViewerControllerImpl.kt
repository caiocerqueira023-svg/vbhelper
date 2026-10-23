package com.github.nacabaro.vbhelper.screens.spriteViewer

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.ComponentActivity
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.ui.SpritePreviewPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class SpriteViewerControllerImpl(private val context: ComponentActivity) : SpriteViewerController {
    private val app get() = context.applicationContext as VBHelper
    private val frames = object : LruCache<String, Bitmap>(SpritePreviewPolicy.CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    override suspend fun getPage(page: Int): SpritePreviewPage = withContext(Dispatchers.IO) {
        check(app.container.debugSettingsRepository.enabled)
        val dao = app.container.db.spriteDao()
        val total = dao.countSprites()
        val eligible = dao.countPreviewSprites(SpritePreviewPolicy.MAX_DIMENSION, SpritePreviewPolicy.MAX_FRAME_BYTES)
        val sprites = dao.getPreviewPage(page.coerceAtLeast(0) * SpritePreviewPolicy.PAGE_SIZE,
            SpritePreviewPolicy.PAGE_SIZE, SpritePreviewPolicy.MAX_DIMENSION, SpritePreviewPolicy.MAX_FRAME_BYTES)
        check(app.container.debugSettingsRepository.enabled)
        SpritePreviewPage(sprites, eligible, total)
    }

    override suspend fun decodeFrame(frame: SpritePreviewFrame): Bitmap? = withContext(Dispatchers.Default) {
        if (!app.container.debugSettingsRepository.enabled ||
            !SpritePreviewPolicy.validFrame(frame.width, frame.height, frame.bytes.size)) return@withContext null
        val key = "${frame.spriteId}:${frame.name}:${frame.width}:${frame.height}:${frame.bytes.contentHashCode()}"
        frames.get(key)?.let { return@withContext it }
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.RGB_565)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(frame.bytes))
        if (!app.container.debugSettingsRepository.enabled) return@withContext null
        frames.put(key, bitmap)
        bitmap
    }
}
