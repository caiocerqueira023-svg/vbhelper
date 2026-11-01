package com.tamerslab.data.database

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomSchemaCharacterizationTest {
    @Test
    fun schema55SnapshotRemainsThePhase0CharacterizationBaseline() {
        val schemaFile = File("schemas/com.tamerslab.data.database.AppDatabase/55.json")
        assertTrue("Missing Room schema snapshot: ${schemaFile.absolutePath}", schemaFile.isFile)

        val database = JSONObject(schemaFile.readText()).getJSONObject("database")
        assertEquals(55, database.getInt("version"))
        assertEquals("6fd191c26b88a13772edac2e31aad3c2", database.getString("identityHash"))

        val entities = database.getJSONArray("entities")
        val tableNames = (0 until entities.length())
            .map { entities.getJSONObject(it).getString("tableName") }
            .toSet()

        assertEquals(106, tableNames.size)
        assertTrue(tableNames.containsAll(listOf(
            "Card",
            "UserCharacter",
            "WatchTransfer",
            "validated",
            "Farm",
            "WorldSpawn",
        )))
    }
}
