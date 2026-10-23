package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.domain.characters.Sprite

/** App-authored neutral data marker. Reserved -1 is excluded from bundled Dot IDs. */
object PlaceholderSprite {
    const val ID = -1

    fun create(): Sprite {
        val size = 32
        val frame = ByteArray(size * size * 2)
        for (y in 0 until size) for (x in 0 until size) {
            val distance = kotlin.math.abs(x - 15) + kotlin.math.abs(y - 15)
            val value = if (distance in 11..12) 0xBDF7 else 0x07E0
            val index = (y * size + x) * 2
            frame[index] = value.toByte()
            frame[index + 1] = (value ushr 8).toByte()
        }
        return Sprite(ID, frame, frame, frame, frame, frame, frame, frame, frame,
            frame, frame, frame, frame, size, size)
    }
}
