package com.github.nacabaro.vbhelper.species

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SpeciesDatabaseCacheTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class FakeSpeciesService(
        var json: String = """{"version":1,"species":{}}""",
        var delayMillis: Long = 0,
        var fail: Boolean = false
    ) : SpeciesDatabaseService {
        var calls = 0
        override suspend fun getSpeciesDatabase(url: String): ResponseBody {
            calls++
            if (delayMillis > 0) delay(delayMillis)
            if (fail) throw IOException("offline")
            return json.toResponseBody("application/json".toMediaType())
        }
    }

    // The cache is process-wide, so every test starts cold.
    @Before fun clearSharedCache() = SpeciesRepository.clearDatabaseCacheForTests()

    @Test fun separateInstancesShareOneFetch() = runTest {
        val service = FakeSpeciesService()
        repository(service).fetchDatabase()
        repository(service).fetchDatabase()
        assertEquals(1, service.calls)
    }

    private fun repository(service: FakeSpeciesService, now: () -> Long = { 0L }): SpeciesRepository {
        val repository = SpeciesRepository(
            database = null,
            settingsRepository = SpeciesSettingsRepository(MemoryStore()),
            service = service
        )
        repository.clock = now
        return repository
    }

    @Test fun repeatedReadsHitTheNetworkOnce() = runTest {
        val service = FakeSpeciesService()
        val repository = repository(service)
        val first = repository.fetchDatabase()
        assertNotNull(first)
        assertSame(first, repository.fetchDatabase())
        assertEquals(1, service.calls)
    }

    @Test fun expiredCacheRefetchesOnce() = runTest {
        val service = FakeSpeciesService()
        var now = 0L
        val repository = repository(service, now = { now })
        repository.fetchDatabase()
        now += SpeciesRepository.DATABASE_CACHE_TTL_MILLIS + 1
        repository.fetchDatabase()
        assertEquals(2, service.calls)
    }

    @Test fun offlineServesTheDataStoreCacheAndRemembersIt() = runTest {
        val service = FakeSpeciesService(fail = true)
        val settings = SpeciesSettingsRepository(MemoryStore())
        settings.cacheDatabase("""{"version":7,"species":{}}""", 7)
        val repository = SpeciesRepository(database = null, settingsRepository = settings, service = service)
        assertEquals(7, repository.fetchDatabase()?.version)
        assertEquals(7, repository.fetchDatabase()?.version)
        assertEquals(1, service.calls)
    }

    @Test fun concurrentFirstReadsCollapseIntoOneFetch() = runTest {
        val service = FakeSpeciesService(delayMillis = 50)
        val repository = repository(service)
        val results = (1..5).map { async { repository.fetchDatabase() } }.awaitAll()
        assertEquals(1, service.calls)
        assertTrue(results.all { it === results.first() && it != null })
    }
}
