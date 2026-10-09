package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import com.github.nacabaro.vbhelper.world.WorldRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugWorldSpawnTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: WorldRepository

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(IndividualIntegrity.callback).build()
        repository = WorldRepository(db, clock = { 123_000L })
        seed("Card", mapOf("id" to 1, "name" to "Enabled card", "worldSpawnsEnabled" to 1))
        seed("Card", mapOf("id" to 2, "name" to "Disabled card", "worldSpawnsEnabled" to 0))
        seed("Sprite", mapOf("id" to 1))
        seed("CardCharacter", mapOf("id" to 100, "cardId" to 1, "spriteId" to 1, "attribute" to "Virus"))
        seed("CardCharacter", mapOf("id" to 200, "cardId" to 2, "spriteId" to 1, "attribute" to "Data"))
        seed("SpeciesProfile", mapOf("cardCharacterId" to 200, "speciesName" to "Chosen Digimon", "source" to "MANUAL"))
    }

    @After fun cleanup() { db.close() }

    @Test fun pickerIncludesUndiscoveredCharactersFromDisabledCards() = runBlocking {
        val choices = repository.getDebugSpawnCharacters()
        assertEquals(setOf(100L, 200L), choices.map { it.id }.toSet())
        val chosen = choices.single { it.id == 200L }
        assertEquals("Disabled card", chosen.cardName)
        assertEquals("Chosen Digimon", chosen.speciesName)
    }

    @Test fun chosenCharacterSpawnsWithItsOwnIdentityWithinTwentyMeters() = runBlocking {
        val id = repository.spawnDebugDigimon(-23.55, -46.63, cardCharacterId = 200)!!
        val spawn = db.worldSpawnDao().getActiveSpawnsSync(123_000).single { it.id == id }
        assertEquals(200L, spawn.cardCharacterId)
        assertTrue(RadarWorldGeometry.relative(GeoPoint(-23.55, -46.63),
            GeoPoint(spawn.latitude, spawn.longitude)).withinRadius(20.0))
        assertEquals(1, count("DigimonIndividual"))
        assertEquals(1, count("DigimonPersonalityTraits"))
        assertEquals(1, count("WildRelationship"))
    }

    @Test fun randomSpawnStillUsesOnlyEnabledCards() = runBlocking {
        repository.spawnDebugDigimon(-23.55, -46.63)
        assertEquals(100L, db.worldSpawnDao().getActiveSpawnsSync(123_000).single().cardCharacterId)
    }

    @Test fun removedSelectionDoesNotFallBackToRandomOrCreateAnIndividual() = runBlocking {
        db.cardDao().deleteCard(2)
        assertNull(repository.spawnDebugDigimon(-23.55, -46.63, cardCharacterId = 200))
        assertEquals(0, count("WorldSpawn"))
        assertEquals(0, count("DigimonIndividual"))
    }

    private fun count(table: String) = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use {
        it.moveToFirst(); it.getInt(0)
    }

    private fun seed(table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                if (column in overrides) values[column] = overrides[column]
                else if (cursor.getInt(3) != 0 && cursor.isNull(4) && column != "id") {
                    values[column] = when (cursor.getString(2)) { "TEXT" -> ""; "BLOB" -> byteArrayOf(0); else -> 0 }
                }
            }
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray())
    }
}
