package com.tamerslab.species.ui.spriteviewer

import android.graphics.Bitmap
import com.tamerslab.domain.characters.Sprite

data class SpritePreviewPage(val sprites: List<Sprite>, val eligibleCount: Int, val totalCount: Int)
data class SpritePreviewFrame(val spriteId: Int, val name: String, val width: Int, val height: Int, val bytes: ByteArray)

interface SpriteViewerController {
    suspend fun getPage(page: Int): SpritePreviewPage
    suspend fun decodeFrame(frame: SpritePreviewFrame): Bitmap?
}
