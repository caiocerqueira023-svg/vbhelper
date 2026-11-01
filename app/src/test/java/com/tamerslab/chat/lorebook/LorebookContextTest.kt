package com.tamerslab.chat.lorebook

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.tamerslab.chat.data.LorebookEntryDao
import com.tamerslab.domain.lorebook.LorebookEntry
import com.tamerslab.settings.data.SpeciesSettingsRepository
import com.tamerslab.species.SpeciesDatabaseService
import com.tamerslab.species.SpeciesRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class LorebookContextTest {
    @Test fun primarySourceSetCoversTheEntireAboutTabIncludingDistinctWorlds() {
        val sources = ResearchSources.aboutTab.map { it.url }.toSet()
        assertTrue(sources.containsAll(setOf("https://digimon.net/about/", "https://digimon.net/profile/",
            "https://digimon.net/history/", "https://digimon-gameportal.bn-ent.net/product/",
            "https://digimon.net/digitalworld-Iliad/", "https://digimon.net/digitalworld-shambala/")))
        val lore = DigimonWorldLore.entries("en")
        assertTrue(lore.first { "Homeros" in it.keys }.content.contains("Iliad"))
        assertTrue(lore.first { "Kunlun" in it.keys }.content.contains("Shambhala"))
        assertFalse(lore.first { it.title == "Yggdrasill" }.content.contains("all Digital Worlds"))
    }

    @Test fun highPriorityPersonalNotesDoNotDisplacePublishedWorldResearch() = runBlocking {
        SpeciesRepository.clearDatabaseCacheForTests()
        val notes = Notes(listOf(LorebookEntry(title = "Personal theory", triggerKeys = listOf("Digital World"),
            content = "A personal interpretation, not a published observation.", priority = 999)))
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(emptyPreferences())
        }
        val species = SpeciesRepository(settingsRepository = SpeciesSettingsRepository(store),
            service = object : SpeciesDatabaseService {
                override suspend fun getSpeciesDatabase(url: String) = "{\"version\":1,\"species\":{}}".toResponseBody()
            })
        val repository = LorebookRepository(notes, species)
        val matches = repository.scanForMatches("Digital World", "en")
        assertEquals(LoreAuthority.GENERAL_RESEARCH, matches.first().authority)
        assertTrue(matches.any { it.title == "Personal theory" && it.authority == LoreAuthority.PERSONAL_NOTE })
        val context = repository.formatContextBlock(matches, "en")!!
        assertTrue(context.contains("not personal memories"))
        assertTrue(context.contains("Personal note"))
        assertFalse(context.contains("offstage as lived background"))
        SpeciesRepository.clearDatabaseCacheForTests()
    }

    @Test fun settingSpecificTechnologyCarriesItsScopeRatherThanBecomingUniversalDigiviceLore() {
        val entry = DigimonWorldLore.entries("en").first { "Mindlink" in it.keys }
        assertEquals(LoreAuthority.SETTING_OBSERVATION, entry.reference?.authority)
        assertTrue(entry.reference?.scope.orEmpty().contains("Seekers"))
        assertTrue(entry.content.contains("Digimon Linker"))
    }

    private class Notes(private val entries: List<LorebookEntry>) : LorebookEntryDao {
        override fun getAll() = flowOf(entries)
        override suspend fun getAllEnabled() = entries.filter { it.enabled }
        override suspend fun insert(entry: LorebookEntry) = 0L
        override suspend fun update(entry: LorebookEntry) {}
        override suspend fun delete(entry: LorebookEntry) {}
    }
}
