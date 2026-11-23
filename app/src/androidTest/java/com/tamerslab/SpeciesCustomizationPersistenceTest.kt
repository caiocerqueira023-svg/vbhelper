package com.tamerslab

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.data.database.IndividualIntegrity
import com.tamerslab.data.database.PreservingMigrationFactory
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.domain.species.SpeciesProfile
import com.tamerslab.domain.species.SpeciesSource
import com.tamerslab.domain.species.SpeciesSpriteChoice
import com.tamerslab.species.SpeciesCustomizationRepository
import com.tamerslab.species.SpeciesEntryDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeciesCustomizationPersistenceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "species-customization-regression.db"
    private lateinit var db: AppDatabase
    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(AppDatabase.MIGRATION_40_41, AppDatabase.MIGRATION_41_42, AppDatabase.MIGRATION_42_43,
            AppDatabase.MIGRATION_43_44, AppDatabase.MIGRATION_44_45, AppDatabase.MIGRATION_45_46, AppDatabase.MIGRATION_46_47,
            AppDatabase.MIGRATION_47_48, AppDatabase.MIGRATION_48_49, AppDatabase.MIGRATION_49_50, AppDatabase.MIGRATION_50_51,
            AppDatabase.MIGRATION_51_52, AppDatabase.MIGRATION_52_53, AppDatabase.MIGRATION_53_54,
            AppDatabase.MIGRATION_54_55, AppDatabase.MIGRATION_55_56, AppDatabase.MIGRATION_56_57)
        .openHelperFactory(PreservingMigrationFactory())
        .addCallback(IndividualIntegrity.callback).build()

    @Before fun setup() {
        check(context.packageName.endsWith(".integritycheck"))
        context.deleteDatabase(databaseName)
        db = open()
    }
    @After fun teardown() { db.close(); context.deleteDatabase(databaseName) }

    private fun sprite(id: Int) = byteArrayOf(id.toByte(), 0).let { bytes ->
        Sprite(id, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, 1, 1)
    }

    private fun seed(table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { columns ->
            while (columns.moveToNext()) {
                val name = columns.getString(1)
                if (name in overrides) values[name] = overrides[name]
                else if (columns.getInt(3) != 0 && columns.isNull(4) && name != "id") values[name] = when (columns.getString(2)) {
                    "TEXT" -> ""; "BLOB" -> byteArrayOf(0); else -> 0
                }
            }
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray())
    }

    private suspend fun fixtures() {
        (1..3).forEach { index ->
            seed("Card", mapOf("id" to index, "cardId" to if (index == 2) 124 else index, "name" to "Card $index",
                "officialStatus" to "OFFICIAL"))
            db.spriteDao().insertSprite(sprite(index))
            seed("CardCharacter", mapOf("id" to index * 100, "cardId" to index, "spriteId" to index,
                "stage" to 5, "attribute" to "Vaccine", "baseHp" to index * 400))
            seed("UserCharacter", mapOf("id" to index, "charId" to index * 100, "individualId" to "appearance-fixture-$index",
                "injuryStatus" to "None", "characterType" to "VBDevice", "vitalPoints" to 1234, "mood" to 80))
        }
        db.speciesProfileDao().upsert(SpeciesProfile(100, "Gallantmon", "Gallantmon", source = SpeciesSource.OFFICIAL_MATCHED))
        db.speciesProfileDao().upsert(SpeciesProfile(200, "Dukemon", "Dukemon", source = SpeciesSource.OFFICIAL_MATCHED))
        db.speciesProfileDao().upsert(SpeciesProfile(300, "Agumon", "Agumon", source = SpeciesSource.OFFICIAL_MATCHED))
    }

    @Test fun oneChoiceAppliesAcrossDimsAndAliasesWithoutChangingOriginalArtOrStats() = runBlocking {
        fixtures()
        // A base species keeps its catalog identity; only its shared appearance is normalized.
        db.speciesCustomizationDao().upsertSpriteChoice(SpeciesSpriteChoice("dukemon", 3))
        val members = db.userCharacterDao().getAllCharacters().first().filter { it.charId in listOf(100L, 200L) }
        assertEquals(2, members.size)
        members.forEach { assertArrayEquals(sprite(3).spriteIdle1, it.spriteIdle); assertEquals(1234, it.vitalPoints) }
        assertArrayEquals(sprite(1).spriteIdle1, db.spriteDao().getOriginalForCharacter(100)!!.spriteIdle1)
        assertArrayEquals(sprite(2).spriteIdle1, db.spriteDao().getOriginalForCharacter(200)!!.spriteIdle1)
        assertEquals(1L, db.characterDao().getById(100)!!.spriteId)
        assertEquals(800, db.characterDao().getById(200)!!.baseHp)
        assertArrayEquals(sprite(3).spriteIdle1, db.dexDao().getCharacterProgress(100).first()!!.spriteIdle)
    }

    @Test fun customEntryAndChoiceSurviveDatabaseReopen() = runBlocking {
        fixtures()
        val entry = SpeciesEntryDto("Starlightmon", "Child", "Light", "User-authored profile", listOf("Star Rush"))
        SpeciesCustomizationRepository(db).save(null, entry, 3)
        db.close()
        db = open()
        assertEquals(entry, SpeciesCustomizationRepository(db).mergeCatalog(emptyList()).single())
        assertEquals(3, db.speciesCustomizationDao().getSpriteId("starlightmon"))
    }

    @Test fun copyingACatalogSpeciesLeavesTheOriginalAndItsProfilesUntouched() = runBlocking {
        fixtures()
        val repository = SpeciesCustomizationRepository(db)
        // Re-saving a catalog species under its own name is rejected instead of shadowing the original.
        val rejected = runCatching { repository.save("Dukemon", SpeciesEntryDto("Dukemon"), 3) }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)
        assertTrue(db.speciesCustomizationDao().getCatalog().isEmpty())
        // A renamed copy creates a separate entry; the original and its assigned profiles stay put.
        repository.save("Dukemon", SpeciesEntryDto("My Knightmon", "Ultimate", "Holy Knight", "Copy", listOf("Royal Saber")), 3)
        val original = db.speciesProfileDao().getByCardCharacterId(200)!!
        assertEquals("Dukemon", original.speciesName)
        assertEquals("dukemon", original.appearanceKey)
        assertEquals(3, db.speciesCustomizationDao().getSpriteId("myknightmon"))
        val catalog = repository.mergeCatalog(listOf(SpeciesEntryDto("Dukemon")))
        assertEquals(setOf("Dukemon", "My Knightmon"), catalog.map { it.name }.toSet())
        // Returning the copy to original sprites only clears the copy's choice.
        repository.save("My Knightmon", SpeciesEntryDto("My Knightmon"), null)
        assertNull(db.speciesCustomizationDao().getSpriteId("myknightmon"))
        assertArrayEquals(sprite(2).spriteIdle1, db.spriteDao().getForCharacter(200)!!.spriteIdle1)
    }

    @Test fun editingAnOwnedEntryRenamesItInPlaceAndMovesItsProfiles() = runBlocking {
        fixtures()
        val repository = SpeciesCustomizationRepository(db)
        repository.save(null, SpeciesEntryDto("Starlightmon", "Child", "Light", "Mine", listOf("Star Rush")), null)
        db.speciesProfileDao().upsert(SpeciesProfile(300, "Starlightmon", "Starlightmon", source = SpeciesSource.OFFICIAL_MATCHED))
        repository.save("Starlightmon", SpeciesEntryDto("Nova Starlightmon", "Child", "Light", "Mine", listOf("Star Rush")), null)
        val profile = db.speciesProfileDao().getByCardCharacterId(300)!!
        assertEquals("Nova Starlightmon", profile.speciesName)
        assertEquals("novastarlightmon", profile.appearanceKey)
        assertEquals(listOf("Nova Starlightmon"), repository.mergeCatalog(emptyList()).map { it.name })
    }

    @Test fun unavailableSpriteRejectsSaveWithoutPartialCatalogOrProfileChanges() = runBlocking {
        fixtures()
        val before = db.speciesProfileDao().getByCardCharacterId(100)
        val error = runCatching { SpeciesCustomizationRepository(db).save("Dukemon", SpeciesEntryDto("Changed Name"), 99999) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(before, db.speciesProfileDao().getByCardCharacterId(100))
        assertTrue(db.speciesCustomizationDao().getCatalog().isEmpty())
        assertTrue(db.speciesCustomizationDao().getSpriteChoices().isEmpty())
    }

    @Test fun deletingPreferredSourceClearsItsReferenceAndRestoresOtherMembers() = runBlocking {
        fixtures()
        db.speciesCustomizationDao().upsertSpriteChoice(SpeciesSpriteChoice("dukemon", 3))
        db.openHelper.writableDatabase.execSQL("DELETE FROM Sprite WHERE id = 3")
        assertNull(db.speciesCustomizationDao().getSpriteId("dukemon"))
        assertArrayEquals(sprite(1).spriteIdle1, db.spriteDao().getForCharacter(100)!!.spriteIdle1)
        assertArrayEquals(sprite(2).spriteIdle1, db.spriteDao().getForCharacter(200)!!.spriteIdle1)
    }

    @Test fun allLoadedChoiceIncludesUnassignedSpritesWhileMatchingFilterRemainsSpeciesSpecific() = runBlocking {
        fixtures()
        db.spriteDao().insertSprite(sprite(4))
        val matching = db.speciesCustomizationDao().getSpriteChoices("dukemon", true, "", 0, 24)
        assertEquals(setOf(1, 2), matching.map { it.spriteId }.toSet())
        val all = db.speciesCustomizationDao().getSpriteChoices("dukemon", false, "", 0, 24)
        assertEquals(setOf(1, 2, 3, 4), all.map { it.spriteId }.toSet())
        assertEquals(listOf(4), db.speciesCustomizationDao().getSpriteChoices("dukemon", false, "4", 0, 24).map { it.spriteId })
    }

    @Test fun actualVersion40UpgradePreservesProfilesAndCreatesValidatedVersion41DisplayView() = runBlocking {
        db.close()
        context.deleteDatabase(databaseName)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tamerslab.data.database.AppDatabase/40.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (position in 0 until indices.length())
                    legacy.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))
            legacy.execSQL("INSERT INTO Card (id,cardId,logo,logoWidth,logoHeight,name,stageCount,isBEm,officialStatus,worldSpawnsEnabled) VALUES (1,1,X'00',1,1,'Legacy card',6,0,'OFFICIAL',1)")
            legacy.execSQL("INSERT INTO Sprite (id,spriteIdle1,spriteIdle2,spriteWalk1,spriteWalk2,spriteRun1,spriteRun2,spriteTrain1,spriteTrain2,spriteHappy,spriteSleep,spriteAttack,spriteDodge,width,height) VALUES (1,X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',1,1)")
            legacy.execSQL("INSERT INTO CardCharacter (id,cardId,spriteId,charaIndex,stage,attribute,baseHp,baseBp,baseAp,nameSprite,nameWidth,nameHeight) VALUES (100,1,1,0,5,'Vaccine',400,30,20,X'00',1,1)")
            legacy.execSQL("INSERT INTO SpeciesProfile (cardCharacterId,speciesName,matchedName,level,type,profileDescription,specialMoves,source) VALUES (100,'Gallantmon','Gallantmon','Ultimate','Holy Knight','Legacy profile','[]','OFFICIAL_MATCHED')")
            legacy.version = 40
        }
        db = open()
        val profile = db.speciesProfileDao().getByCardCharacterId(100)!!
        assertEquals("dukemon", profile.appearanceKey)
        assertEquals("Legacy profile", profile.profileDescription)
        assertEquals(56, db.openHelper.readableDatabase.version)
        assertArrayEquals(byteArrayOf(1, 0), db.spriteDao().getForCharacter(100)!!.spriteIdle1)
        assertTrue(db.speciesCustomizationDao().getCatalog().isEmpty())
    }
}
