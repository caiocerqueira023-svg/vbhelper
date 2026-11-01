package com.tamerslab.hardware.dim.sprite

data class SpriteWithIndex(
    val sprite: SpriteData.Sprite,
    val index: Int
) {
    val size: Int
        get() = sprite.getByteCountAt16BitPerPixel()

    val pixelData: ByteArray
        get() = sprite.pixelData

    fun toBuilder(): SpriteWithIndexBuilder = SpriteWithIndexBuilder()
        .sprite(sprite)
        .index(index)

    class SpriteWithIndexBuilder {
        private var sprite: SpriteData.Sprite? = null
        private var index: Int = 0

        fun sprite(sprite: SpriteData.Sprite): SpriteWithIndexBuilder =
            apply { this.sprite = sprite }

        fun index(index: Int): SpriteWithIndexBuilder =
            apply { this.index = index }

        fun build(): SpriteWithIndex = SpriteWithIndex(sprite!!, index)
    }

    companion object {
        @JvmStatic
        fun builder(): SpriteWithIndexBuilder = SpriteWithIndexBuilder()
    }
}
