package com.github.nacabaro.vbhelper.battle

import com.github.cfogrady.vb.dim.character.CharacterStats
import com.github.cfogrady.vb.dim.sprite.SpriteData
import com.github.nacabaro.vbhelper.domain.card.CardAttackArt
import com.github.nacabaro.vbhelper.utils.BitmapData

internal object ImportedAttackArtReader {
    fun read(
        cardCharacterId: Long,
        stats: CharacterStats.CharacterStatsEntry,
        sprites: SpriteData,
        isBem: Boolean
    ): CardAttackArt {
        // Standard IDs are firmware sprites: small 0..38 and large 0..21.
        // BEM stores ten additional small sprites at 34..43 and large sprites at 44..53.
        val small = if (isBem && stats.smallAttackId in 39..48) sprites.sprites.getOrNull(34 + stats.smallAttackId - 39) else null
        val large = if (isBem && stats.bigAttackId in 22..31) sprites.sprites.getOrNull(44 + stats.bigAttackId - 22) else null
        return CardAttackArt(
            cardCharacterId, stats.smallAttackId, stats.bigAttackId,
            small?.pixelData, small?.width, small?.height,
            large?.pixelData, large?.width, large?.height
        )
    }
}

internal sealed interface ImportedAttackSpriteSource {
    data class Asset(val fileName: String) : ImportedAttackSpriteSource
    data class Pixels(val bitmap: BitmapData) : ImportedAttackSpriteSource
}

/** Imported assignments are authoritative, including missing/invalid sprite IDs. */
internal fun importedAttackSpriteSource(art: CardAttackArt, isLarge: Boolean): ImportedAttackSpriteSource? {
    val id = if (isLarge) art.largeAttackId else art.smallAttackId
    val maxStandardId = if (isLarge) 21 else 38
    if (id in 0..maxStandardId) {
        return ImportedAttackSpriteSource.Asset("atk_${if (isLarge) "l" else "s"}_${id.toString().padStart(2, '0')}")
    }
    val pixels = (if (isLarge) art.largePixels else art.smallPixels) ?: return null
    val width = (if (isLarge) art.largeWidth else art.smallWidth) ?: return null
    val height = (if (isLarge) art.largeHeight else art.smallHeight) ?: return null
    if (width !in 1..256 || height !in 1..256 || pixels.size.toLong() != width.toLong() * height * 2) return null
    return ImportedAttackSpriteSource.Pixels(BitmapData(pixels, width, height))
}
