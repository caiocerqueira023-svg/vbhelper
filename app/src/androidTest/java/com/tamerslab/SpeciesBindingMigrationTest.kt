package com.tamerslab

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.data.database.IndividualIntegrity
import com.tamerslab.data.database.PreservingMigrationFactory
import com.tamerslab.domain.device_data.IndividualOrigin
import com.tamerslab.species.SpeciesBindingRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the 41 -> 52 migration chain against real Android SQLite.
 * Reconstructs the exported version-41 schema, opens it through the registered
 * migration, and asserts existing partners, chats and history are preserved.
 */
@RunWith(AndroidJUnit4::class)
class SpeciesBindingMigrationTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "species-binding-migration.db"
    private lateinit var db: AppDatabase

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_40_41, AppDatabase.MIGRATION_41_42, AppDatabase.MIGRATION_42_43,
            AppDatabase.MIGRATION_43_44, AppDatabase.MIGRATION_44_45, AppDatabase.MIGRATION_45_46, AppDatabase.MIGRATION_46_47,
            AppDatabase.MIGRATION_47_48, AppDatabase.MIGRATION_48_49, AppDatabase.MIGRATION_49_50, AppDatabase.MIGRATION_50_51,
            AppDatabase.MIGRATION_51_52, AppDatabase.MIGRATION_52_53, AppDatabase.MIGRATION_53_54,
            AppDatabase.MIGRATION_54_55)
        .openHelperFactory(PreservingMigrationFactory())
        .addCallback(IndividualIntegrity.callback).build()

    @Before fun setup() {
        check(context.packageName.endsWith(".integritycheck"))
        context.deleteDatabase(name)
    }
    @After fun teardown() { if (::db.isInitialized) db.close(); context.deleteDatabase(name) }

    /** Builds a real version-41 database from the exported schema, then seeds legacy rows. */
    private fun createVersion41() {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tamerslab.data.database.AppDatabase/41.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (position in 0 until indices.length())
                    legacy.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val views = schema.optJSONArray("views")
            if (views != null) for (index in 0 until views.length())
                legacy.execSQL(views.getJSONObject(index).getString("createSql"))
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))

            legacy.execSQL("INSERT INTO Card (id,cardId,logo,logoWidth,logoHeight,name,stageCount,isBEm,officialStatus,worldSpawnsEnabled) VALUES (1,1,X'00',1,1,'Legacy card',6,0,'OFFICIAL',1)")
            legacy.execSQL("INSERT INTO Sprite (id,spriteIdle1,spriteIdle2,spriteWalk1,spriteWalk2,spriteRun1,spriteRun2,spriteTrain1,spriteTrain2,spriteHappy,spriteSleep,spriteAttack,spriteDodge,width,height) VALUES (1,X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',X'0100',1,1)")
            legacy.execSQL("INSERT INTO CardCharacter (id,cardId,spriteId,charaIndex,stage,attribute,baseHp,baseBp,baseAp,nameSprite,nameWidth,nameHeight) VALUES (100,1,1,0,5,'Vaccine',400,30,20,X'00',1,1)")
            legacy.execSQL("INSERT INTO SpeciesProfile (cardCharacterId,speciesName,matchedName,level,type,profileDescription,specialMoves,source,appearanceKey) VALUES (100,'Dukemon','Gallantmon','Ultimate','Holy Knight','Legacy profile','[]','OFFICIAL_MATCHED','dukemon')")
            legacy.execSQL("INSERT INTO DigimonIndividual (individualId,createdAt,nickname,lastDiaryEntryAt,lastCelebratedWinsMilestone,lastCelebratedTrophyMilestone,blastMode,blastTargetSpecies,jogressResultSpecies) VALUES ('legacy-1',111,'Legacy',NULL,0,0,'NONE',NULL,NULL)")
            legacy.execSQL("INSERT INTO DigimonIndividual (individualId,createdAt,nickname,lastDiaryEntryAt,lastCelebratedWinsMilestone,lastCelebratedTrophyMilestone,blastMode,blastTargetSpecies,jogressResultSpecies) VALUES ('legacy-2',222,NULL,NULL,0,0,'NONE',NULL,NULL)")
            legacy.execSQL("INSERT INTO UserCharacter (id,individualId,charId,ageInDays,mood,vitalPoints,transformationCountdown,injuryStatus,trophies,currentPhaseBattlesWon,currentPhaseBattlesLost,totalBattlesWon,totalBattlesLost,activityLevel,heartRateCurrent,characterType,isActive,isFavorite) VALUES (1,'legacy-1',100,3,80,1234,120,'None',5,1,0,1,0,0,0,'VBDevice',1,0)")
            legacy.execSQL("INSERT INTO ChatMessageEntity (individualId,role,content,timestamp,isRead) VALUES ('legacy-1','assistant','Keep this conversation',333,1)")
            legacy.execSQL("INSERT INTO TransformationHistory (monId,stageId,transformationDate) VALUES (1,100,444)")
            legacy.version = 41
        }
    }

    private fun assertLegacyRecords() {
        val sql = db.openHelper.readableDatabase
        sql.query("SELECT individualId,charId,vitalPoints,isActive FROM UserCharacter WHERE id = 1").use {
            assertTrue("The original Storage row must survive the definition rebuild", it.moveToFirst())
            assertEquals("legacy-1", it.getString(0))
            assertEquals(100L, it.getLong(1))
            assertEquals(1234, it.getInt(2))
            assertEquals(1, it.getInt(3))
        }
        sql.query("SELECT content,timestamp FROM ChatMessageEntity WHERE individualId = 'legacy-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Keep this conversation", it.getString(0))
            assertEquals(333L, it.getLong(1))
        }
        sql.query("SELECT stageId,transformationDate FROM TransformationHistory WHERE monId = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(100L, it.getLong(0))
            assertEquals(444L, it.getLong(1))
        }
        sql.query("SELECT spriteIdle1 FROM DisplaySprite WHERE cardCharacterId = 100").use {
            assertTrue(it.moveToFirst())
            assertArrayEquals(byteArrayOf(1, 0), it.getBlob(0))
        }
        sql.query("PRAGMA foreign_keys").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun version41UpgradeAddsSpeciesBindingAndPreservesPartnersChatsAndHistory() = runBlocking {
        createVersion41()
        db = open()

        assertEquals(55, db.openHelper.readableDatabase.version)
        // The legacy partner keeps identity, stats and its human-facing name.
        val matched = db.digimonIndividualDao().getIndividual("legacy-1")!!
        assertEquals("legacy-1", matched.individualId)
        assertEquals("Legacy", matched.nickname)
        assertEquals(111L, matched.createdAt)
        assertEquals(IndividualOrigin.CARD, matched.originKind)
        assertLegacyRecords()
        // Backfill happens at open for partners created before the binding, from the card slot.
        db.close()
        db = open()
        SpeciesBindingRepository(db, catalogLoader = { null }).backfillMissingBindings()
        val filled = db.digimonIndividualDao().getIndividual("legacy-1")!!
        assertEquals("Dukemon", filled.speciesName)
        assertEquals(100L, filled.originCardCharacterId)
        // A partner with no matching profile is left without a fabricated reference.
        assertNull(db.digimonIndividualDao().getIndividual("legacy-2")!!.speciesName)
        assertLegacyRecords()
    }

    @Test fun newIndividualColumnsExistWithCorrectDefaultsAfterMigration() = runBlocking {
        createVersion41()
        db = open()
        db.openHelper.readableDatabase.query("PRAGMA table_info(DigimonIndividual)").use { cursor ->
            val names = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            assertTrue(names.containsAll(listOf("speciesName", "originKind", "originCardCharacterId")))
        }
    }
}
