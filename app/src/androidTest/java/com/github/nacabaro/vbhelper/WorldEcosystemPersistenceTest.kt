package com.github.nacabaro.vbhelper

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.world.WorldMovementState
import com.github.nacabaro.vbhelper.world.ecosystem.WorldEcosystemSession
import com.github.nacabaro.vbhelper.world.ecosystem.WorldPauseReason
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real exported v24 schema, rather than restamping a current database. */
@RunWith(AndroidJUnit4::class)
class WorldEcosystemPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val name = "radar-ecosystem-migration-tests.db"
    private var db: AppDatabase? = null

    @Before fun setup() {
        check(context.packageName.endsWith(".integritycheck"))
        context.deleteDatabase(name)
    }

    @After fun cleanup() { db?.close(); context.deleteDatabase(name) }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_25_26, AppDatabase.MIGRATION_26_27, AppDatabase.MIGRATION_27_28, AppDatabase.MIGRATION_28_29, AppDatabase.MIGRATION_29_30, AppDatabase.MIGRATION_30_31, AppDatabase.MIGRATION_31_32, AppDatabase.MIGRATION_32_33, AppDatabase.MIGRATION_33_34, AppDatabase.MIGRATION_34_35, AppDatabase.MIGRATION_35_36, AppDatabase.MIGRATION_36_37, AppDatabase.MIGRATION_37_38)
        .addCallback(IndividualIntegrity.callback)
        .build().also { db = it }

    private fun createVersion(version: Int = 24) {
        val schema = instrumentation.context.assets.open(
            "com.github.nacabaro.vbhelper.database.AppDatabase/$version.json"
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(name)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            sql.setForeignKeyConstraintsEnabled(true)
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    sql.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            seed(sql, "Card", mapOf("id" to 1, "name" to "Migration fixture"))
            seed(sql, "Sprite", mapOf("id" to 1))
            seed(sql, "CardCharacter", mapOf("id" to 100, "cardId" to 1, "spriteId" to 1, "attribute" to "Virus"))
            listOf("wild", "pending", "recruited").forEach { id ->
                seed(sql, "DigimonIndividual", mapOf("individualId" to id, "nickname" to "$id-name"))
                seed(sql, "WildRelationship", mapOf(
                    "individualId" to id, "cardCharacterId" to 100, "trust" to 82,
                    "contactUnlockedAt" to 1_000, "recruitmentState" to when (id) {
                        "pending" -> "PENDING_RECRUITMENT"; "recruited" -> "RECRUITED"; else -> "WILD"
                    }
                ))
                seed(sql, "ChatMessageEntity", mapOf("individualId" to id, "role" to "user", "content" to "private-$id"))
                if (id != "recruited") seed(sql, "WorldSpawn", mapOf(
                    "individualId" to id, "cardCharacterId" to 100,
                    "latitude" to -23.5, "longitude" to -46.6, "mood" to 82,
                    "recruitmentState" to if (id == "pending") "PENDING_RECRUITMENT" else "WILD",
                    "expiresAt" to Long.MAX_VALUE
                )) else seed(sql, "UserCharacter", mapOf("individualId" to id, "charId" to 100))
            }
            if (version >= 25) seed(sql, "WorldEcosystemSession", mapOf(
                "id" to "radar-local", "seed" to 42L, "rulesVersion" to 1,
                "tickIndex" to 37L, "tickRemainderMillis" to 999L, "lastCheckpointAt" to 123_456L,
                "pauseReason" to "PLAYER_BATTLE"
            ))
            sql.version = version
        }
    }

    private fun seed(sql: SQLiteDatabase, table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        sql.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                if (column in overrides) values[column] = overrides[column]
                else if (cursor.getInt(3) != 0 && cursor.isNull(4) && column != "id") {
                    values[column] = when (cursor.getString(2)) {
                        "TEXT" -> ""; "BLOB" -> byteArrayOf(0); else -> 0
                    }
                }
            }
        }
        sql.execSQL(
            "INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray()
        )
    }

    @Test fun migrationPreservesHomesIdentityPrivateHistoryTrustAndRecruits() = runBlocking {
        createVersion()
        val database = open()
        val wild = database.worldSpawnDao().getByIndividualId("wild")!!
        assertEquals(-23.5, wild.homeLatitude, 0.0)
        assertEquals(-46.6, wild.homeLongitude, 0.0)
        assertEquals(WorldMovementState.HOME, wild.movementState)
        assertEquals(25.0, wild.anchorRadiusMeters, 0.0)
        assertNull(wild.wanderTargetLatitude)
        assertNull(wild.wanderTargetLongitude)
        assertNull(wild.denId)
        assertEquals(0, wild.ecosystemEmotion)
        assertEquals(82, wild.mood)
        assertEquals("wild-name", database.digimonIndividualDao().getIndividual("wild")!!.nickname)
        assertEquals(82, database.wildRelationshipDao().get("wild")!!.trust)
        assertEquals(1_000L, database.wildRelationshipDao().get("wild")!!.contactUnlockedAt)
        assertEquals("PENDING_RECRUITMENT", database.worldSpawnDao().getByIndividualId("pending")!!.recruitmentState.name)
        assertEquals("RECRUITED", database.wildRelationshipDao().get("recruited")!!.recruitmentState)
        database.openHelper.writableDatabase.query("SELECT content FROM ChatMessageEntity ORDER BY individualId").use {
            assertEquals(3, it.count)
            assertTrue(it.moveToFirst())
            assertEquals("private-pending", it.getString(0))
        }
        database.openHelper.writableDatabase.query("SELECT individualId FROM UserCharacter").use {
            assertTrue(it.moveToFirst()); assertEquals("recruited", it.getString(0))
        }
        assertEquals(30, database.openHelper.writableDatabase.version)
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        assertCheckpointRoundTrip(database)
    }

    @Test fun freshCreationSupportsTheSameDurableClockContract() = runBlocking {
        val database = open()
        database.openHelper.writableDatabase.query("PRAGMA table_info(WorldSpawn)").use {
            val columns = buildSet { while (it.moveToNext()) add(it.getString(1)) }
            assertTrue(columns.containsAll(listOf("homeLatitude", "homeLongitude", "movementState", "ecosystemEmotion")))
        }
        assertCheckpointRoundTrip(database)
    }

    @Test fun migrationFrom25PreservesTheExistingClockWhileAddingInteractionTables() = runBlocking {
        createVersion(25)
        val database = open()
        val session = database.worldEcosystemDao().getSession()!!
        assertEquals(42L, session.seed)
        assertEquals(37L, session.tickIndex)
        assertEquals(999L, session.tickRemainderMillis)
        assertEquals(WorldPauseReason.PLAYER_BATTLE, session.pauseReason)
        assertTrue(database.worldInteractionDao().getOpenInteractions().isEmpty())
        assertEquals(30, database.openHelper.writableDatabase.version)
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }

    @Test fun migrationFrom26PreservesHistoryAndAddsReplayAndDialogueIndexes() = runBlocking {
        createVersion(26)
        val database=open()
        assertEquals(42L,database.worldEcosystemDao().getSession()!!.seed)
        assertEquals(0L,database.worldSpawnDao().getByIndividualId("wild")!!.movementTick)
        assertEquals(82,database.wildRelationshipDao().get("wild")!!.trust)
        assertTrue(database.worldEcosystemDao().getBonds().isEmpty())
        database.openHelper.writableDatabase.query("PRAGMA index_list(WorldNpcBattle)").use { cursor ->
            val indexes=buildSet { while(cursor.moveToNext())add(cursor.getString(1)) }
            assertTrue("index_WorldNpcBattle_interactionId" in indexes)
        }
    }

    @Test fun migrationFrom29AddsDurableMemoriesWithoutChangingExistingConversations() = runBlocking {
        createVersion(29)
        val database=open()
        assertEquals(30,database.openHelper.writableDatabase.version)
        assertEquals("private-wild",database.chatDao().getMessagesSync("wild").single().content)
        assertEquals(82,database.wildRelationshipDao().get("wild")!!.trust)
        assertTrue(database.worldChatMemoryDao().getMemories("wild").isEmpty())
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0,it.count) }
    }

    private suspend fun assertCheckpointRoundTrip(database: AppDatabase) {
        val session = WorldEcosystemSession(
            seed = 42L, tickIndex = 37L, tickRemainderMillis = 999L,
            lastCheckpointAt = 123_456L, revision = 8L,
            regionLatitude = -23.5, regionLongitude = -46.6, pauseReason = WorldPauseReason.PLAYER_BATTLE
        )
        database.worldEcosystemDao().saveSession(session)
        database.close()
        assertEquals(session, open().worldEcosystemDao().getSession())
    }
}
