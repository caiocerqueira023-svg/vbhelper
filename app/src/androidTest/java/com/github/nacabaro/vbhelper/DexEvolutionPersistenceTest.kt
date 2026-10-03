package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.dtos.CardEvolutionGraph
import com.github.nacabaro.vbhelper.source.DexRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DexEvolutionPersistenceTest {
    private lateinit var db: AppDatabase

    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db.withTransaction {
            seed("Card", mapOf("id" to 7, "cardId" to 42, "name" to "Synthetic Dim", "officialStatus" to "CUSTOM"))
            seed("Card", mapOf("id" to 12, "cardId" to 42, "name" to "Another custom", "officialStatus" to "CUSTOM"))
            seed("Sprite", mapOf("id" to 309))
            for ((id, card, index) in listOf(Triple(101, 7, 0), Triple(102, 7, 1), Triple(103, 7, 2), Triple(201, 12, 0))) {
                seed("CardCharacter", mapOf("id" to id, "cardId" to card, "charaIndex" to index,
                    "stage" to index, "spriteId" to 309, "attribute" to "Data"))
            }
            seed("PossibleTransformations", mapOf("charaId" to 101, "toCharaId" to 102))
            seed("PossibleTransformations", mapOf("charaId" to 101, "toCharaId" to 102))
            seed("PossibleTransformations", mapOf("charaId" to 101, "toCharaId" to 201))
            seed("CardFusions", mapOf("fromCharaId" to 102, "toCharaId" to 103, "attribute" to "Virus"))
            seed("Dex", mapOf("id" to 103, "discoveredOn" to 1234))
        }
    }

    @After fun teardown() { db.close() }

    @Test fun graphQueriesUseLocalIdsAndPreserveDiscoveryAcrossLiveOwnershipUpdates() = runBlocking {
        val updates = Channel<CardEvolutionGraph>(Channel.UNLIMITED)
        val collection = launch { DexRepository(db).getCardEvolutionGraph(7).collect { updates.send(it) } }
        suspend fun await(available: Boolean): CardEvolutionGraph = withTimeout(5000) {
            var graph = updates.receive()
            while (graph.characters.last().isCurrentlyAvailable != available) graph = updates.receive()
            graph
        }
        try {
            val initial = await(false)
            assertEquals(listOf(101L, 102L, 103L), initial.characters.map { it.id })
            assertEquals(listOf(0, 1, 2), initial.characters.map { it.charaIndex })
            assertEquals(2, initial.links.size)
            assertTrue(initial.links.none { it.toId == 201L })
            db.withTransaction {
                seed("UserCharacter", mapOf("id" to 99, "charId" to 103, "individualId" to "dex-chart-test-individual"))
            }
            val owned = await(true)
            assertEquals(initial.links, owned.links)
            db.withTransaction { db.openHelper.writableDatabase.execSQL("DELETE FROM UserCharacter WHERE id=99") }
            val previous = await(false)
            assertEquals(1234L, previous.characters.last().discoveredOn)
            assertEquals(initial.links, previous.links)
            assertTrue(DexRepository(db).getCardEvolutionGraph(12).first().links.isEmpty())
        } finally {
            collection.cancel()
            updates.close()
        }
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
