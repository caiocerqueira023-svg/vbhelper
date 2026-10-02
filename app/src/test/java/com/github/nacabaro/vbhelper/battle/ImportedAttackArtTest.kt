package com.github.nacabaro.vbhelper.battle

import com.github.cfogrady.vb.dim.character.DimStatsReader
import com.github.cfogrady.vb.dim.sprite.SpriteData
import com.github.nacabaro.vbhelper.domain.card.CardAttackArt
import com.github.nacabaro.vbhelper.utils.BitmapData
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImportedAttackArtTest {
    private fun stats(small: Int, large: Int) = DimStatsReader.dimStatsFromBytes(
        ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
            listOf(2, 0, 1, 0, small, large, 1, 10, 3, 2, 50, 50).forEach { putShort(it.toShort()) }
        }.array()
    ).characterEntries.single()

    private fun sprites() = SpriteData.builder().sprites((0..53).map { index ->
        SpriteData.Sprite.builder().width(1).height(1).pixelData(byteArrayOf(index.toByte(), 0)).build()
    }).build()

    @Test fun customDimKeepsAttackAssignmentsFromItsOwnStats() {
        val art = ImportedAttackArtReader.read(91L, stats(7, 8), sprites(), isBem = false)
        assertEquals(7, art.smallAttackId)
        assertEquals(8, art.largeAttackId)
        assertEquals(ImportedAttackSpriteSource.Asset("atk_s_07"), importedAttackSpriteSource(art, false))
        assertEquals(ImportedAttackSpriteSource.Asset("atk_l_08"), importedAttackSpriteSource(art, true))
    }

    @Test fun zeroIsAValidStandardAttackId() {
        val art = ImportedAttackArtReader.read(1L, stats(0, 0), sprites(), isBem = false)
        assertEquals(ImportedAttackSpriteSource.Asset("atk_s_00"), importedAttackSpriteSource(art, false))
        assertEquals(ImportedAttackSpriteSource.Asset("atk_l_00"), importedAttackSpriteSource(art, true))
    }

    @Test fun bemSpecificAttacksUseTheActualEmbeddedPixelsIncludingBoundaryIds() {
        for ((small, large, smallIndex, largeIndex) in listOf(listOf(39, 22, 34, 44), listOf(48, 31, 43, 53))) {
            val art = ImportedAttackArtReader.read(1L, stats(small, large), sprites(), isBem = true)
            assertEquals(ImportedAttackSpriteSource.Pixels(BitmapData(byteArrayOf(smallIndex.toByte(), 0), 1, 1)),
                importedAttackSpriteSource(art, false))
            assertEquals(ImportedAttackSpriteSource.Pixels(BitmapData(byteArrayOf(largeIndex.toByte(), 0), 1, 1)),
                importedAttackSpriteSource(art, true))
        }
    }

    @Test fun missingOrInvalidImportedArtDoesNotInventAnOfficialOrGenericAttack() {
        for (isBem in listOf(false, true)) {
            val art = ImportedAttackArtReader.read(1L, stats(65535, 65535), sprites(), isBem)
            assertNull(importedAttackSpriteSource(art, false))
            assertNull(importedAttackSpriteSource(art, true))
        }
        val malformed = CardAttackArt(1L, 39, 22, byteArrayOf(1), 2, 2)
        assertNull(importedAttackSpriteSource(malformed, false))
        assertNull(importedAttackSpriteSource(malformed, true))
        val dim = ImportedAttackArtReader.read(1L, stats(39, 22), sprites(), isBem = false)
        assertNull(importedAttackSpriteSource(dim, false))
        assertNull(importedAttackSpriteSource(dim, true))
    }

    @Test fun embeddedArtIsComparedByPixelContentAndKeepsLocalSpeciesIdentity() {
        val first = ImportedAttackArtReader.read(91L, stats(39, 22), sprites(), isBem = true)
        val same = ImportedAttackArtReader.read(91L, stats(39, 22), sprites(), isBem = true)
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(first, same.copy(cardCharacterId = 92L))
        assertNotEquals(first, same.copy(smallPixels = byteArrayOf(0, 0)))
        assertNotEquals(first, same.copy(largeAttackId = 23))
    }
}
