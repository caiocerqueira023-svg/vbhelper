package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.gamesave.SaveObservation
import com.github.nacabaro.vbhelper.source.gameSave.LinkedGameSaveRepository
import com.github.nacabaro.vbhelper.source.gameSave.SaveDocument
import com.github.nacabaro.vbhelper.source.gameSave.SaveDocumentAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LinkedGameSavePersistenceTest {
    private lateinit var db: AppDatabase
    private val observation = SaveObservation("game", "profile", 1, "a".repeat(64), emptyList(), emptyList())
    private val document = SaveDocument("provider:slot", "content://provider/slot", "content://provider/slot", "0001.bin")

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun cleanup() { db.close() }

    private fun repository(read: suspend () -> SaveObservation) = LinkedGameSaveRepository(db,
        SaveDocumentAccess { _, _ -> read() })

    @Test fun failureKeepsCachedRosterAndBrowsingNeverCreatesPartners() = runBlocking {
        var fail = false
        val repository = repository { if (fail) throw SecurityException() else observation }
        val id = repository.link(document)
        repository.refresh(id)
        fail = true
        repository.refresh(id)
        val view = repository.sources.first().single()
        assertEquals(observation, view.observation)
        assertEquals("PERMISSION", view.source.lastError)
        assertNotNull(view.source.lastSuccessAt)
        for (table in listOf("UserCharacter", "DigimonIndividual", "TransformationHistory")) {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test fun overlappingRefreshesAreDeduplicatedAndExposeBusyState() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val repository = repository { calls.incrementAndGet(); started.complete(Unit); release.await(); observation }
        val id = repository.link(document)
        val first = launch { repository.refresh(id) }
        started.await()
        assertTrue(id in repository.refreshing.value)
        try {
            repository.refresh(id)
            assertEquals(1, calls.get())
        } finally { release.complete(Unit) }
        first.join()
        assertTrue(repository.refreshing.value.isEmpty())
    }

    @Test fun unlinkDuringRefreshCannotResurrectSourceOrSnapshot() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = repository { started.complete(Unit); release.await(); observation }
        val id = repository.link(document)
        val job = launch { repository.refresh(id) }
        started.await()
        repository.unlink(id)
        release.complete(Unit)
        job.join()
        assertTrue(repository.sources.first().isEmpty())
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM LinkedSaveObservation").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun cancelledRefreshClearsBusyStateAndRetainsLastGoodObservation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var block = false
        val repository = repository { if (block) { started.complete(Unit); awaitCancellation() } else observation }
        val id = repository.link(document)
        repository.refresh(id)
        block = true
        val job = launch { repository.refresh(id) }
        started.await()
        job.cancelAndJoin()
        assertTrue(repository.refreshing.value.isEmpty())
        assertEquals(observation, repository.sources.first().single().observation)
    }

    @Test fun relinkingPreservesSourceIdentityAndLabelButRejectsAnotherDocument() = runBlocking {
        val repository = repository { observation }
        val id = repository.link(document)
        repository.rename(id, "Main playthrough")
        val newAccess = document.copy(uri = "content://provider/tree/document/slot", grantUri = "content://provider/tree")
        assertEquals(id, repository.link(newAccess))
        val source = repository.sources.first().single().source
        assertEquals("Main playthrough", source.label)
        assertEquals(newAccess.uri, source.documentUri)
        try {
            repository.relink(id, document.copy(key = "provider:another"))
            fail("A different document must be linked as a distinct source")
        } catch (_: IllegalArgumentException) { }
        assertEquals(newAccess.uri, repository.sources.first().single().source.documentUri)
    }

    @Test fun explicitUnlinkSuppressionWinsAnOverlappingDiscovery() = runBlocking {
        val guarded = LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> observation }, allowDiscovered = { false })
        try { guarded.link(document, discovered = true); fail("Explicit unlink must suppress automatic discovery") }
        catch (_: com.github.nacabaro.vbhelper.source.gameSave.SaveDiscoverySuppressedException) { }
        assertTrue(guarded.sources.first().isEmpty())
        assertNotNull(guarded.link(document))
    }

    @Test fun slowDiscoveryDoesNotDelayAlreadyLinkedSourceRefresh() = runBlocking {
        val scanning = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val read = CompletableDeferred<Unit>()
        val guarded = LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> read.complete(Unit); observation },
            beforeRefresh = { scanning.complete(Unit); release.await() })
        guarded.link(document)
        val job = launch { guarded.refreshAll() }
        scanning.await()
        try { kotlinx.coroutines.withTimeout(2000) { read.await() } } finally { release.complete(Unit) }
        job.join()
        assertEquals(observation, guarded.sources.first().single().observation)
    }
}
