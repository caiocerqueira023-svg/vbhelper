package com.tamerslab

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.data.database.IndividualIntegrity
import com.tamerslab.data.database.PreservingMigrationFactory
import com.tamerslab.farm.DigifarmRepository
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.domain.device_data.DigimonIndividual
import com.tamerslab.domain.digifarm.Farm
import com.tamerslab.domain.digifarm.FarmResident
import com.tamerslab.domain.scan.DigimonScanProgress
import com.tamerslab.domain.species.SpeciesProfile
import com.tamerslab.domain.species.SpeciesSource
import com.tamerslab.species.data.DigimonScanRepository
import com.tamerslab.settings.data.SpritePreferenceRepository
import com.tamerslab.species.data.ScanConversionResult
import com.tamerslab.species.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Small real-Room regression batch, isolated from the user's installation and preferences. */
@RunWith(AndroidJUnit4::class)
class PartnerParticipationPersistenceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "participation-regression.db"
    private val preferences get() = context.getSharedPreferences("participation-regression", Context.MODE_PRIVATE)
    private lateinit var db: AppDatabase
    private lateinit var graphics: SpritePreferenceRepository
    private var now = 100_000L
    private val catalog = CanonicalSpeciesCatalog(CanonicalSpeciesDatabaseDto(species = listOf(
        CanonicalSpeciesDto("digimon.agumon", "Agumon", dotSprites = DotSpriteAssociationDto("dot", listOf("dot"))),
        CanonicalSpeciesDto("digimon.noart", "Noartmon")), spriteSets = listOf(
        DotSpriteSetDto("dot", -100, "Agumon", "Bandai", width = 1, height = 1,
            frames = mapOf("idle_1" to "frames/${"a".repeat(64)}.bin")))))

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_42_43, AppDatabase.MIGRATION_43_44, AppDatabase.MIGRATION_44_45,
            AppDatabase.MIGRATION_45_46, AppDatabase.MIGRATION_46_47, AppDatabase.MIGRATION_47_48, AppDatabase.MIGRATION_48_49,
            AppDatabase.MIGRATION_49_50, AppDatabase.MIGRATION_50_51, AppDatabase.MIGRATION_51_52, AppDatabase.MIGRATION_52_53,
            AppDatabase.MIGRATION_53_54, AppDatabase.MIGRATION_54_55)
        .openHelperFactory(PreservingMigrationFactory())
        .addCallback(IndividualIntegrity.callback).build().also {
            it.participation = PartnerParticipationGate(it, { catalog }, { now })
        }

    @Before fun setup() {
        check(context.packageName.endsWith(".integritycheck"))
        context.deleteDatabase(name)
        preferences.edit().clear().commit()
        graphics = SpritePreferenceRepository(preferences)
        db = open()
    }
    @After fun teardown() { db.close(); context.deleteDatabase(name); preferences.edit().clear().commit() }

    private fun seed(table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { columns ->
            while (columns.moveToNext()) {
                val column = columns.getString(1)
                if (column in overrides) values[column] = overrides[column]
                else if (columns.getInt(3) != 0 && columns.isNull(4) && column != "id") values[column] = when (columns.getString(2)) {
                    "TEXT" -> ""; "BLOB" -> byteArrayOf(0, 0); else -> 0
                }
            }
        }
        db.openHelper.writableDatabase.execSQL("INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray())
    }

    private suspend fun fixtures() {
        seed("Card", mapOf("id" to 1, "cardId" to 1, "name" to "Test DiM", "officialStatus" to "OFFICIAL"))
        val bytes = byteArrayOf(0, 0)
        db.spriteDao().insertSprite(Sprite(1, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, 1, 1))
        for ((index, species) in listOf("Agumon", "Noartmon").withIndex()) {
            val id = index + 1
            seed("CardCharacter", mapOf("id" to id * 100, "cardId" to 1, "spriteId" to 1,
                "charaIndex" to index, "stage" to 2, "attribute" to "Vaccine", "nameWidth" to 1, "nameHeight" to 1))
            db.speciesProfileDao().upsert(SpeciesProfile(id * 100L, species, species, source = SpeciesSource.OFFICIAL_MATCHED))
            db.digimonIndividualDao().insert(DigimonIndividual("owned-$id", now, speciesName = species))
            seed("UserCharacter", mapOf("id" to id, "individualId" to "owned-$id", "charId" to id * 100,
                "injuryStatus" to "None", "characterType" to "VBDevice", "vitalPoints" to 1234, "mood" to 80,
                "isActive" to if (id == 2) 1 else 0))
        }
    }

    @Test fun artworkPreferenceDoesNotClearActiveSelectionOrDisableAnyStoredPartner() = runBlocking {
        fixtures()
        graphics.setPreference(SpritePreference.DOT_FIRST)
        db.withTransaction { db.participation!!.reconcileLocked() }
        assertTrue(db.userCharacterDao().getCharacter(2).isActive)
        db.withTransaction {
            db.participation!!.snapshot().requireOwned(2)
            db.userCharacterDao().setOnlyActiveCharacter(2)
        }
        assertTrue(db.participation!!.snapshot().owned(1).usable)
        assertTrue(db.participation!!.snapshot().owned(2).usable)
        assertEquals(2, db.userCharacterDao().getAllCharacters().first().size)
        assertEquals(1234, db.userCharacterDao().getCharacter(2).vitalPoints)
    }

    @Test fun pausedFarmAndAdventureResumeWithoutInactiveTimeEvenAfterReopen() = runBlocking {
        fixtures()
        db.digifarmDao().insertFarm(Farm("farm", "Test farm", createdAt = now, lastSimulatedAt = now, randomSeed = 1))
        db.digifarmDao().insertResident(FarmResident("owned-2", "farm", 0f, 0f, 0f, 0f,
            energy = 60, satiety = 70, activity = "TRAIN", activityStartedAt = now, updatedAt = now))
        seed("Adventure", mapOf("characterId" to 2, "finishesAdventure" to 160L, "originalDuration" to 1))
        seed("ParticipationPause", mapOf("individualId" to "owned-2", "pausedAt" to now))
        now += 3_600_000L
        db.close()
        graphics = SpritePreferenceRepository(preferences)
        db = open()
        db.withTransaction { db.participation!!.reconcileLocked() }
        val remaining = db.adventureDao().getAdventureCharacters().first().single().finishesAdventure - now / 1000
        assertEquals(60L, remaining)
        assertEquals(60, db.digifarmDao().getResident("owned-2")!!.energy)
        assertEquals(70, db.digifarmDao().getResident("owned-2")!!.satiety)
        assertEquals(now, db.digifarmDao().getResident("owned-2")!!.updatedAt)
    }

    @Test fun missingDotArtDoesNotBlockNormalScanConversion() = runBlocking {
        fixtures()
        db.digimonScanDao().saveProgress(DigimonScanProgress(200, 100, now))
        graphics.setPreference(SpritePreference.DOT_FIRST)
        assertTrue(DigimonScanRepository(db).convert(200) is ScanConversionResult.Converted)
        assertEquals(0, db.digimonScanDao().getProgress(200))
        assertEquals(3, db.userCharacterDao().getAllCharacters().first().size)
    }

    @Test fun staleModelActionIsRejectedInsideTheQuestTransactionBeforeAnyProgress() = runBlocking {
        fixtures()
        seed("QuestInstance", mapOf("id" to "stale", "giverId" to "owned-1", "giverCardCharacterId" to 100,
            "giverName" to "Agumon", "category" to "NORMAL", "state" to "ACTIVE", "templateId" to "supplies",
            "createdAt" to now, "acceptedAt" to now, "revision" to 0))
        graphics.setPreference(SpritePreference.DOT_FIRST)
        val failure = runCatching {
            com.tamerslab.quests.QuestRepository(db).act("owned-1",
                com.tamerslab.quests.QuestDialogueAction(
                     com.tamerslab.quests.QuestActionType.DELIVER, "stale", 1, "private:1"))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("Quest progress changed"))
        assertEquals(0L, db.questDao().getQuest("stale")!!.revision)
        assertNull(db.questDao().reward("stale"))
    }

    @Test fun cosmeticPreferenceRemainsIndependentOfBattleAndSettlementLeases() = runBlocking {
        fixtures()
        val visible = db.participation!!.battleLease()
        val settlement = db.participation!!.battleLease()
        visible.close()
        graphics.setPreference(SpritePreference.DOT_FIRST)
        assertEquals(SpritePreference.DOT_FIRST, graphics.current)
        settlement.close()
        settlement.close()
        graphics.setPreference(SpritePreference.VB_FIRST)
        assertTrue(db.userCharacterDao().getCharacter(2).isActive)
    }

    @Test fun cosmeticChangeDoesNotInvalidateAnOtherwiseReadyRecruitment() = runBlocking {
        fixtures()
        db.digimonIndividualDao().insert(DigimonIndividual("wild", now, speciesName = "Agumon"))
        seed("WildRelationship", mapOf("individualId" to "wild", "cardCharacterId" to 100,
            "speciesNameSnapshot" to "Agumon", "trust" to 100, "recruitmentState" to "PENDING_RECRUITMENT"))
        seed("QuestInstance", mapOf("id" to "recruit", "giverId" to "wild", "giverCardCharacterId" to 100,
            "giverName" to "Agumon", "category" to "RECRUITMENT", "state" to "READY", "templateId" to "recruitment",
            "createdAt" to now, "acceptedAt" to now, "revision" to 0))
        seed("QuestObjective", mapOf("id" to "recruit:0", "questId" to "recruit", "type" to "PARTNER_STAGE", "required" to 1, "progress" to 1))
        graphics.setPreference(SpritePreference.DOT_FIRST)
        val result = com.tamerslab.world.WorldRepository(db).recruitIndividual("wild")
        assertTrue(result.isSuccess)
        assertEquals(3, db.userCharacterDao().getAllCharacters().first().size)
        assertEquals("RECRUITED", db.wildRelationshipDao().get("wild")!!.recruitmentState)
    }

    @Test fun realVersion42UpgradeRetainsPermanentIdentityAndPassesRoomValidation() = runBlocking {
        db.close()
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tamerslab.data.database.AppDatabase/42.json").bufferedReader().use {
                JSONObject(it.readText()).getJSONObject("database")
            }
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
            val views = schema.getJSONArray("views")
            for (index in 0 until views.length()) {
                val view = views.getJSONObject(index)
                legacy.execSQL(view.getString("createSql").replace("\${VIEW_NAME}", view.getString("viewName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))
            legacy.execSQL("""INSERT INTO DigimonIndividual(individualId, createdAt, nickname,
                lastCelebratedWinsMilestone, lastCelebratedTrophyMilestone, blastMode, speciesName)
                VALUES ('kept', 55, 'Keep me', 2, 3, 'NONE', 'Agumon')""")
            legacy.version = 42
        }
        db = open()
        val individual = db.digimonIndividualDao().getIndividual("kept")!!
        assertEquals(55, db.openHelper.readableDatabase.version)
        assertEquals("Keep me", individual.nickname)
        assertEquals("Agumon", individual.speciesName)
        assertEquals(2, individual.lastCelebratedWinsMilestone)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
