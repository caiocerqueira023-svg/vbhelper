package com.github.nacabaro.vbhelper

import android.graphics.BitmapFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.screens.settingsScreen.controllers.CardImportController
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import com.github.nacabaro.vbhelper.species.SpeciesDatabaseService
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import com.github.nacabaro.vbhelper.species.SpriteMatchFrame
import com.github.nacabaro.vbhelper.species.bundledOfficialSpriteMatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomDimSpeciesPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: SpeciesRepository
    private val settings = SpeciesSettingsRepository(object : DataStore<Preferences> {
        override val data = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(emptyPreferences())
    })

    @Before fun setup() {
        check(context.packageName.endsWith(".integritycheck"))
        SpeciesRepository.clearDatabaseCacheForTests()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(IndividualIntegrity.callback).build()
        repo = SpeciesRepository(database = db, settingsRepository = settings,
            service = object : SpeciesDatabaseService {
                override suspend fun getSpeciesDatabase(url: String): okhttp3.ResponseBody =
                    throw AssertionError("Custom matching must work without a network request")
            },
            assetLoader = { context.assets.open("species.json").bufferedReader().use { it.readText() } },
            spriteMatcher = bundledOfficialSpriteMatcher(context.assets))
    }

    @After fun teardown() { db.close() }

    private fun rawFrame(id: String, frame: Int): SpriteData.Sprite {
        val bitmap = context.assets.open(BattleAssetPaths.characterFrame(id, frame)).use { BitmapFactory.decodeStream(it)!! }
        val native = try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            SpriteMatchFrame.fromArgb(bitmap.width, bitmap.height, pixels)!!
        } finally { bitmap.recycle() }
        val width = maxOf(64, native.width)
        val height = maxOf(64, native.height)
        val data = ByteArray(width * height * 2) { if (it % 2 == 0) 0xe0.toByte() else 7 }
        for (row in 0 until native.height) native.pixels.copyInto(data, row * width * 2,
            row * native.width * 2, (row + 1) * native.width * 2)
        return SpriteData.Sprite.builder().width(width).height(height).pixelData(data).build()
    }

    private fun card(modifiedLastFrame: Boolean = false, includeBabySprites: Boolean = false): DimCard {
        val sprites = (0..50).map { index ->
            SpriteData.Sprite.builder().width(1).height(1).pixelData(byteArrayOf(index.toByte(), 0)).build()
        }.toMutableList()
        if (includeBabySprites) {
            for ((slot, start) in listOf(1 to 10, 2 to 16)) {
                for ((index, frame) in listOf(1, 2, 4, 9, 10).withIndex()) {
                    sprites[start + index + 1] = rawFrame("dim034_mon0$slot", frame)
                }
            }
        }
        // Custom number 1, with BEM 131/slot 3 in custom slot 3 and DIM 34/slot 3 in custom slot 4.
        for (frame in 1..12) {
            sprites[23 + frame] = rawFrame("dim131_mon03", frame)
            sprites[37 + frame] = rawFrame("dim034_mon03", frame)
        }
        if (modifiedLastFrame) {
            val original = sprites[49]
            val bytes = original.pixelData.copyOf()
            bytes[0] = 0 // One changed pixel in the final pose invalidates the whole match.
            sprites[49] = original.toBuilder().pixelData(bytes).build()
        }
        return DimCard.builder().header(DimHeader.builder().dimId(1).build())
            .characterStats(DimStats.builder().characterEntries((0..3).map { index ->
                DimStats.DimStatBlock.builder().stage(index.coerceAtMost(2)).attribute(1).hp(3).dp(10).ap(2)
                    .smallAttackId(7).bigAttackId(8).build()
            }).build())
            .spriteData(SpriteData.builder().text("Custom species fixture").sprites(sprites).build())
            .adventureLevels(DimAdventures.builder().levels(emptyList()).build())
            .transformationRequirements(DimEvolutionRequirements.builder().transformationEntries(emptyList()).build())
            .attributeFusions(DimFusions.builder().entries(emptyList()).build()).build()
    }

    @Test fun importingReorderedSpritesLoadsCompleteSpeciesDataWithoutOfficialCardsOrNetwork() = runBlocking {
        val result = CardImportController(db, repo).importParsedCardWithResult(card(), "custom.bin")
        assertTrue(result.isNew)
        assertEquals(1, db.cardDao().getAllCards().size)
        assertEquals(1, db.cardDao().getCardById(result.cardId)!!.cardId)
        assertEquals(OfficialStatus.UNKNOWN, db.cardDao().getCardById(result.cardId)!!.officialStatus)
        val characters = db.characterDao().getCharactersForCard(result.cardId).associateBy { it.charaIndex }
        assertNull(repo.getProfileForCharacter(characters.getValue(0).id))
        assertNull(repo.getProfileForCharacter(characters.getValue(1).id))
        val json = repo.parseDatabase(context.assets.open("species.json").bufferedReader().use { it.readText() })
        for ((index, officialNumber) in listOf(2 to "131", 3 to "34")) {
            val expected = json.species.getValue(officialNumber).getValue("3")
            val profile = repo.getProfileForCharacter(characters.getValue(index).id)!!
            assertEquals(expected.name, profile.speciesName)
            assertEquals(expected.level, profile.level)
            assertEquals(expected.type, profile.type)
            assertEquals(expected.profile, profile.profileDescription)
            assertEquals(expected.specialMoves, profile.specialMoves)
        }
        db.cardDao().updateOfficialStatus(result.cardId, OfficialStatus.CUSTOM)
        assertEquals(2, repo.matchSpeciesForCard(result.cardId))
        assertEquals(OfficialStatus.CUSTOM, db.cardDao().getCardById(result.cardId)!!.officialStatus)
    }

    @Test fun modifiedPoseDoesNotMatchAndSameNumberCustomCardsKeepSeparateProfiles() = runBlocking {
        val importer = CardImportController(db, repo)
        val exact = importer.importParsedCardWithResult(card(), "exact.bin")
        val modified = importer.importParsedCardWithResult(card(true), "modified.bin")
        assertNotEquals(exact.cardId, modified.cardId)
        assertEquals(2, db.speciesProfileDao().getByCardId(exact.cardId).size)
        assertEquals(1, db.speciesProfileDao().getByCardId(modified.cardId).size)
        val changedCharacter = db.characterDao().getCharactersForCard(modified.cardId).single { it.charaIndex == 3 }
        assertNull(repo.getProfileForCharacter(changedCharacter.id))
    }

    @Test fun compactDimBabyAnimationsMatchAllFiveNativePoses() = runBlocking {
        val imported = CardImportController(db, repo).importParsedCardWithResult(card(includeBabySprites = true), "babies.bin")
        val characters = db.characterDao().getCharactersForCard(imported.cardId).associateBy { it.charaIndex }
        assertEquals("Sakumon", repo.getProfileForCharacter(characters.getValue(0).id)!!.speciesName)
        assertEquals("Sakuttomon", repo.getProfileForCharacter(characters.getValue(1).id)!!.speciesName)
        assertEquals(4, db.speciesProfileDao().getByCardId(imported.cardId).size)
    }

    @Test fun reimportAndRetryRestoreMissingMatchesAndPreserveManualSpeciesEdits() = runBlocking {
        val importer = CardImportController(db, repo)
        val imported = importer.importParsedCardWithResult(card(), "custom.bin")
        db.cardDao().updateOfficialStatus(imported.cardId, OfficialStatus.CUSTOM)
        val characters = db.characterDao().getCharactersForCard(imported.cardId).associateBy { it.charaIndex }
        val manual = SpeciesProfile(characters.getValue(2).id, "My species", null, "Custom level", "Custom type",
            "My description", listOf("My move"), SpeciesSource.MANUAL)
        db.speciesProfileDao().upsert(manual)
        db.openHelper.writableDatabase.execSQL("DELETE FROM SpeciesProfile WHERE cardCharacterId = ?",
            arrayOf(characters.getValue(3).id))
        val reimport = importer.importParsedCardWithResult(card(), "custom.bin")
        assertFalse(reimport.isNew)
        assertEquals(imported.cardId, reimport.cardId)
        assertEquals(characters.values.toSet(), db.characterDao().getCharactersForCard(imported.cardId).toSet())
        assertEquals(manual, repo.getProfileForCharacter(manual.cardCharacterId))
        assertEquals("Zubamon", repo.getProfileForCharacter(characters.getValue(3).id)!!.speciesName)
        assertEquals(1, repo.matchOfficialSpeciesForCard(imported.cardId)) // CUSTOM still takes the sprite path.
        assertEquals(manual, repo.getProfileForCharacter(manual.cardCharacterId))
        assertEquals(OfficialStatus.CUSTOM, db.cardDao().getCardById(imported.cardId)!!.officialStatus)
    }
}
