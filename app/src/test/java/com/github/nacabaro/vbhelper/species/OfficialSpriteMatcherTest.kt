package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.domain.characters.Sprite
import org.junit.Assert.*
import org.junit.Test

class OfficialSpriteMatcherTest {
    private fun sprite(frames: List<ByteArray> = (1..12).map { byteArrayOf(it.toByte(), 0) }) = Sprite(
        id = 99, width = 1, height = 1,
        spriteIdle1 = frames[0], spriteIdle2 = frames[1], spriteWalk1 = frames[2], spriteWalk2 = frames[3],
        spriteRun1 = frames[4], spriteRun2 = frames[5], spriteTrain1 = frames[6], spriteTrain2 = frames[7],
        spriteHappy = frames[8], spriteSleep = frames[9], spriteAttack = frames[10], spriteDodge = frames[11]
    )

    private fun matcher(
        ids: List<String> = listOf("dim034_mon03"),
        load: (String, Int) -> SpriteMatchFrame? = { _, frame -> SpriteMatchFrame(1, 1, byteArrayOf(frame.toByte(), 0)) }
    ) = OfficialSpriteMatcher({ ids }, load)

    @Test fun exactMatchReturnsTheOfficialCardAndZeroBasedSlotInsteadOfTheCustomSlot() {
        assertEquals(listOf(OfficialSpriteIdentity(34, 2)), matcher().findMatches(sprite()))
    }

    @Test fun everyAnimationFrameMustMatchIncludingTheLastFrame() {
        for (frame in 1..12) {
            val changed = (1..12).map { byteArrayOf((if (it == frame) 31 else it).toByte(), 0) }
            assertTrue("Modified frame $frame must not match", matcher().findMatches(sprite(changed)).isEmpty())
        }
    }

    @Test fun differentDimensionsAndMalformedImportedFramesDoNotMatch() {
        assertTrue(matcher().findMatches(sprite().copy(width = 2)).isEmpty())
        assertTrue(matcher().findMatches(sprite().copy(width = 0)).isEmpty())
        assertTrue(matcher().findMatches(sprite().copy(spriteDodge = byteArrayOf(12))).isEmpty())
        assertTrue(matcher(load = { _, n -> SpriteMatchFrame(2, 1, byteArrayOf(n.toByte(), 0, 0, 0)) })
            .findMatches(sprite()).isEmpty())
    }

    @Test fun missingOfficialAnimationFrameCannotProduceAPartialMatch() {
        assertTrue(matcher(load = { _, n -> if (n == 10) null else SpriteMatchFrame(1, 1, byteArrayOf(n.toByte(), 0)) })
            .findMatches(sprite()).isEmpty())
    }

    @Test fun identicalSpritesReturnAllCandidatesForSpeciesAmbiguityResolution() {
        assertEquals(listOf(OfficialSpriteIdentity(1, 0), OfficialSpriteIdentity(124, 22)),
            matcher(listOf("dim124_mon23", "dim001_mon01", "other_folder", "dim034_mon00")).findMatches(sprite()))
    }

    @Test fun catalogIsIndexedOnceAndOnlyFirstFrameCandidatesNeedFullDecoding() {
        val calls = mutableListOf<Pair<String, Int>>()
        val matcher = matcher(listOf("dim001_mon03", "dim002_mon03")) { id, n ->
            calls += id to n
            // The decoy uses a different silhouette so the mask index excludes it
            // without decoding its remaining poses.
            if (id == "dim001_mon03") SpriteMatchFrame(1, 1, byteArrayOf(n.toByte(), 0))
            else SpriteMatchFrame(1, 2, byteArrayOf(31, 0, 31, 0))
        }
        repeat(2) { assertEquals(listOf(OfficialSpriteIdentity(1, 2)), matcher.findMatches(sprite())) }
        assertEquals(1, calls.count { it == "dim002_mon03" to 1 })
        assertTrue(calls.none { it.first == "dim002_mon03" && it.second > 1 })
    }

    private fun shifted(color: Int, xor: Int): ByteArray {
        val shifted = color xor xor
        return byteArrayOf((shifted and 0xff).toByte(), ((shifted ushr 8) and 0xff).toByte())
    }

    @Test fun paletteShiftedArtStillMatchesWithinEncoderTolerance() {
        // One red LSB differs by ~8 in RGB888: same art through a different DIM/PNG encoder.
        val shifted = (1..12).map { shifted(it, 0x0800) }
        assertEquals(listOf(OfficialSpriteIdentity(34, 2)), matcher().findMatches(sprite(shifted)))
    }

    @Test fun recoloredArtDoesNotMatchDespiteSharingTheSameSilhouette() {
        // Flipping the whole red channel is a recolor, not an encoding difference.
        val recolored = (1..12).map { shifted(it, 0xf800) }
        assertTrue(matcher().findMatches(sprite(recolored)).isEmpty())
    }

    @Test fun silhouetteChangeDoesNotMatchDespiteSharingColors() {
        // 2x2 art where the import punctures one transparent corner pixel: every color
        // still exists somewhere and the trimmed dimensions are unchanged, but the
        // silhouette mask no longer matches the fully opaque official art.
        fun square(frames: List<ByteArray>) = Sprite(
            id = 99, width = 2, height = 2,
            spriteIdle1 = frames[0], spriteIdle2 = frames[1], spriteWalk1 = frames[2], spriteWalk2 = frames[3],
            spriteRun1 = frames[4], spriteRun2 = frames[5], spriteTrain1 = frames[6], spriteTrain2 = frames[7],
            spriteHappy = frames[8], spriteSleep = frames[9], spriteAttack = frames[10], spriteDodge = frames[11]
        )
        fun opaque(n: Int) = byteArrayOf(n.toByte(), 0, (n + 1).toByte(), 0, (n + 2).toByte(), 0, (n + 3).toByte(), 0)
        fun punctured(n: Int) = byteArrayOf(0xe0.toByte(), 7, (n + 1).toByte(), 0, (n + 2).toByte(), 0, (n + 3).toByte(), 0)
        val official: (String, Int) -> SpriteMatchFrame? = { _, n -> SpriteMatchFrame(2, 2, opaque(n)) }
        assertTrue(matcher(load = official).findMatches(square((1..12).map(::punctured))).isEmpty())
        // The intact silhouette still matches, so the rejection comes from the mask.
        val intact = square((1..12).map(::opaque))
        assertEquals(listOf(OfficialSpriteIdentity(34, 2)), matcher(load = official).findMatches(intact))
    }

    @Test fun pngPixelsAreComparedInTheNativeRgb565ColorSpaceWithTransparentBackgrounds() {
        // Transparent PNG RGB is irrelevant; native DIM transparency is pure green (0x07e0).
        val frame = SpriteMatchFrame.fromArgb(3, 1, intArrayOf(0x00123456, 0xffff0000.toInt(), 0xff0000ff.toInt()))!!
        assertArrayEquals(byteArrayOf(0xe0.toByte(), 7, 0, 0xf8.toByte(), 31, 0), frame.pixels)
        assertNull(SpriteMatchFrame.fromArgb(1, 1, intArrayOf(0x80ff0000.toInt())))
        assertNull(SpriteMatchFrame.fromArgb(2, 1, intArrayOf(0)))
    }

    @Test fun emptyAndEntirelyTransparentArtCannotIdentifyASpecies() {
        val empty = SpriteMatchFrame(1, 1, byteArrayOf(0xe0.toByte(), 7))
        assertTrue(matcher(load = { _, _ -> empty }).findMatches(sprite(List(12) { empty.pixels })).isEmpty())
        assertTrue(matcher(emptyList()).findMatches(sprite()).isEmpty())
        val black = SpriteMatchFrame(1, 1, byteArrayOf(0, 0))
        assertTrue(matcher(load = { _, _ -> black }).findMatches(sprite(List(12) { black.pixels })).isEmpty())
    }

    @Test fun transparentPngMarginsDoNotPreventAnOtherwiseExactMatch() {
        val padded = (1..12).map { byteArrayOf(0xe0.toByte(), 7, it.toByte(), 0, 0xe0.toByte(), 7) }
        assertEquals(listOf(OfficialSpriteIdentity(34, 2)),
            matcher().findMatches(sprite(padded).copy(width = 3)))
    }

    @Test fun colorShapeAndScaleDifferencesAreNotNormalizedAway() {
        val scaled = (1..12).map { byteArrayOf(it.toByte(), 0, it.toByte(), 0) }
        assertTrue(matcher().findMatches(sprite(scaled).copy(width = 2)).isEmpty())
    }

    @Test fun compactDimMatchesEveryNativePoseWithoutRequiringExtraArenaAnimations() {
        val order = listOf(1, 2, 1, 4, 1, 4, 1, 4, 9, 10, 2, 4)
        val baby = sprite(order.map { byteArrayOf(it.toByte(), 0) })
        assertTrue(matcher().findMatches(baby).isEmpty())
        assertEquals(listOf(OfficialSpriteIdentity(34, 2)), matcher().findMatches(baby, compactDim = true))
        for (pose in listOf(1, 2, 4, 9, 10)) {
            val changed = sprite(order.map { byteArrayOf((if (it == pose) 31 else it).toByte(), 0) })
            assertTrue("Modified native pose $pose must not match", matcher().findMatches(changed, compactDim = true).isEmpty())
        }
    }

    @Test fun compactDimDoesNotIgnoreAnInconsistentRepeatedFrame() {
        val baby = sprite(listOf(1, 2, 1, 4, 1, 4, 1, 4, 9, 10, 2, 4).map { byteArrayOf(it.toByte(), 0) })
        assertTrue(matcher().findMatches(baby.copy(spriteDodge = byteArrayOf(31, 0)), compactDim = true).isEmpty())
    }
}
