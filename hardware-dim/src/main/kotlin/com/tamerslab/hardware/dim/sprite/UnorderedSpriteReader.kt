package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteOffsetInputStream
import com.tamerslab.hardware.dim.util.ByteUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.Charset

class UnorderedSpriteReader(
    private val spriteChecksumAreasCalculator: SpriteChecksumAreasCalculator
) {
    data class PointerWithSpriteIndex(
        val index: Int,
        val pointer: Int
    )

    @Throws(IOException::class)
    fun spriteDataFromDimensionsAndStream(
        spriteDimensions: List<SpriteData.SpriteDimensions>,
        generalInputStream: ByteOffsetInputStream
    ): SpriteData {
        val spriteChecksumBuilder = SpriteChecksumBuilder(spriteChecksumAreasCalculator)
        val spriteDataSection = SpritePackageInputStream(
            generalInputStream,
            spriteChecksumBuilder
        )
        val text = String(
            spriteDataSection.readNBytes(0x18),
            Charset.defaultCharset()
        )
        val finalOffset = ByteUtils.getIntsFromBytes(spriteDataSection.readNBytes(4))[0]
        spriteDataSection.readToOffset(SpriteWriter.NUMBER_OF_SPRITES_LOCATION)
        val numberOfSprites =
            ByteUtils.getIntsFromBytes(spriteDataSection.readNBytes(4))[0]
        val pointers = ByteUtils.getIntsFromBytes(
            spriteDataSection.readNBytes(numberOfSprites * 4)
        )
        val endSignalLocation =
            ByteUtils.getIntsFromBytes(spriteDataSection.readNBytes(4))[0]
        val pointersWithSpriteIndices = getPointersWithSpriteIndices(pointers)
        if (finalOffset != endSignalLocation) {
            log.warn(
                "End signal pointer {} at 0x100018 doesn't match the pointer at the end of the pointer table {}.",
                finalOffset,
                endSignalLocation
            )
        }
        pointersWithSpriteIndices.sortWith(compareBy { it.pointer })
        val spritesWithIndices = ArrayList<SpriteWithIndex>(numberOfSprites)
        for (i in pointersWithSpriteIndices.indices) {
            val pointerWithSpriteIndex = pointersWithSpriteIndices[i]
            val offset = pointerWithSpriteIndex.pointer
            spriteDataSection.readToOffset(offset)
            val dimensions = spriteDimensions[pointerWithSpriteIndex.index]
            val width = dimensions.width
            val height = dimensions.height
            val expectedSize = width * height * 2
            val sprite = if (
                wouldOverlapWithNextSprite(i, pointersWithSpriteIndices, expectedSize)
            ) {
                log.warn(
                    "Sprite at index {} has dimensions that would overlap with the next sprite! This is not a valid sprite replacing with a blank 1x1.",
                    i
                )
                SpriteData.Sprite.builder()
                    .width(1)
                    .height(1)
                    .pixelData(ONE_BY_ONE)
                    .build()
            } else {
                SpriteData.Sprite.builder()
                    .width(width)
                    .height(height)
                    .pixelData(spriteDataSection.readNBytes(expectedSize))
                    .build()
            }
            spritesWithIndices.add(
                SpriteWithIndex(sprite, pointerWithSpriteIndex.index)
            )
        }
        spriteDataSection.readToOffset(endSignalLocation + 4)
        spritesWithIndices.sortWith(compareBy { it.index })
        val sprites = ArrayList<SpriteData.Sprite>(spritesWithIndices.size)
        for (spriteWithIndex in spritesWithIndices) {
            sprites.add(spriteWithIndex.sprite)
        }
        return SpriteData.builder()
            .sprites(sprites)
            .text(text)
            .spriteChecksums(spriteDataSection.getSpriteChecksums())
            .build()
    }

    private fun getPointersWithSpriteIndices(
        pointers: IntArray
    ): MutableList<PointerWithSpriteIndex> {
        val pointersWithSpriteIndices = ArrayList<PointerWithSpriteIndex>(pointers.size)
        for (i in pointers.indices) {
            pointersWithSpriteIndices.add(PointerWithSpriteIndex(i, pointers[i]))
        }
        return pointersWithSpriteIndices
    }

    private fun wouldOverlapWithNextSprite(
        index: Int,
        pointersWithSpriteIndices: List<PointerWithSpriteIndex>,
        expectedSize: Int
    ): Boolean {
        if (index + 1 < pointersWithSpriteIndices.size) {
            val spritePointer = pointersWithSpriteIndices[index].pointer
            val nextSpritePointer = pointersWithSpriteIndices[index + 1].pointer
            return spritePointer + expectedSize > nextSpritePointer
        }
        return false
    }

    companion object {
        private val ONE_BY_ONE = byteArrayOf(0x0, 0x0)
        private val log = LoggerFactory.getLogger(UnorderedSpriteReader::class.java)
    }
}
