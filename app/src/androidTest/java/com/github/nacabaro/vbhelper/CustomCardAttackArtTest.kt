package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vb.dim.adventure.DimAdventures
import com.github.cfogrady.vb.dim.card.DimCard
import com.github.cfogrady.vb.dim.character.DimStats
import com.github.cfogrady.vb.dim.fusion.DimFusions
import com.github.cfogrady.vb.dim.header.DimHeader
import com.github.cfogrady.vb.dim.sprite.SpriteData
import com.github.cfogrady.vb.dim.transformation.DimEvolutionRequirements
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.screens.settingsScreen.controllers.CardImportController
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomCardAttackArtTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(IndividualIntegrity.callback).build()
    }

    @After fun teardown() { db.close() }

    private fun card(small: Int = 7, large: Int = 8, body: Int = 24): DimCard = DimCard.builder()
        .header(DimHeader.builder().dimId(0).build())
        .characterStats(DimStats.builder().characterEntries((0..2).map { stage ->
            DimStats.DimStatBlock.builder().stage(stage).attribute(1).hp(3).dp(10).ap(2)
                .smallAttackId(small).bigAttackId(large).build()
        }).build())
        .spriteData(SpriteData.builder().text("Custom fixture").sprites((0..36).map { index ->
            SpriteData.Sprite.builder().width(1).height(1)
                .pixelData(byteArrayOf((if (index == 24) body else index).toByte(), 0)).build()
        }).build())
        .adventureLevels(DimAdventures.builder().levels(emptyList()).build())
        .transformationRequirements(DimEvolutionRequirements.builder().transformationEntries(emptyList()).build())
        .attributeFusions(DimFusions.builder().entries(emptyList()).build())
        .build()

    @Test fun reimportRestoresLegacyAttackArtInPlaceAndPreservesTheExistingIndividual() = runBlocking {
        val importer = CardImportController(db)
        val cardId = importer.importParsedCard(card(), "custom.bin")
        val species = db.characterDao().getCharactersForCard(cardId).single { it.charaIndex == 2 }
        val individualId = "custom-owned-individual"
        val storedId = db.userCharacterDao().insertCharacterData(UserCharacter(
            individualId = individualId, charId = species.id, ageInDays = 4, mood = 80, vitalPoints = 9000,
            transformationCountdown = 10, injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 30, currentPhaseBattlesWon = 7, currentPhaseBattlesLost = 1,
            totalBattlesWon = 12, totalBattlesLost = 3, activityLevel = 0, heartRateCurrent = 0,
            characterType = DeviceType.VBDevice, isActive = true
        ))
        db.openHelper.writableDatabase.execSQL("DELETE FROM CardAttackArt")
        db.cardDao().renameCard(cardId.toInt(), "Renamed custom")

        assertEquals(cardId, importer.importParsedCard(card(15, 14), "original-file-name.bin"))
        assertEquals(1, db.cardDao().getAllCards().size)
        assertEquals(species.id, db.userCharacterDao().getCharacter(storedId).charId)
        assertEquals(individualId, db.userCharacterDao().getCharacter(storedId).individualId)
        assertEquals(9000, db.userCharacterDao().getCharacter(storedId).vitalPoints)
        assertEquals(12, db.userCharacterDao().getCharacter(storedId).totalBattlesWon)
        assertEquals("Renamed custom", db.cardDao().getCardById(cardId)?.name)
        assertEquals(15, db.cardAttackArtDao().getForCharacter(species.id)?.smallAttackId)
        assertEquals(14, db.cardAttackArtDao().getForCharacter(species.id)?.largeAttackId)
    }

    @Test fun twoCustomCardsSharingTheSameDimNumberKeepDifferentAttackAssignments() = runBlocking {
        val importer = CardImportController(db)
        val first = importer.importParsedCard(card(7, 8), "one.bin")
        val second = importer.importParsedCard(card(15, 14), "two.bin")
        assertNotEquals(first, second)
        val firstSpecies = db.characterDao().getCharactersForCard(first).single { it.charaIndex == 2 }
        val secondSpecies = db.characterDao().getCharactersForCard(second).single { it.charaIndex == 2 }
        assertEquals(7, db.cardAttackArtDao().getForCharacter(firstSpecies.id)?.smallAttackId)
        assertEquals(15, db.cardAttackArtDao().getForCharacter(secondSpecies.id)?.smallAttackId)
        assertEquals(8, db.cardAttackArtDao().getForCharacter(firstSpecies.id)?.largeAttackId)
        assertEquals(14, db.cardAttackArtDao().getForCharacter(secondSpecies.id)?.largeAttackId)
    }
}
