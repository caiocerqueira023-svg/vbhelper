package com.tamerslab.hardware.dim.sprite

import java.util.LinkedList
import java.util.TreeSet

class SpriteFinder private constructor(
    private val spritesBySize: MutableMap<Int, LinkedList<SpriteWithIndex>>,
    private val spriteSizes: TreeSet<Int>
) {
    fun getNextSmallestSize(size: Int): SpriteWithIndex? {
        val nextSmallestSize = spriteSizes.floor(size) ?: return null
        return removeFromSizePool(nextSmallestSize)
    }

    fun getNextLargestSprite(): SpriteWithIndex {
        val largestSize = spriteSizes.last()
        return removeFromSizePool(largestSize)
    }

    fun isEmpty(): Boolean = spritesBySize.isEmpty()

    private fun removeFromSizePool(size: Int): SpriteWithIndex {
        val spritesAtSize = spritesBySize[size]!!
        val sprite = spritesAtSize.removeFirst()
        if (spritesAtSize.isEmpty()) {
            spriteSizes.remove(size)
            spritesBySize.remove(size)
        }
        return sprite
    }

    companion object {
        @JvmStatic
        fun createSpriteFinder(sprites: List<SpriteData.Sprite>): SpriteFinder {
            val spritesBySize = HashMap<Int, LinkedList<SpriteWithIndex>>()
            val spriteSizes = TreeSet<Int>()
            var i = 0
            for (sprite in sprites) {
                val size = sprite.getByteCountAt16BitPerPixel()
                val spritesAtSize = spritesBySize.getOrPut(size) { LinkedList() }
                spritesAtSize.addLast(
                    SpriteWithIndex.builder()
                        .index(i)
                        .sprite(sprite)
                        .build()
                )
                i++
            }
            spriteSizes.addAll(spritesBySize.keys)
            return SpriteFinder(spritesBySize, spriteSizes)
        }
    }
}
