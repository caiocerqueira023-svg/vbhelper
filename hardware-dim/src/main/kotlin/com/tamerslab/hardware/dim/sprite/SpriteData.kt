package com.tamerslab.hardware.dim.sprite

import java.util.Arrays

class SpriteData(
    val sprites: List<Sprite>,
    val text: String,
    val spriteChecksums: List<Int>?
) {
    val spriteDimensions: List<SpriteDimensions>
        get() {
            val dimensions = ArrayList<SpriteDimensions>(sprites.size)
            for (sprite in sprites) {
                dimensions.add(
                    SpriteDimensions.builder()
                        .width(sprite.width)
                        .height(sprite.height)
                        .build()
                )
            }
            return dimensions
        }

    fun toBuilder(): SpriteDataBuilder = SpriteDataBuilder()
        .sprites(sprites)
        .text(text)
        .spriteChecksums(spriteChecksums)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is SpriteData) {
            return false
        }
        return sprites == other.sprites &&
            text == other.text &&
            spriteChecksums == other.spriteChecksums
    }

    override fun hashCode(): Int {
        var result = 1
        result = 59 * result + sprites.hashCode()
        result = 59 * result + text.hashCode()
        result = 59 * result + (spriteChecksums?.hashCode() ?: 43)
        return result
    }

    override fun toString(): String =
        "SpriteData(sprites=$sprites, text=$text, spriteChecksums=$spriteChecksums)"

    class SpriteDataBuilder {
        private var sprites: List<Sprite>? = null
        private var text: String? = null
        private var spriteChecksums: List<Int>? = null

        fun sprites(sprites: List<Sprite>): SpriteDataBuilder =
            apply { this.sprites = sprites }

        fun text(text: String): SpriteDataBuilder =
            apply { this.text = text }

        fun spriteChecksums(spriteChecksums: List<Int>?): SpriteDataBuilder =
            apply { this.spriteChecksums = spriteChecksums }

        fun build(): SpriteData = SpriteData(
            sprites ?: emptyList(),
            text ?: "",
            spriteChecksums
        )
    }

    companion object {
        @JvmStatic
        fun builder(): SpriteDataBuilder = SpriteDataBuilder()
    }

    class Sprite(
        val width: Int,
        val height: Int,
        val pixelData: ByteArray
    ) {
        fun getByteCountAt16BitPerPixel(): Int = pixelData.size

        fun get24BitRGB(): ByteArray {
            val rgb = ByteArray((pixelData.size / 2) * 3)
            for (pixel in 0 until width * height) {
                val byte0 = pixelData[pixel * 2 + 1].toInt() and 0xFF
                val byte1 = pixelData[pixel * 2].toInt() and 0xFF
                val red = (byte0 and 0b11111000) shr 3
                val blue = byte1 and 0b00011111
                val green = ((byte0 and 0b00000111) shl 3) or
                    ((byte1 and 0b11100000) shr 5)
                val scaledRed = scaleTo24BitColor(red, 5)
                val scaledGreen = scaleTo24BitColor(green, 6)
                val scaledBlue = scaleTo24BitColor(blue, 5)
                rgb[pixel * 3] = scaledRed.toByte()
                rgb[pixel * 3 + 1] = scaledGreen.toByte()
                rgb[pixel * 3 + 2] = scaledBlue.toByte()
            }
            return rgb
        }

        fun getBGRA(): ByteArray {
            val bgra = ByteArray((pixelData.size / 2) * 4)
            for (pixel in 0 until width * height) {
                val byte0 = pixelData[pixel * 2 + 1].toInt() and 0xFF
                val byte1 = pixelData[pixel * 2].toInt() and 0xFF
                var red = (byte0 and 0b11111000) shr 3
                var blue = byte1 and 0b00011111
                var green = ((byte0 and 0b00000111) shl 3) or
                    ((byte1 and 0b11100000) shr 5)
                red = scaleTo24BitColor(red, 5)
                green = scaleTo24BitColor(green, 6)
                blue = scaleTo24BitColor(blue, 5)
                var alpha = 255
                if (red == 0 && blue == 0 && green == 255) {
                    alpha = 0
                    green = 0
                }
                bgra[pixel * 4] = blue.toByte()
                bgra[pixel * 4 + 1] = green.toByte()
                bgra[pixel * 4 + 2] = red.toByte()
                bgra[pixel * 4 + 3] = alpha.toByte()
            }
            return bgra
        }

        val spriteDimensions: SpriteDimensions
            get() = SpriteDimensions.builder()
                .width(width)
                .height(height)
                .build()

        fun toBuilder(): SpriteBuilder = SpriteBuilder()
            .width(width)
            .height(height)
            .pixelData(pixelData)

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is Sprite) {
                return false
            }
            return width == other.width &&
                height == other.height &&
                pixelData.contentEquals(other.pixelData)
        }

        override fun hashCode(): Int {
            var result = 1
            result = 59 * result + width
            result = 59 * result + height
            result = 59 * result + pixelData.contentHashCode()
            return result
        }

        override fun toString(): String =
            "Sprite(width=$width, height=$height, pixelData=${Arrays.toString(pixelData)})"

        class SpriteBuilder {
            private var width: Int = 0
            private var height: Int = 0
            private var pixelData: ByteArray? = null

            fun width(width: Int): SpriteBuilder = apply { this.width = width }

            fun height(height: Int): SpriteBuilder = apply { this.height = height }

            fun pixelData(pixelData: ByteArray): SpriteBuilder =
                apply { this.pixelData = pixelData }

            fun build(): Sprite = Sprite(width, height, pixelData ?: ByteArray(0))
        }

        companion object {
            @JvmStatic
            fun builder(): SpriteBuilder = SpriteBuilder()

            private fun scaleTo24BitColor(color: Int, fromBits: Int): Int {
                val maxValueOld = (1 shl fromBits) - 1
                return (color * 255) / maxValueOld
            }
        }
    }

    class SpriteDimensions(
        val width: Int,
        val height: Int
    ) {
        fun toBuilder(): SpriteDimensionsBuilder = SpriteDimensionsBuilder()
            .width(width)
            .height(height)

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is SpriteDimensions) {
                return false
            }
            return width == other.width && height == other.height
        }

        override fun hashCode(): Int {
            var result = 1
            result = 59 * result + width
            result = 59 * result + height
            return result
        }

        override fun toString(): String =
            "SpriteDimensions(width=$width, height=$height)"

        class SpriteDimensionsBuilder {
            private var width: Int = 0
            private var height: Int = 0

            fun width(width: Int): SpriteDimensionsBuilder =
                apply { this.width = width }

            fun height(height: Int): SpriteDimensionsBuilder =
                apply { this.height = height }

            fun build(): SpriteDimensions = SpriteDimensions(width, height)
        }

        companion object {
            @JvmStatic
            fun builder(): SpriteDimensionsBuilder = SpriteDimensionsBuilder()
        }
    }

}
