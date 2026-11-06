package com.tamerslab.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tamerslab.hardware.dim.sprite.SpriteData

// simple, but smooth
data class BitmapData (
    val bitmap: ByteArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BitmapData

        if (!bitmap.contentEquals(other.bitmap)) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bitmap.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

data class ImageBitmapData(
    val imageBitmap: ImageBitmap,
    val dpWidth: Dp,
    val dpHeight: Dp
)

fun BitmapData.getImageBitmap(
    context: Context,
    multiplier: Int,
    obscure: Boolean
): ImageBitmapData {
    val density: Float = context.resources.displayMetrics.density

    val imageBitmap: ImageBitmap = if (obscure) {
        this.getObscuredBitmap().asImageBitmap()
    } else {
        this.getBitmap().asImageBitmap()
    }

    return ImageBitmapData(
        imageBitmap = imageBitmap,
        dpWidth = (this.width  * multiplier / density).dp,
        dpHeight = (this.height * multiplier / density).dp
    )
}

/**
 * Decoded-sprite cache.
 *
 * A grid rebuilds every visible sprite when it comes back into composition, and again every time an
 * idle frame swaps the artwork: the `remember` key in the entry changes with the frame, so the decode
 * is repeated. Both happen on the main thread, frequently in the frames a route transition is using,
 * and one decode is a per-pixel RGB loop plus a hardware bitmap allocation. The source bytes are
 * immutable and the produced bitmap is never written to, so it is safe to share one instance.
 */
private val decodedSprites = object : LruCache<SpriteBitmapKey, Bitmap>(DECODED_SPRITE_CACHE_BYTES) {
    // Estimated from the dimensions on purpose: a hardware bitmap does not always report a real
    // allocation size, and an under-counted entry would let the cache grow past its budget.
    override fun sizeOf(key: SpriteBitmapKey, value: Bitmap): Int =
        (value.width * value.height * 4).coerceAtLeast(1)
}

private const val DECODED_SPRITE_CACHE_BYTES = 16 * 1024 * 1024

private data class SpriteBitmapKey(
    val pixels: Long,
    val width: Int,
    val height: Int,
    val obscure: Boolean,
)

/**
 * 64-bit FNV-1a over the raw pixels. Content hashing costs a few microseconds per sprite, which is
 * two orders of magnitude below the decode it replaces, and 64 bits keeps the collision odds far
 * below the point where a wrong sprite could be served from the cache.
 */
private fun ByteArray.spriteContentHash(): Long {
    var hash = -3750763034362895579L
    for (byte in this) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= 1099511628211L
    }
    return hash
}

private fun BitmapData.decodedBitmap(obscure: Boolean): Bitmap {
    val key = SpriteBitmapKey(
        pixels = bitmap.spriteContentHash(),
        width = width,
        height = height,
        obscure = obscure,
    )
    decodedSprites.get(key)?.let { return it }

    val argbPixels = createARGBIntArray()
    if (obscure) {
        for (index in argbPixels.indices) {
            if (argbPixels[index] and ARGBMasks.ALPHA != 0) argbPixels[index] = BLACK
        }
    }
    val decoded = Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.HARDWARE)
    decodedSprites.put(key, decoded)
    return decoded
}

fun BitmapData.getBitmap(): Bitmap = decodedBitmap(obscure = false)

object ARGBMasks {
    const val ALPHA = (0xFF shl 24)
    const val RED = (0xFF shl 16)
    const val GREEN = (0xFF shl 8)
    const val BLUE = 0xFF

}

const val BLACK = ARGBMasks.ALPHA

fun BitmapData.getObscuredBitmap(): Bitmap = decodedBitmap(obscure = true)



fun BitmapData.createARGBIntArray(): IntArray {
    // hack to get it into correct format by relying on the DIM Sprites methods since we haven't changed the raw pixel data at this point.
    val bytes = SpriteData.Sprite.builder().width(this.width).height(this.height).pixelData(this.bitmap).build()
        .get24BitRGB()
    val result = IntArray(this.width*this.height)
    for(i in result.indices) {
        val originalIndex = i*3
        val red = bytes[originalIndex].toUInt() and 0xFFu
        val green = bytes[originalIndex+1].toUInt() and 0xFFu
        val blue = bytes[originalIndex+2].toUInt() and 0xFFu
        val alpha = if(red == 0u && blue == 0u && green == 0xFFu) 0 else 0xFF
        result[i] = (alpha shl 24) or (red shl 16).toInt() or (green shl 8).toInt() or blue.toInt()
    }
    return result
}

fun List<BitmapData>.getBitmaps(): List<Bitmap> {
    return this.map{it.getBitmap()}
}