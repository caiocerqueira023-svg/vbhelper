package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteOffsetOutputStream
import com.tamerslab.hardware.dim.util.ByteUtils
import java.io.IOException

class SpriteDimentionsWriter {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun writeSpriteDimensions(
            spriteData: SpriteData,
            outputStreamWithNot: ByteOffsetOutputStream
        ) {
            outputStreamWithNot.writeZerosUntilOffset(0x60000)
            writeSpriteDimensionsAtCurrentLocation(spriteData, outputStreamWithNot)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeSpriteDimensionsAtCurrentLocation(
            spriteData: SpriteData,
            outputStreamWithNot: ByteOffsetOutputStream
        ) {
            for (sprite in spriteData.sprites) {
                outputStreamWithNot.writeBytes(
                    ByteUtils.convert16BitIntToBytes(sprite.width)
                )
                outputStreamWithNot.writeBytes(
                    ByteUtils.convert16BitIntToBytes(sprite.height)
                )
            }
        }
    }
}
