package com.tamerslab.species.ui.spriteviewer

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.ComponentActivity
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.ui.SpritePreviewPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class SpriteViewerControllerImpl(private val context: ComponentActivity) : SpriteViewerController {
    private val app get() = context.applicationContext as TamersLabApplication
    private val frames = object : LruCache<String, Bitmap>(SpritePreviewPolicy.CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    override suspend fun getPage(page: Int): SpritePreviewPage = withContext(Dispatchers.IO) {
        check(com.tamerslab.di.AppServices.debugSettingsRepository.enabled)
        val sprites = com.tamerslab.di.AppServices.spriteRepository
        val total = sprites.count()
        val eligible = sprites.countPreview(SpritePreviewPolicy.MAX_DIMENSION, SpritePreviewPolicy.MAX_FRAME_BYTES)
        val pageFrames = sprites.previewPage(page.coerceAtLeast(0) * SpritePreviewPolicy.PAGE_SIZE,
            SpritePreviewPolicy.PAGE_SIZE, SpritePreviewPolicy.MAX_DIMENSION, SpritePreviewPolicy.MAX_FRAME_BYTES)
        check(com.tamerslab.di.AppServices.debugSettingsRepository.enabled)
        SpritePreviewPage(pageFrames, eligible, total)
    }

    override suspend fun decodeFrame(frame: SpritePreviewFrame): Bitmap? = withContext(Dispatchers.Default) {
        if (!com.tamerslab.di.AppServices.debugSettingsRepository.enabled ||
            !SpritePreviewPolicy.validFrame(frame.width, frame.height, frame.bytes.size)) return@withContext null
        val key = "${frame.spriteId}:${frame.name}:${frame.width}:${frame.height}:${frame.bytes.contentHashCode()}"
        frames.get(key)?.let { return@withContext it }
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.RGB_565)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(frame.bytes))
        if (!com.tamerslab.di.AppServices.debugSettingsRepository.enabled) return@withContext null
        frames.put(key, bitmap)
        bitmap
    }
}

