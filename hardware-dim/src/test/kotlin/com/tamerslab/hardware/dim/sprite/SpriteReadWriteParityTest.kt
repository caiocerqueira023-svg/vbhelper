package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.RawChecksumBuilder
import com.tamerslab.hardware.dim.util.RelativeByteOffsetInputStream
import com.tamerslab.hardware.dim.util.RelativeByteOffsetOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SpriteReadWriteParityTest {
    private val calculator = SpriteChecksumAreasCalculator.buildForDIM()
    private val writer = SpriteWriter(
        UnorderedSpriteChecksumHacker(
            calculator,
            SpriteWriter.PIXEL_POINTER_TABLE_START,
            RawChecksumBuilder()
        )
    )
    private val reader = BemSpriteReader(UnorderedSpriteReader(calculator))

    @Test
    fun `unsigned round trip preserves text dimensions and pixels`() {
        val original = syntheticSpriteData()
        val read = read(write(original, false), original.spriteDimensions)

        assertEquals(original.text, read.text)
        assertSpritesEqual(original.sprites, read.sprites)
    }

    @Test
    fun `signed write is deterministic and round trips checksums`() {
        val original = syntheticSpriteData()
        val unsignedRead = read(write(original, false), original.spriteDimensions)
        val signed = write(unsignedRead, true)
        val signedAgain = write(unsignedRead, true)

        assertArrayEquals(signed, signedAgain)

        val signedRead = read(signed, original.spriteDimensions)
        assertEquals(unsignedRead.spriteChecksums, signedRead.spriteChecksums)
        assertSpritesEqual(original.sprites, signedRead.sprites)
    }

    @Test
    fun `sprite finder returns sizes in deterministic fifo order`() {
        val finder = SpriteFinder.createSpriteFinder(
            listOf(
                sprite(2, 2, 1),
                sprite(2, 2, 2),
                sprite(4, 4, 3)
            )
        )

        assertNull(finder.getNextSmallestSize(7))
        assertEquals(0, finder.getNextSmallestSize(8)?.index)
        assertEquals(1, finder.getNextSmallestSize(8)?.index)
        assertEquals(2, finder.getNextLargestSprite().index)
        assertTrue(finder.isEmpty())
    }

    private fun syntheticSpriteData(): SpriteData = SpriteData.builder()
        .text("Synthetic sprite package")
        .sprites(
            listOf(
                sprite(2, 2, 1),
                sprite(3, 1, 2),
                sprite(1, 4, 3),
                sprite(2, 2, 4)
            )
        )
        .build()

    private fun sprite(width: Int, height: Int, seed: Int): SpriteData.Sprite {
        val pixelData = ByteArray(width * height * 2) { index ->
            ((seed * 37 + index * 19) and 0xFF).toByte()
        }
        return SpriteData.Sprite.builder()
            .width(width)
            .height(height)
            .pixelData(pixelData)
            .build()
    }

    private fun write(data: SpriteData, signing: Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        writer.writeSpriteData(
            data,
            signing,
            RelativeByteOffsetOutputStream(output)
        )
        return output.toByteArray()
    }

    private fun read(
        bytes: ByteArray,
        dimensions: List<SpriteData.SpriteDimensions>
    ): SpriteData {
        val input = RelativeByteOffsetInputStream(ByteArrayInputStream(bytes))
        input.readToOffset(SpriteWriter.SPRITE_SECTION_START)
        return reader.getSpriteData(input, dimensions)
    }

    private fun assertSpritesEqual(
        expected: List<SpriteData.Sprite>,
        actual: List<SpriteData.Sprite>
    ) {
        assertEquals(expected.size, actual.size)
        for (index in expected.indices) {
            assertEquals(expected[index].width, actual[index].width)
            assertEquals(expected[index].height, actual[index].height)
            assertArrayEquals(expected[index].pixelData, actual[index].pixelData)
        }
    }
}
