package com.tamerslab.hardware.dim.sprite

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpriteDataRgbTest {
    @Test
    fun `converts r5g6b5 pixels to rgb and bgra`() {
        val sprite = SpriteData.Sprite.builder()
            .width(4)
            .height(1)
            .pixelData(
                byteArrayOf(
                    0xFF.toByte(), 0xFF.toByte(),
                    0x00, 0x08,
                    0x01, 0x00,
                    0x20, 0x00
                )
            )
            .build()

        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                8, 0, 0,
                0, 0, 8,
                0, 4, 0
            ),
            sprite.get24BitRGB()
        )
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0, 0, 8, 0xFF.toByte(),
                8, 0, 0, 0xFF.toByte(),
                0, 4, 0, 0xFF.toByte()
            ),
            sprite.getBGRA()
        )
    }

    @Test
    fun `green transparency key becomes transparent bgra`() {
        val sprite = SpriteData.Sprite.builder()
            .width(1)
            .height(1)
            .pixelData(byteArrayOf(0xE0.toByte(), 0x07))
            .build()

        assertArrayEquals(byteArrayOf(0, 0xFF.toByte(), 0), sprite.get24BitRGB())
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), sprite.getBGRA())
    }

    @Test
    fun `builders preserve values and derived dimensions`() {
        val sprite = SpriteData.Sprite.builder()
            .width(2)
            .height(2)
            .pixelData(ByteArray(8) { it.toByte() })
            .build()

        assertEquals(8, sprite.getByteCountAt16BitPerPixel())
        assertEquals(2, sprite.spriteDimensions.width)
        assertEquals(2, sprite.spriteDimensions.height)

        val changed = sprite.toBuilder().width(3).build()
        assertEquals(3, changed.width)
        assertEquals(2, changed.height)
        assertArrayEquals(sprite.pixelData, changed.pixelData)

        val data = SpriteData.builder()
            .text("synthetic")
            .sprites(listOf(sprite))
            .build()
        val rebuilt = data.toBuilder().text("updated").build()
        assertEquals("updated", rebuilt.text)
        assertEquals(listOf(sprite), rebuilt.sprites)
        assertNull(rebuilt.spriteChecksums)
        assertEquals(1, rebuilt.spriteDimensions.size)
        assertEquals(2, rebuilt.spriteDimensions[0].width)
        assertEquals(2, rebuilt.spriteDimensions[0].height)
    }
}
