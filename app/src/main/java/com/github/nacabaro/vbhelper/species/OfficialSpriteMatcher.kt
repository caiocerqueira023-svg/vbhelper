package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.domain.characters.Sprite
import java.security.MessageDigest
import java.util.Base64

data class OfficialSpriteIdentity(val cardNumber: Int, val charaIndex: Int)

/** Native little-endian RGB565 pixels, including the DIM's green transparency key. */
class SpriteMatchFrame(val width: Int, val height: Int, val pixels: ByteArray) {
    internal fun isValid(): Boolean = width > 0 && height > 0 &&
        width.toLong() * height * 2 == pixels.size.toLong()

    internal fun hasIdentifyingPixels(): Boolean = pixels.indices.step(2).any {
        val color = colorAt(it / 2)
        color != 0x07e0 && color != 0 // Entirely black catalog placeholders are not species art.
    }

    private fun colorAt(index: Int): Int = (pixels[index * 2].toInt() and 0xff) or
        ((pixels[index * 2 + 1].toInt() and 0xff) shl 8)

    /** Unity PNGs are trimmed per pose; only transparent canvas margins are discarded. */
    internal fun withoutTransparentMargins(): SpriteMatchFrame? {
        if (!isValid()) return null
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (colorAt(y * width + x) != 0x07e0) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return SpriteMatchFrame(0, 0, byteArrayOf())
        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) return this
        val trimmedWidth = right - left + 1
        val trimmedHeight = bottom - top + 1
        val trimmed = ByteArray(trimmedWidth * trimmedHeight * 2)
        for (row in 0 until trimmedHeight) {
            val start = ((top + row) * width + left) * 2
            pixels.copyInto(trimmed, row * trimmedWidth * 2, start, start + trimmedWidth * 2)
        }
        return SpriteMatchFrame(trimmedWidth, trimmedHeight, trimmed)
    }

    companion object {
        /** PNG export rounding is normalized to the original DIM's 5/6/5-bit precision. */
        fun fromArgb(width: Int, height: Int, argb: IntArray): SpriteMatchFrame? {
            if (width <= 0 || height <= 0 || width.toLong() * height != argb.size.toLong() ||
                argb.size > Int.MAX_VALUE / 2) return null
            val bytes = ByteArray(argb.size * 2)
            for ((index, color) in argb.withIndex()) {
                val alpha = color ushr 24
                if (alpha != 0 && alpha != 255) return null // DIMs cannot represent partial alpha.
                val rgb565 = if (alpha == 0) 0x07e0 else
                    ((color ushr 19 and 31) shl 11) or ((color ushr 10 and 63) shl 5) or (color ushr 3 and 31)
                bytes[index * 2] = rgb565.toByte()
                bytes[index * 2 + 1] = (rgb565 ushr 8).toByte()
            }
            return SpriteMatchFrame(width, height, bytes)
        }
    }
}

/**
 * Uses bundled sprite folders, never imported official cards. The first-frame index is
 * small and built once; a silhouette digest selects candidates, then every native pose
 * must share the exact trimmed shape and transparency mask, with per-pixel colors within
 * toolchain tolerance. The tolerance exists because visually identical art can carry
 * slightly shifted RGB565 values depending on which DIM/PNG encoder produced it, while
 * true recolors and different species differ by far more. Standard DIM baby slots have
 * five native poses repeated across 12 animation slots; other slots and BEMs have 12 poses.
 */
class OfficialSpriteMatcher(
    private val characterIds: () -> List<String>,
    private val loadFrame: (String, Int) -> SpriteMatchFrame?
) {
    companion object {
        /** Maximum per-pixel RGB888 Euclidean distance; conversion rounding stays far below this. */
        internal const val MAX_PIXEL_DISTANCE = 32.0
        internal val MAX_PIXEL_DISTANCE_SQUARED = MAX_PIXEL_DISTANCE * MAX_PIXEL_DISTANCE
    }

    private data class Candidate(
        val assetId: String,
        val identity: OfficialSpriteIdentity,
        val firstFrame: TrimmedArt
    )

    private val firstFrameIndex by lazy {
        val folderPattern = Regex("dim(\\d+)_mon(\\d+)")
        val index = mutableMapOf<String, MutableList<Candidate>>()
        for (id in characterIds().distinct().sorted()) {
            val match = folderPattern.matchEntire(id) ?: continue
            val cardNumber = match.groupValues[1].toIntOrNull() ?: continue
            val slot = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: continue
            val first = loadFrame(id, 1)?.withoutTransparentMargins()?.toTrimmedArt()
                ?.takeIf { it.hasIdentifyingPixels() } ?: continue
            index.getOrPut(first.maskKey()) { mutableListOf() }
                .add(Candidate(id, OfficialSpriteIdentity(cardNumber, slot - 1), first))
        }
        index
    }

    fun findMatches(sprite: Sprite, compactDim: Boolean = false): List<OfficialSpriteIdentity> {
        val frames = listOf(sprite.spriteIdle1, sprite.spriteIdle2, sprite.spriteWalk1, sprite.spriteWalk2,
            sprite.spriteRun1, sprite.spriteRun2, sprite.spriteTrain1, sprite.spriteTrain2,
            sprite.spriteHappy, sprite.spriteSleep, sprite.spriteAttack, sprite.spriteDodge)
            .map {
                SpriteMatchFrame(sprite.width, sprite.height, it).withoutTransparentMargins()?.toTrimmedArt()
                    ?: return emptyList()
            }
        if (!frames.first().hasIdentifyingPixels()) return emptyList()
        if (compactDim) {
            // These repetitions are created by CardImportController from five raw images.
            // Reject inconsistent stored repetitions rather than ignoring edited pixels.
            for (group in listOf(listOf(0, 2, 4, 6), listOf(1, 10), listOf(3, 5, 7, 11))) {
                if (group.drop(1).any { !frames[group.first()].hasIdenticalPixels(frames[it]) }) return emptyList()
            }
        }
        val nativeFrames = if (compactDim) listOf(0, 1, 3, 8, 9) else frames.indices.toList()
        return firstFrameIndex[frames.first().maskKey()].orEmpty().filter { candidate ->
            nativeFrames.all { index ->
                val frame = frames[index]
                val official = loadFrame(candidate.assetId, index + 1)?.withoutTransparentMargins()?.toTrimmedArt()
                official != null && frame.matchesWithinTolerance(official)
            }
        }.map { it.identity }
    }
}

/**
 * Trimmed sprite art: exact dimensions, an exact transparency silhouette, and native
 * RGB565 colors. Shape comparisons are exact; color comparisons allow toolchain tolerance.
 */
internal class TrimmedArt(
    val width: Int,
    val height: Int,
    private val mask: ByteArray,
    private val colors: IntArray
) {
    fun maskKey(): String =
        "${width}x${height}:" + Base64.getEncoder()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(mask))

    fun hasIdentifyingPixels(): Boolean = colors.any { it != 0x07e0 && it != 0 }

    fun hasIdenticalPixels(other: TrimmedArt): Boolean =
        width == other.width && height == other.height &&
            mask.contentEquals(other.mask) && colors.contentEquals(other.colors)

    /** Exact silhouette, per-pixel colors within [OfficialSpriteMatcher.MAX_PIXEL_DISTANCE]. */
    fun matchesWithinTolerance(other: TrimmedArt): Boolean {
        if (width != other.width || height != other.height) return false
        if (!mask.contentEquals(other.mask)) return false
        for (i in colors.indices) {
            if (pixelDistanceSquared(colors[i], other.colors[i]) > OfficialSpriteMatcher.MAX_PIXEL_DISTANCE_SQUARED) {
                return false
            }
        }
        return true
    }

    private fun pixelDistanceSquared(first: Int, second: Int): Double {
        if (first == second) return 0.0
        // Transparent pixels always share the mask, so only opaque colors reach here.
        val r = ((first ushr 11) and 31) * 255 / 31 - ((second ushr 11) and 31) * 255 / 31
        val g = ((first ushr 5) and 63) * 255 / 63 - ((second ushr 5) and 63) * 255 / 63
        val b = (first and 31) * 255 / 31 - (second and 31) * 255 / 31
        return (r * r + g * g + b * b).toDouble()
    }
}

internal fun SpriteMatchFrame.toTrimmedArt(): TrimmedArt? {
    if (!isValid()) return null
    val colors = IntArray(width * height) { (pixels[it * 2].toInt() and 0xff) or
        ((pixels[it * 2 + 1].toInt() and 0xff) shl 8) }
    val mask = ByteArray(colors.size) { if (colors[it] == 0x07e0) 0 else 1 }
    return TrimmedArt(width, height, mask, colors)
}
