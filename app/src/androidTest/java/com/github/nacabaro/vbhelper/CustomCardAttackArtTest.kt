package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vb.dim.adventure.DimAdventures
import com.github.cfogrady.vb.dim.adventure.BemAdventureLevels
import com.github.cfogrady.vb.dim.card.BemCard
import com.github.cfogrady.vb.dim.card.DimCard
import com.github.cfogrady.vb.dim.character.DimStats
import com.github.cfogrady.vb.dim.character.BemCharacterStats
import com.github.cfogrady.vb.dim.fusion.AttributeFusions
import com.github.cfogrady.vb.dim.fusion.DimFusions
import com.github.cfogrady.vb.dim.fusion.DimSpecificFusions
import com.github.cfogrady.vb.dim.fusion.SpecificFusions
import com.github.cfogrady.vb.dim.header.DimHeader
import com.github.cfogrady.vb.dim.header.BemHeader
import com.github.cfogrady.vb.dim.sprite.SpriteData
import com.github.cfogrady.vb.dim.transformation.DimEvolutionRequirements
import com.github.cfogrady.vb.dim.transformation.BemTransformationRequirements
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.screens.settingsScreen.controllers.CardImportController
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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

    @Test fun reimportRefreshesAutomaticNamesButPreservesAnExplicitManualName() = runBlocking {
        val importer = CardImportController(db)
        val id = importer.importParsedCard(card(), "old-file.bin")
        assertEquals(id, importer.importParsedCard(card(), "new-file.bin"))
        assertEquals("new-file", db.cardDao().getCardById(id)?.name)
        assertFalse(db.cardDao().getCardById(id)!!.nameIsUserEdited)
        db.cardDao().renameCard(id.toInt(), "My chosen name")
        assertEquals(id, importer.importParsedCard(card(), "third-file.bin"))
        assertEquals("My chosen name", db.cardDao().getCardById(id)?.name)
        assertTrue(db.cardDao().getCardById(id)!!.nameIsUserEdited)
    }

    @Test fun reimportRestoresSpecificJogressWithoutRecreatingCardCharacters() = runBlocking {
        val importer = CardImportController(db)
        val id = importer.importParsedCard(card(), "jogress.bin")
        val before = db.characterDao().getCharactersForCard(id).associateBy { it.charaIndex }
        val pair = SpecificFusions.SpecificFusionEntry.builder().fromCharacterIndex(1).toCharacterIndex(2)
            .backupDimId(0).backupCharacterIndex(0).build()
        val parsed = card().toBuilder().specificFusions(DimSpecificFusions.builder().entries(listOf(pair)).build()).build()
        assertEquals(id, importer.importParsedCard(parsed, "jogress.bin"))
        assertEquals(before, db.characterDao().getCharactersForCard(id).associateBy { it.charaIndex })
        val links = db.dexDao().getCardEvolutionLinks(id).first()
        assertEquals(setOf(before.getValue(0).id, before.getValue(1).id), links.map { it.fromId }.toSet())
        assertTrue(links.all { it.isJogress && it.toId == before.getValue(2).id })
        val partnerDetails = db.cardFusionsDao().getSpecificJogressForCharacter(before.getValue(0).id).first().single()
        assertEquals(1, partnerDetails.partnerCharaIndex)
    }

    @Test fun namedReimportNeverChangesAnotherSameBodyCustomVariant() = runBlocking {
        val importer = CardImportController(db)
        val first = importer.importParsedCard(card(7, 8), "one.bin")
        val second = importer.importParsedCard(card(15, 14), "two.bin")
        assertEquals(first, importer.importParsedCard(card(15, 14), "one.bin"))
        assertEquals("two", db.cardDao().getCardById(second)?.name)
        assertEquals(2, db.cardDao().getAllCards().size)
        val other = db.characterDao().getCharactersForCard(second).single { it.charaIndex == 2 }
        assertEquals(15, db.cardAttackArtDao().getForCharacter(other.id)?.smallAttackId)
        assertEquals(14, db.cardAttackArtDao().getForCharacter(other.id)?.largeAttackId)
    }

    @Test fun bemFirstAdventureRequirementAndAbsentRequirementStayDistinctAfterReimport() = runBlocking {
        val parsed = BemCard.builder().header(BemHeader.builder().dimId(42).bemFlags(ByteArray(32)).build())
            .characterStats(BemCharacterStats.builder().characterEntries((0..2).map { stage ->
                BemCharacterStats.BemCharacterStatEntry.builder().stage(stage).attribute(1)
                    .hp(100).dp(90).ap(80).spriteResizeFlag(2).build()
            }).build())
            .spriteData(SpriteData.builder().text("Synthetic BEM").sprites((0..95).map {
                SpriteData.Sprite.builder().width(1).height(1).pixelData(byteArrayOf(it.toByte(), 0)).build()
            }).build()).adventureLevels(BemAdventureLevels.builder().levels(emptyList()).build())
            .attributeFusions(AttributeFusions.builder().entries(emptyList()).build())
            .transformationRequirements(BemTransformationRequirements.builder().transformationEntries(listOf(
                BemTransformationRequirements.BemTransformationRequirementEntry.builder().fromCharacterIndex(0).toCharacterIndex(1)
                    .minutesUntilTransformation(60).requiredCompletedAdventureLevel(0).build(),
                BemTransformationRequirements.BemTransformationRequirementEntry.builder().fromCharacterIndex(1).toCharacterIndex(2)
                    .minutesUntilTransformation(60).requiredCompletedAdventureLevel(65535).build()
            )).build()).build()
        val importer = CardImportController(db)
        val id = importer.importParsedCard(parsed, "bem.bin")
        val characters = db.characterDao().getCharactersForCard(id).associateBy { it.charaIndex }
        assertEquals(0, db.characterDao().getEvolutionRequirementsForCard(characters.getValue(0).id).first().single().requiredAdventureLevelCompleted)
        assertEquals(-1, db.characterDao().getEvolutionRequirementsForCard(characters.getValue(1).id).first().single().requiredAdventureLevelCompleted)
        assertEquals(id, importer.importParsedCard(parsed, "bem.bin"))
        assertEquals(1, db.characterDao().getEvolutionRequirementsForCard(characters.getValue(0).id).first().size)
    }

    @Test fun creationFailureRollsBackTheWholeCardInsteadOfLeavingABrokenNewImport() = runBlocking {
        val importer = CardImportController(db)
        try {
            importer.importParsedCard(card(), "broken.bin") { _, _ -> error("Interrupted creation") }
            fail("Creation must fail")
        } catch (_: IllegalStateException) { }
        assertTrue(db.cardDao().getAllCards().isEmpty())
        assertTrue(db.characterDao().getAllCharacters().isEmpty())
    }

    @Test fun explicitImportResultsDistinguishNewCardsFromIdenticalReimports() = runBlocking {
        val importer = CardImportController(db)
        val first = importer.importParsedCardWithResult(card(), "batch.bin")
        val second = importer.importParsedCardWithResult(card(), "batch.bin")
        assertTrue(first.isNew)
        assertFalse(second.isNew)
        assertEquals(first.cardId, second.cardId)
        assertEquals("batch", second.cardName)
    }
}
