package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.domain.characters.Sprite
import org.junit.Assert.*
import org.junit.Test

class NormalizedSpriteFramesTest {
    private fun fixture(): Sprite {
        val frames = (1..12).map { byteArrayOf(it.toByte(), 0) }
        return Sprite(1, frames[0], frames[1], frames[2], frames[3], frames[4], frames[5], frames[6], frames[7],
            frames[8], frames[9], frames[10], frames[11], 1, 1)
    }

    @Test fun chosenSetPreservesTheNativeTwelvePoseOrder() {
        val sprite = fixture()
        (1..12).forEach { pose -> assertArrayEquals(byteArrayOf(pose.toByte(), 0), sprite.displayFrame(pose)) }
    }

    @Test fun missingSecondaryAndAttackPosesUseTheChosenIdle() {
        val sprite = fixture().copy(spriteIdle2 = byteArrayOf(), spriteAttack = byteArrayOf())
        assertArrayEquals(sprite.spriteIdle1, sprite.displayFrame(2))
        assertArrayEquals(sprite.spriteIdle1, sprite.displayFrame(11))
    }

    @Test fun malformedSecondaryBufferDoesNotReachTheDecoder() {
        val sprite = fixture().copy(spriteWalk2 = byteArrayOf(1))
        assertArrayEquals(sprite.spriteIdle1, sprite.displayFrame(4))
    }

    @Test fun invalidDimensionsAndMissingFallbackProduceNoFrame() {
        assertNull(fixture().copy(width = 0).displayFrame(1))
        assertNull(fixture().copy(spriteIdle1 = byteArrayOf(), spriteAttack = byteArrayOf()).displayFrame(11))
    }
}
