package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteOffsetInputStream
import com.tamerslab.hardware.dim.util.ByteUtils
import com.tamerslab.hardware.dim.util.RelativeByteOffsetInputStream
import java.io.IOException
import java.io.UncheckedIOException

class BemSpriteReader(
    private val unorderedSpriteReader: UnorderedSpriteReader
) {
    constructor() : this(
        UnorderedSpriteReader(SpriteChecksumAreasCalculator.buildForBEM())
    )

    fun readSpriteDimensions(
        generalInputStream: ByteOffsetInputStream
    ): List<SpriteData.SpriteDimensions> {
        try {
            val relativeInputStream = RelativeByteOffsetInputStream(generalInputStream)
            val dimensions = ArrayList<SpriteData.SpriteDimensions>()
            var values = getDimensionRowValues(relativeInputStream)
            var validRow = noZeros(values)
            while (validRow) {
                dimensions.add(
                    SpriteData.SpriteDimensions.builder()
                        .width(values[0])
                        .height(values[1])
                        .build()
                )
                values = getDimensionRowValues(relativeInputStream)
                validRow = noZeros(values)
            }
            return dimensions
        } catch (ioe: IOException) {
            throw UncheckedIOException(ioe)
        }
    }

    fun getSpriteData(
        generalInputStream: ByteOffsetInputStream,
        spriteDimensions: List<SpriteData.SpriteDimensions>
    ): SpriteData {
        try {
            return unorderedSpriteReader.spriteDataFromDimensionsAndStream(
                spriteDimensions,
                generalInputStream
            )
        } catch (ioe: IOException) {
            throw UncheckedIOException(ioe)
        }
    }

    @Throws(IOException::class)
    private fun getDimensionRowValues(
        relativeInputStream: RelativeByteOffsetInputStream
    ): IntArray {
        val rowBytes = relativeInputStream.readNBytes(2 * 2)
        return ByteUtils.getUnsigned16Bit(rowBytes)
    }

    private fun noZeros(values: IntArray): Boolean {
        for (value in values) {
            if (value == 0) {
                return false
            }
        }
        return true
    }
}
