package com.tamerslab.farm.ui.digifarm

import com.tamerslab.domain.characters.Sprite
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.utils.DeviceType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM coverage for the artwork contract and the legacy four-frame fallback. The roster resolver
 * itself needs Room, so it is exercised through the shipped gate instead.
 */
class FarmArtworkTest {

    @Test
    fun spriteArtworkMapsEveryPoseField() {
        val sprite = sprite()

        val artwork = farmArtworkFromSprite(sprite)

        assertArrayEquals(sprite.spriteIdle1, artwork.idle1)
        assertArrayEquals(sprite.spriteIdle2, artwork.idle2)
        assertArrayEquals(sprite.spriteWalk1, artwork.walk1)
        assertArrayEquals(sprite.spriteWalk2, artwork.walk2)
        assertArrayEquals(sprite.spriteRun1, artwork.run1)
        assertArrayEquals(sprite.spriteRun2, artwork.run2)
        assertArrayEquals(sprite.spriteTrain1, artwork.train1)
        assertArrayEquals(sprite.spriteTrain2, artwork.train2)
        assertArrayEquals(sprite.spriteHappy, artwork.happy)
        assertArrayEquals(sprite.spriteSleep, artwork.sleep)
        assertEquals(48, artwork.width)
        assertEquals(64, artwork.height)
    }

    @Test
    fun contentKeyIsStableForEqualContentAndChangesWithAnyFrame() {
        val expected = farmArtworkFromSprite(sprite()).contentKey

        // Same bytes in fresh arrays: the key is content-based, not identity-based.
        assertEquals(expected.toLong(), farmArtworkFromSprite(sprite()).contentKey.toLong())

        val singleFrameChanges = listOf(
            "idle1" to sprite(idle1 = bytes(101)),
            "idle2" to sprite(idle2 = bytes(102)),
            "walk1" to sprite(walk1 = bytes(103)),
            "walk2" to sprite(walk2 = bytes(104)),
            "run1" to sprite(run1 = bytes(105)),
            "run2" to sprite(run2 = bytes(106)),
            "train1" to sprite(train1 = bytes(107)),
            "train2" to sprite(train2 = bytes(108)),
            "happy" to sprite(happy = bytes(109)),
            "sleep" to sprite(sleep = bytes(110)),
        )
        singleFrameChanges.forEach { (pose, changed) ->
            assertNotEquals(
                "$pose must change the content key",
                expected.toLong(),
                farmArtworkFromSprite(changed).contentKey.toLong(),
            )
        }

        assertNotEquals(
            "width must change the content key",
            expected.toLong(),
            farmArtworkFromSprite(sprite(width = 49)).contentKey.toLong(),
        )
        assertNotEquals(
            "height must change the content key",
            expected.toLong(),
            farmArtworkFromSprite(sprite(height = 65)).contentKey.toLong(),
        )
    }

    @Test
    fun bodyAspectIsClampedToTheRendererExtrusionRange() {
        assertEquals(1f, artwork(width = 48, height = 48).bodyAspect, 1e-6f)
        assertEquals(0.5f, artwork(width = 10, height = 20).bodyAspect, 1e-6f)
        assertEquals(1.8f, artwork(width = 18, height = 10).bodyAspect, 1e-6f)

        // Outside the legal range both directions clamp instead of skewing the extruded body.
        assertEquals(0.5f, artwork(width = 1, height = 100).bodyAspect, 1e-6f)
        assertEquals(1.8f, artwork(width = 100, height = 1).bodyAspect, 1e-6f)
    }

    @Test
    fun bodyRadiusIsPositiveAndProportionalToAspect() {
        // Residents render 30% smaller than the original 0.13-unit framing (owner request). The
        // collision radius follows the same height, so no other constant has to change with it.
        assertEquals(0.091f, FARM_RESIDENT_HEIGHT_UNITS, 1e-6f)

        val square = artwork(width = 40, height = 40)
        assertEquals(0.5f * FARM_RESIDENT_HEIGHT_UNITS, square.bodyRadius, 1e-6f)
        assertTrue("collision radius must be positive", square.bodyRadius > 0f)

        val tall = artwork(width = 40, height = 80)
        assertEquals(0.25f * FARM_RESIDENT_HEIGHT_UNITS, tall.bodyRadius, 1e-6f)
        assertTrue("clamped tall body keeps a positive radius", tall.bodyRadius > 0f)

        val wide = artwork(width = 28, height = 20)
        assertEquals(1.4f * square.bodyRadius, wide.bodyRadius, 1e-6f)
    }

    @Test
    fun legacyCharacterFallbackReusesFourFramesAcrossPoses() {
        val character = character()

        val artwork = farmArtworkFromCharacter(character)

        assertArrayEquals(character.spriteIdle, artwork.idle1)
        assertArrayEquals(character.spriteIdle2, artwork.idle2)
        // Walk and train reuse the real run frames; happy/sleep reuse the idle frames.
        assertArrayEquals(character.spriteRun1, artwork.walk1)
        assertArrayEquals(character.spriteRun2, artwork.walk2)
        assertArrayEquals(character.spriteRun1, artwork.run1)
        assertArrayEquals(character.spriteRun2, artwork.run2)
        assertArrayEquals(character.spriteRun1, artwork.train1)
        assertArrayEquals(character.spriteRun2, artwork.train2)
        assertArrayEquals(character.spriteIdle, artwork.happy)
        assertArrayEquals(character.spriteIdle2, artwork.sleep)
        assertEquals(character.spriteWidth, artwork.width)
        assertEquals(character.spriteHeight, artwork.height)
    }

    private fun bytes(seed: Int): ByteArray = ByteArray(8) { index -> (seed * 31 + index).toByte() }

    private fun sprite(
        idle1: ByteArray = bytes(1),
        idle2: ByteArray = bytes(2),
        walk1: ByteArray = bytes(3),
        walk2: ByteArray = bytes(4),
        run1: ByteArray = bytes(5),
        run2: ByteArray = bytes(6),
        train1: ByteArray = bytes(7),
        train2: ByteArray = bytes(8),
        happy: ByteArray = bytes(9),
        sleep: ByteArray = bytes(10),
        attack: ByteArray = bytes(11),
        dodge: ByteArray = bytes(12),
        width: Int = 48,
        height: Int = 64,
    ): Sprite = Sprite(
        spriteIdle1 = idle1,
        spriteIdle2 = idle2,
        spriteWalk1 = walk1,
        spriteWalk2 = walk2,
        spriteRun1 = run1,
        spriteRun2 = run2,
        spriteTrain1 = train1,
        spriteTrain2 = train2,
        spriteHappy = happy,
        spriteSleep = sleep,
        spriteAttack = attack,
        spriteDodge = dodge,
        width = width,
        height = height,
    )

    private fun artwork(width: Int = 48, height: Int = 64): FarmArtwork =
        farmArtworkFromSprite(sprite(width = width, height = height))

    private fun character(): CharacterDtos.CharacterWithSprites =
        CharacterDtos.CharacterWithSprites(
            id = 42L,
            charId = 420L,
            stage = 1,
            attribute = NfcCharacter.Attribute.Vaccine,
            ageInDays = 3,
            mood = 60,
            vitalPoints = 5,
            transformationCountdown = 0,
            injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 0,
            currentPhaseBattlesWon = 0,
            currentPhaseBattlesLost = 0,
            totalBattlesWon = 0,
            totalBattlesLost = 0,
            activityLevel = 1,
            heartRateCurrent = 72,
            characterType = DeviceType.APP,
            spriteIdle = bytes(21),
            spriteIdle2 = bytes(22),
            spriteRun1 = bytes(23),
            spriteRun2 = bytes(24),
            spriteWidth = 40,
            spriteHeight = 40,
            nameSprite = ByteArray(0),
            nameSpriteWidth = 0,
            nameSpriteHeight = 0,
            isBemCard = false,
            nickname = "Agumon",
            speciesName = "Agumon",
            isInAdventure = false,
            active = true,
            isFavorite = true,
        )
}
