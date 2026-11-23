package com.tamerslab.species

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.tamerslab.battle.BattleAssetPaths
import com.tamerslab.domain.characters.Sprite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * VB (Vital Bracelet) battle sprite frames shipped inside the APK.
 *
 * Unlike the Dot pack these files are not installed into the database: each character is a folder of
 * individually cropped RGBA PNGs keyed by the physical DiM binding (`dim<card>_mon<slot>`), so the
 * canonical catalog's [CanonicalSpeciesDto.cardSlots] is the only bridge from a species the owner has
 * not imported to its shipped VB artwork.
 *
 * Every read touches the asset manager or decodes a PNG, so call on IO. Frame dimensions may differ
 * per frame; consumers must keep each frame's own dimensions (`IndividualSpriteManager`, the battle
 * presentation) instead of forcing a single width/height on the PNG path.
 */
class BundledVbSprites(
    private val context: Context,
    private val catalog: () -> CanonicalSpeciesCatalog,
) {
    private val lock = Any()
    private val framesByAsset = mutableMapOf<String, List<Int>>()
    @Volatile private var assetDirs: Set<String>? = null
    private val bitmaps = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val sprites = object : LruCache<String, Sprite>(SPRITE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Sprite): Int =
            (value.spriteIdle1.size + value.spriteIdle2.size + value.spriteWalk1.size + value.spriteWalk2.size +
                value.spriteRun1.size + value.spriteRun2.size + value.spriteTrain1.size + value.spriteTrain2.size +
                value.spriteHappy.size + value.spriteSleep.size + value.spriteAttack.size + value.spriteDodge.size)
                .coerceAtLeast(1)
    }

    /**
     * Shipped character id that supplies VB artwork for [species], or null when the app ships nothing
     * for it. The canonical card-slot binding decides the candidates; the first one whose frame folder
     * actually exists wins. Call on IO.
     */
    fun assetIdFor(species: CanonicalSpeciesDto?): String? {
        species ?: return null
        return catalog().bundledVbAssetIds(species).firstOrNull { frames(it).isNotEmpty() }
    }

    /** Frame numbers (1..12) shipped for one character, ascending. Empty when the folder is absent. */
    fun frames(assetId: String): List<Int> = synchronized(lock) {
        framesByAsset.getOrPut(assetId) {
            // One parent listing for the whole library; `assets.list` per character is the slow path.
            val directories = assetDirectories()
            if (assetId !in directories) return@getOrPut emptyList()
            val pattern = Regex(Regex.escape("${assetId}_") + "(\\d{2})\\.png")
            context.assets.list("${BattleAssetPaths.CHARACTER_SPRITES}/$assetId").orEmpty()
                .mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
                .filter { it in 1..12 }
                .sorted()
        }
    }

    private fun assetDirectories(): Set<String> = assetDirs ?: context.assets
        .list(BattleAssetPaths.CHARACTER_SPRITES).orEmpty().toSet().also { assetDirs = it }

    /** One decoded frame, cached by character/frame. Call on IO. */
    fun bitmap(assetId: String, frame: Int): Bitmap? {
        val key = "$assetId:$frame"
        bitmaps.get(key)?.let { return it }
        val decoded = runCatching {
            context.assets.open(BattleAssetPaths.characterFrame(assetId, frame)).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull() ?: return null
        bitmaps.put(key, decoded)
        return decoded
    }

    /** Idle portrait frame, preferring the first shipped frame. Call on IO. */
    suspend fun portrait(assetId: String): Bitmap? = withContext(Dispatchers.IO) {
        bitmap(assetId, 1) ?: bitmap(assetId, 2)
    }

    /**
     * A twelve-pose [Sprite] built from [poses], for the byte-based projections that predate the PNG
     * path (world spawns, stored partners). Only the frames a surface renders are decoded, so opening
     * Storage or the radar does not pay twelve PNG decodes per species. A `Sprite` row has one
     * width/height, so every frame is bottom-center aligned on a shared canvas and packed as RGB565
     * with the green transparency key the 16-bit readers expect. Missing poses reuse the idle frame.
     * Call on IO.
     */
    fun sprite(assetId: String, poses: IntArray): Sprite? {
        val key = "$assetId:${poses.joinToString(",")}"
        sprites.get(key)?.let { return it }
        val decoded = poses.distinct().mapNotNull { number -> bitmap(assetId, number)?.let { number to it } }
        if (decoded.isEmpty()) return null
        val width = decoded.maxOf { it.second.width }
        val height = decoded.maxOf { it.second.height }
        val idle = decoded.firstOrNull { it.first == 1 }?.second ?: decoded.first().second
        val posedFrames = decoded.associate { (number, bitmap) -> number to bitmap.toRgb565Frame(width, height) }
        val idleFrame = posedFrames[1] ?: idle.toRgb565Frame(width, height)
        fun pose(number: Int): ByteArray = posedFrames[number] ?: idleFrame
        val sprite = Sprite(spriteId(assetId), pose(1), pose(2), pose(3), pose(4), pose(5), pose(6),
            pose(7), pose(8), pose(9), pose(10), pose(11), pose(12), width, height)
        sprites.put(key, sprite)
        return sprite
    }

    private fun spriteId(assetId: String): Int =
        -2 - ((assetId.hashCode().toLong() and 0x7FFF_FFFFL).toInt() % 1_000_000_000)

    private companion object {
        const val CACHE_BYTES = 8 * 1024 * 1024
        const val SPRITE_CACHE_BYTES = 32 * 1024 * 1024
    }
}

/** Bottom-center aligns this bitmap on a transparent canvas and packs it as little-endian R5G6B5. */
internal fun Bitmap.toRgb565Frame(width: Int, height: Int): ByteArray {
    val pixels = IntArray(width * height)
    val left = (width - this.width) / 2
    val top = height - this.height
    getPixels(pixels, top * width + left, width, 0, 0, this.width, this.height)
    val bytes = ByteArray(pixels.size * 2)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val value = if ((pixel ushr 24) and 0xFF == 0) {
            // Green key: the 16-bit readers treat pure green as transparent.
            0x07E0
        } else {
            (((pixel ushr 19) and 0x1F) shl 11) or
                (((pixel ushr 10) and 0x3F) shl 5) or
                ((pixel ushr 3) and 0x1F)
        }
        bytes[index * 2] = (value and 0xFF).toByte()
        bytes[index * 2 + 1] = (value ushr 8).toByte()
    }
    return bytes
}

/** Frames each byte-based projection actually renders, so first loads decode only what they draw. */
val VB_IDLE_POSES = intArrayOf(1, 2)
val VB_WALK_POSES = intArrayOf(1, 2, 3, 4)
val VB_PARTNER_POSES = intArrayOf(1, 2, 5, 6)

/** Battle poses the shipped VB frames cover, numbered like `Sprite.displayFrame`. */
internal val VB_BATTLE_POSES: Map<String, Int> = mapOf(
    "idle" to 1, "idle2" to 2, "walk" to 3, "walk2" to 4,
    "attack" to 11, "guard" to 12, "defeated" to 10,
)

/** Locale-stable character id for one physical DiM binding; also used by the asset index. */
fun vbCharacterId(card: Int, mon: Int): String = String.format(Locale.ROOT, "dim%03d_mon%02d", card, mon)
