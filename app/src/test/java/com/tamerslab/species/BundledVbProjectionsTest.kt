package com.tamerslab.species

import com.tamerslab.domain.characters.Sprite
import com.tamerslab.domain.world.RecruitmentState
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.WorldDtos
import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.utils.DeviceType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The byte projections keep every gameplay value; only artwork fields come from the shipped sprite. */
class BundledVbProjectionsTest {
    private fun frame(value: Byte) = ByteArray(8) { value }

    private fun sprite() = Sprite(id = -77,
        spriteIdle1 = frame(1), spriteIdle2 = frame(2), spriteWalk1 = frame(3), spriteWalk2 = frame(4),
        spriteRun1 = frame(5), spriteRun2 = frame(6), spriteTrain1 = frame(7), spriteTrain2 = frame(8),
        spriteHappy = frame(9), spriteSleep = frame(10), spriteAttack = frame(11), spriteDodge = frame(12),
        width = 49, height = 47)

    private fun character(speciesName: String? = "Agumon") = CharacterDtos.CharacterWithSprites(
        id = 7, charId = 70, stage = 2, attribute = NfcCharacter.Attribute.Vaccine, ageInDays = 3, mood = 80,
        vitalPoints = 1200, transformationCountdown = 4, injuryStatus = NfcCharacter.InjuryStatus.None,
        trophies = 2, currentPhaseBattlesWon = 1, currentPhaseBattlesLost = 2, totalBattlesWon = 9,
        totalBattlesLost = 4, activityLevel = 2, heartRateCurrent = 60, characterType = DeviceType.APP,
        spriteIdle = frame(31), spriteIdle2 = frame(32), spriteRun1 = frame(33), spriteRun2 = frame(34),
        spriteWidth = 32, spriteHeight = 32, nameSprite = frame(35), nameSpriteWidth = 40, nameSpriteHeight = 8,
        isBemCard = false, nickname = "Agumon", speciesName = speciesName, isInAdventure = false, active = true,
        isFavorite = true,
    )

    private fun spawn(speciesName: String? = "Agumon", cardId: Long? = null) = WorldDtos.SpawnWithDetails(
        id = 9, cardCharacterId = 90, individualId = "wild-9", latitude = 1.0, longitude = 2.0,
        spawnedAt = 10, expiresAt = 20, interacted = false, charaIndex = 2, stage = 2, cardId = cardId,
        externalCharacterId = "app_mon000090", attribute = NfcCharacter.Attribute.Vaccine,
        baseHp = 100, baseBp = 10, baseAp = 20, isBemCard = false,
        spriteIdle = frame(41), spriteIdle2 = frame(42), spriteWalk = frame(43), spriteWalk2 = frame(44),
        spriteRun = frame(45), spriteRun2 = frame(46), spriteWidth = 48, spriteHeight = 48,
        speciesName = speciesName, mood = 50, recruitmentState = RecruitmentState.WILD,
    )

    @Test fun storedPartnerUsesTheShippedIdleAndRunFramesAndKeepsEverythingElse() {
        val mapped = character().withBundledVbArtwork(sprite())
        assertArrayEquals(frame(1), mapped.spriteIdle)
        assertArrayEquals(frame(2), mapped.spriteIdle2)
        assertArrayEquals(frame(5), mapped.spriteRun1)
        assertArrayEquals(frame(6), mapped.spriteRun2)
        assertEquals(49, mapped.spriteWidth)
        assertEquals(47, mapped.spriteHeight)
        assertEquals(7, mapped.id)
        assertEquals(1200, mapped.vitalPoints)
        assertEquals(9, mapped.totalBattlesWon)
        assertEquals("Agumon", mapped.speciesName)
        assertTrue(mapped.isFavorite)
    }

    @Test fun radarSpawnUsesTheShippedIdleWalkAndRunFrames() {
        val mapped = spawn().withBundledVbArtwork(sprite())
        assertArrayEquals(frame(1), mapped.spriteIdle)
        assertArrayEquals(frame(2), mapped.spriteIdle2)
        assertArrayEquals(frame(3), mapped.spriteWalk)
        assertArrayEquals(frame(4), mapped.spriteWalk2)
        assertArrayEquals(frame(5), mapped.spriteRun)
        assertArrayEquals(frame(6), mapped.spriteRun2)
        assertEquals(49, mapped.spriteWidth)
        assertEquals(47, mapped.spriteHeight)
        // Wild identity and combat data must never be rewritten by artwork.
        assertEquals("wild-9", mapped.individualId)
        assertEquals("app_mon000090", mapped.externalCharacterId)
        assertEquals(90L, mapped.cardCharacterId)
        assertEquals(100, mapped.baseHp)
    }

    @Test fun widgetUsesTheShippedIdleAndWalkFrames() {
        val mapped = CharacterDtos.WidgetSprites(
            spriteIdle1 = frame(21), spriteIdle2 = frame(22), spriteWalk1 = frame(23), spriteWalk2 = frame(24),
            width = 32, height = 32,
        ).withBundledVbArtwork(sprite())
        assertArrayEquals(frame(1), mapped.spriteIdle1)
        assertArrayEquals(frame(2), mapped.spriteIdle2)
        assertArrayEquals(frame(3), mapped.spriteWalk1)
        assertArrayEquals(frame(4), mapped.spriteWalk2)
        assertEquals(49, mapped.width)
        assertEquals(47, mapped.height)
    }

    @Test fun digilineThreadAvatarUsesTheShippedIdleFrame() {
        val thread = com.tamerslab.dtos.DigilineStorageThread(
            characterId = 7, individualId = "owned-7", nickname = "Agumon", speciesName = "Agumon",
            spriteIdle = frame(51), spriteWidth = 32, spriteHeight = 32,
            lastMessage = "Hello", lastTimestamp = 10, unreadCount = 2,
        )
        val mapped = thread.withBundledVbArtwork(sprite())
        assertArrayEquals(frame(1), mapped.spriteIdle)
        assertEquals(49, mapped.spriteWidth)
        assertEquals(47, mapped.spriteHeight)
        assertEquals("Hello", mapped.lastMessage)
        assertEquals(2, mapped.unreadCount)
    }

    @Test fun encounterProfileUsesTheShippedIdleFramesAndKeepsItsFacts() {        val progress = CharacterDtos.CardCharaProgress(
            id = 90, spriteIdle = frame(11), spriteIdle2 = frame(12), spriteWidth = 48, spriteHeight = 48,
            nameSprite = frame(13), nameSpriteWidth = 40, nameSpriteHeight = 8, discoveredOn = 5,
            baseHp = 400, baseBp = 40, baseAp = 60, stage = 2, attribute = NfcCharacter.Attribute.Vaccine,
            isCurrentlyAvailable = false,
        )
        val mapped = progress.withBundledVbArtwork(sprite())
        assertArrayEquals(frame(1), mapped.spriteIdle)
        assertArrayEquals(frame(2), mapped.spriteIdle2)
        assertEquals(49, mapped.spriteWidth)
        assertEquals(400, mapped.baseHp)
        assertArrayEquals(frame(13), mapped.nameSprite)
    }
}
