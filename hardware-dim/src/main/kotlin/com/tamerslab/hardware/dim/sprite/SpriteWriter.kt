package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteOffsetOutputStream
import com.tamerslab.hardware.dim.util.ByteUtils
import com.tamerslab.hardware.dim.util.RelativeByteOffsetOutputStream
import java.io.IOException
import java.nio.charset.Charset

class SpriteWriter(
    private val unorderedChecksumHacker: UnorderedSpriteChecksumHacker
) {
    @Throws(IOException::class)
    fun writeSpriteData(
        spriteData: SpriteData,
        hasSpriteSigning: Boolean,
        outputStreamWithNot: ByteOffsetOutputStream
    ) {
        outputStreamWithNot.writeZerosUntilOffset(SPRITE_SECTION_START)
        if (hasSpriteSigning) {
            writeSpriteDataToMatchChecksum(outputStreamWithNot, spriteData)
        } else {
            writeUnmodified(spriteData, outputStreamWithNot)
        }
    }

    @Throws(IOException::class)
    fun writeSpriteDataToMatchChecksum(
        outputStreamWithNot: ByteOffsetOutputStream,
        spriteData: SpriteData
    ) {
        unorderedChecksumHacker.writeSpritesUnorderedToCorrectChecksums(
            spriteData,
            outputStreamWithNot
        )
    }

    @Throws(IOException::class)
    fun writeUnmodified(
        spriteData: SpriteData,
        outputStream: ByteOffsetOutputStream
    ) {
        val relativeOutputStream = RelativeByteOffsetOutputStream(outputStream)
        relativeOutputStream.writeBytes(spriteData.text.toByteArray(Charset.defaultCharset()))
        relativeOutputStream.writeZerosUntilOffset(TERMINATION_BYTES_OF_POINTER_TABLE)
        val firstSpritePointer = PIXEL_POINTER_TABLE_START + spriteData.sprites.size * 4 + 4
        val terminationBytesPointer =
            calculateTerminationBytesLocation(firstSpritePointer, spriteData.sprites)
        relativeOutputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(terminationBytesPointer)
        )
        relativeOutputStream.writeZerosUntilOffset(TABLE_START)
        relativeOutputStream.writeBytes(ByteUtils.convert32BitIntToBytes(1))
        relativeOutputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(NUMBER_OF_SPRITES_LOCATION)
        )
        relativeOutputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(spriteData.sprites.size)
        )
        var currentSpritePointer = firstSpritePointer
        for (sprite in spriteData.sprites) {
            relativeOutputStream.writeBytes(
                ByteUtils.convert32BitIntToBytes(currentSpritePointer)
            )
            currentSpritePointer += sprite.width * sprite.height * 2
        }
        relativeOutputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(currentSpritePointer)
        )
        for (sprite in spriteData.sprites) {
            relativeOutputStream.writeBytes(sprite.pixelData)
        }
        relativeOutputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(TERMINATION_BYTES)
        )
    }

    companion object {
        const val SPRITE_SECTION_START = 0x100_000
        const val TABLE_START = 0x40
        const val PIXEL_POINTER_TABLE_START = 0x4C
        const val NUMBER_OF_SPRITES_LOCATION = 0x48
        const val TERMINATION_BYTES_OF_POINTER_TABLE = 0x18
        const val TERMINATION_BYTES = -254

        private fun calculateTerminationBytesLocation(
            firstSpritePointer: Int,
            sprites: List<SpriteData.Sprite>
        ): Int {
            var offset = firstSpritePointer
            for (sprite in sprites) {
                offset += sprite.width * sprite.height * 2
            }
            return offset
        }
    }
}
