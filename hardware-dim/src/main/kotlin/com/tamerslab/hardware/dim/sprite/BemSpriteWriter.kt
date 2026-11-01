package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteOffsetOutputStream
import com.tamerslab.hardware.dim.util.RelativeByteOffsetOutputStream
import java.io.IOException
import java.io.UncheckedIOException

class BemSpriteWriter(
    private val spriteWriter: SpriteWriter
) {
    fun writeSpriteDimensions(
        spriteDimensions: List<SpriteData.SpriteDimensions>,
        generalOutputStream: ByteOffsetOutputStream
    ) {
        try {
            val relativeOutputStream = RelativeByteOffsetOutputStream(generalOutputStream)
            for (dimension in spriteDimensions) {
                relativeOutputStream.write16BitInt(dimension.width)
                relativeOutputStream.write16BitInt(dimension.height)
            }
        } catch (ioe: IOException) {
            throw UncheckedIOException(ioe)
        }
    }

    fun writeSpriteDimensions(
        spriteData: SpriteData,
        generalOutputStream: ByteOffsetOutputStream
    ) {
        writeSpriteDimensions(spriteData.spriteDimensions, generalOutputStream)
    }

    fun writeSpritePackage(
        spriteData: SpriteData,
        hasSpriteSigning: Boolean,
        generalOutputStream: ByteOffsetOutputStream
    ) {
        try {
            spriteWriter.writeSpriteData(spriteData, hasSpriteSigning, generalOutputStream)
        } catch (ioe: IOException) {
            throw UncheckedIOException(ioe)
        }
    }
}
