package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SpeciesRepositoryTest {

    private fun createRepository(
        database: AppDatabase? = null,
        settingsRepository: SpeciesSettingsRepository = createMockSettingsRepository(),
        service: SpeciesDatabaseService = createMockService(),
        assetLoader: (() -> String?)? = null
    ): SpeciesRepository {
        return SpeciesRepository(
            database = database,
            settingsRepository = settingsRepository,
            service = service,
            assetLoader = assetLoader
        )
    }

    private fun createMockService(jsonToReturn: String? = null, shouldThrow: Boolean = false): SpeciesDatabaseService {
        return object : SpeciesDatabaseService {
            override suspend fun getSpeciesDatabase(url: String): okhttp3.ResponseBody {
                if (shouldThrow) throw IOException("Simulated network failure")
                return (jsonToReturn ?: "{}").toResponseBody()
            }
        }
    }

    private fun createMockSettingsRepository(): SpeciesSettingsRepository {
        return SpeciesSettingsRepository(createDummyDataStore())
    }

    private fun createDummyDataStore(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        return object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = flowOf(androidx.datastore.preferences.core.emptyPreferences())
            override suspend fun updateData(
                transform: suspend (t: androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences
            ): androidx.datastore.preferences.core.Preferences {
                return androidx.datastore.preferences.core.emptyPreferences()
            }
        }
    }

    @Test
    fun parseDatabase_recoversFromMalformedDuplicateEndingInGitHubJson() {
        val repo = createRepository()
        val malformedJson = """
            {
              "version": 1,
              "species": {
                "34": {
                  "1": {
                    "name": "Sakumon",
                    "level": "Baby I",
                    "type": "Weapon",
                    "profile": "A tiny Digimon...",
                    "specialMoves": ["Sakkuri"]
                  }
                },
                "138": {
                  "23": {
                    "name": "Shoutmon X7",
                    "level": "Ultimate",
                    "type": "Composite",
                    "profile": "The Ardor-burst Mode...",
                    "specialMoves": [
                      "Xros Burning Rocker",
                      "Seven Victorize Maximum"
                    ]
                  }
                }
              }
            }",
                      "All Omega the Fusion",
                      "Seven Victorize Maximum"
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = repo.parseDatabase(malformedJson)
        assertEquals(1, parsed.version)
        assertEquals(2, parsed.species.size)

        // DIM 34
        val dim34 = parsed.species["34"]
        assertNotNull(dim34)
        assertEquals("Sakumon", dim34!!["1"]?.name)

        // BEM 138
        val bem138 = parsed.species["138"]
        assertNotNull(bem138)
        assertEquals("Shoutmon X7", bem138!!["23"]?.name)
        assertEquals(listOf("Xros Burning Rocker", "Seven Victorize Maximum"), bem138["23"]?.specialMoves)
    }

    @Test
    fun cardLookupKeys_generatesExpectedKeysForDimAndBem() {
        val repo = createRepository()

        // Single digit DIM (e.g. 1)
        val keys1 = repo.cardLookupKeys(1)
        assertTrue(keys1.contains("1"))
        assertTrue(keys1.contains("01"))

        // Two-digit DIM (e.g. 34)
        val keys34 = repo.cardLookupKeys(34)
        assertTrue(keys34.contains("34"))

        // Masked DIM (e.g. 0x0112 = 274, masked 18)
        val keysMasked = repo.cardLookupKeys(274)
        assertTrue(keysMasked.contains("274"))
        assertTrue(keysMasked.contains("18"))

        // BEM 3-digit card (e.g. 124)
        val keysBem = repo.cardLookupKeys(124)
        assertTrue(keysBem.contains("124"))

        // BEM 3-digit card (e.g. 138)
        val keysBem138 = repo.cardLookupKeys(138)
        assertTrue(keysBem138.contains("138"))
    }

    @Test
    fun findSpeciesForCard_matchesDimCards() {
        val repo = createRepository()
        val sakumon = SpeciesEntryDto("Sakumon", "Baby I", "Weapon")
        val agumon = SpeciesEntryDto("Agumon", "Child", "Reptile")
        val vmon = SpeciesEntryDto("V-mon", "Child", "Dragon")

        val dbDto = SpeciesDatabaseDto(
            version = 1,
            species = mapOf(
                "1" to mapOf("1" to agumon),
                "02" to mapOf("1" to vmon),
                "18" to mapOf("1" to agumon),
                "34" to mapOf("1" to sakumon)
            )
        )

        // Direct DIM 1
        val match1 = repo.findSpeciesForCard(dbDto, cardId = 1, isBEm = false)
        assertNotNull(match1)
        assertEquals("Agumon", match1!!["1"]?.name)

        // Padded DIM 2 -> matches "02"
        val match2 = repo.findSpeciesForCard(dbDto, cardId = 2, isBEm = false)
        assertNotNull(match2)
        assertEquals("V-mon", match2!!["1"]?.name)

        // Masked DIM (274 = 0x0112 -> 18)
        val matchMasked = repo.findSpeciesForCard(dbDto, cardId = 274, isBEm = false)
        assertNotNull(matchMasked)
        assertEquals("Agumon", matchMasked!!["1"]?.name)

        // Standard DIM 34
        val match34 = repo.findSpeciesForCard(dbDto, cardId = 34, isBEm = false)
        assertNotNull(match34)
        assertEquals("Sakumon", match34!!["1"]?.name)
    }

    @Test
    fun findSpeciesForCard_matchesBemCardsFrom124Onwards() {
        val repo = createRepository()
        val botamon = SpeciesEntryDto("Botamon", "Baby I", "Slime")
        val shoutmon = SpeciesEntryDto("Shoutmon X7", "Ultimate", "Composite")

        val dbDto = SpeciesDatabaseDto(
            version = 1,
            species = mapOf(
                "124" to mapOf("1" to botamon),
                "138" to mapOf("23" to shoutmon)
            )
        )

        // BEM 124
        val match124 = repo.findSpeciesForCard(dbDto, cardId = 124, isBEm = true)
        assertNotNull(match124)
        assertEquals("Botamon", match124!!["1"]?.name)

        // BEM 138
        val match138 = repo.findSpeciesForCard(dbDto, cardId = 138, isBEm = true)
        assertNotNull(match138)
        assertEquals("Shoutmon X7", match138!!["23"]?.name)

        // Masked BEM (380 = 0x017C -> 124)
        val matchMaskedBem = repo.findSpeciesForCard(dbDto, cardId = 380, isBEm = true)
        assertNotNull(matchMaskedBem)
        assertEquals("Botamon", matchMaskedBem!!["1"]?.name)
    }

    @Test
    fun findCharacterEntry_handlesOneIndexedAndPaddedKeys() {
        val repo = createRepository()
        val baby = SpeciesEntryDto("BabyMon", "Baby I")
        val ultimate = SpeciesEntryDto("UltimateMon", "Ultimate")

        // 1-indexed (standard DIM/BEM)
        val cardSpecies1 = mapOf(
            "1" to baby,
            "17" to ultimate
        )
        // charaIndex 0 in DB -> slot 1 in card
        val char0 = repo.findCharacterEntry(cardSpecies1, charaIndex = 0)
        assertNotNull(char0)
        assertEquals("BabyMon", char0!!.name)

        // charaIndex 16 in DB -> slot 17 in card
        val char16 = repo.findCharacterEntry(cardSpecies1, charaIndex = 16)
        assertNotNull(char16)
        assertEquals("UltimateMon", char16!!.name)

        // 2-digit padded keys in card
        val cardSpeciesPadded = mapOf(
            "01" to baby,
            "23" to ultimate
        )
        val charPadded0 = repo.findCharacterEntry(cardSpeciesPadded, charaIndex = 0)
        assertNotNull(charPadded0)
        assertEquals("BabyMon", charPadded0!!.name)

        // BEM slot 23: charaIndex 22 -> slot 23
        val charBem22 = repo.findCharacterEntry(cardSpeciesPadded, charaIndex = 22)
        assertNotNull(charBem22)
        assertEquals("UltimateMon", charBem22!!.name)
    }

    @Test
    fun fetchDatabase_fallsBackToAssetLoaderWhenNetworkFails() = runBlocking {
        val assetJson = """
            {
              "version": 1,
              "species": {
                "124": {
                  "1": { "name": "Botamon", "level": "Baby I" }
                }
              }
            }
        """.trimIndent()

        val repo = createRepository(
            service = createMockService(shouldThrow = true),
            assetLoader = { assetJson }
        )

        val db = repo.fetchDatabase()
        assertNotNull(db)
        assertEquals(1, db!!.version)
        assertEquals("Botamon", db.species["124"]?.get("1")?.name)
    }

    @Test
    fun fullBundledAssetSpeciesJson_loadsAndMatchesAllDimsAndBems() = runBlocking {
        val assetFile = java.io.File("src/main/assets/species.json")
        assertTrue("species.json asset must exist", assetFile.exists())
        val jsonText = assetFile.readText(Charsets.UTF_8)

        val repo = createRepository(
            service = createMockService(shouldThrow = true),
            assetLoader = { jsonText }
        )

        val db = repo.fetchDatabase()
        assertNotNull("Database must be parsed from asset", db)
        val species = db!!.species
        assertEquals(51, species.size)

        // Check DIM cards (2 digits: e.g. 1..37)
        val expectedDims = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 25, 26, 27, 28, 30, 31, 32, 33, 34, 35, 36, 37)
        for (dimId in expectedDims) {
            val cardMap = repo.findSpeciesForCard(db, cardId = dimId, isBEm = false)
            assertNotNull("DIM card $dimId must be matched", cardMap)
            // Verify slot 1 character is found
            val baby1 = repo.findCharacterEntry(cardMap!!, charaIndex = 0)
            assertNotNull("Slot 1 character for DIM $dimId must be resolved", baby1)
            assertTrue("Character name for DIM $dimId must not be blank", baby1!!.name.isNotBlank())
        }

        // Check BEM cards (3 digits >= 124: 124..138)
        val expectedBems = listOf(124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138)
        for (bemId in expectedBems) {
            val cardMap = repo.findSpeciesForCard(db, cardId = bemId, isBEm = true)
            assertNotNull("BEM card $bemId must be matched", cardMap)
            // Verify slot 1 character is found
            val char0 = repo.findCharacterEntry(cardMap!!, charaIndex = 0)
            assertNotNull("Slot 1 character for BEM $bemId must be resolved", char0)
            assertTrue("Character name for BEM $bemId must not be blank", char0!!.name.isNotBlank())
        }

        // Check character count for BEM 138 (23 characters, index 22 is Shoutmon X7)
        val bem138 = repo.findSpeciesForCard(db, cardId = 138, isBEm = true)
        assertNotNull(bem138)
        val shoutmon = repo.findCharacterEntry(bem138!!, charaIndex = 22)
        assertNotNull(shoutmon)
        assertEquals("Shoutmon X7", shoutmon!!.name)
        assertEquals("Ultimate", shoutmon.level)
        assertEquals("Composite", shoutmon.type)
        assertTrue(shoutmon.profile!!.contains("Ardor-burst Mode"))
        assertTrue(shoutmon.specialMoves.contains("Seven Victorize Maximum"))

        // Verify getAllSpeciesNames and getAllSpeciesEntries
        val allNames = repo.getAllSpeciesNames()
        assertTrue("Must have hundreds of Digimon names", allNames.size > 500)
        assertTrue("Must contain Shoutmon X7", allNames.contains("Shoutmon X7"))
        assertTrue("Must contain Sakumon", allNames.contains("Sakumon"))

        val allEntries = repo.getAllSpeciesEntries()
        assertTrue("Must have hundreds of Digimon entries", allEntries.size > 500)
    }
}

