package com.tamerslab

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.gamesave.*
import com.tamerslab.transfer.data.gameSave.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveImportPreviewPersistenceTest {
    private lateinit var db: AppDatabase
    private val record = ObservedDigimon("main:1", SaveContainer.PARTY, 1, 10, "Kept name", 42, 16,
        123, RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private var observation = SaveObservation("game", "profile", 1, "a".repeat(64), listOf(record),
        listOf(ObservationNotice.OWNERSHIP_RESEARCH_PENDING, ObservationNotice.IDENTITY_RESEARCH_PENDING))
    private lateinit var sources: LinkedGameSaveRepository
    private lateinit var previews: SaveImportPreviewRepository

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sources = LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> observation })
        previews = SaveImportPreviewRepository(db, sources)
    }
    @After fun cleanup() { db.close() }
    private suspend fun source() = sources.link(SaveDocument("provider:slot", "content://provider/slot", "content://provider/slot", "0001.bin"))

    @Test fun previewIsIdempotentPreservesNativeDataAndSurvivesUnlinkWithoutAcquisition() = runBlocking {
        val id = source()
        val first = previews.prepare(id, observation.contentRevision, listOf(record.recordKey))
        val again = previews.prepare(id, observation.contentRevision, listOf(record.recordKey))
        assertEquals(first, again)
        assertEquals("AQID", first.preview.items.single().record.nativeRecordBase64)
        assertFalse(first.preview.canCommit)
        sources.unlink(id)
        assertEquals(first, previews.load(first.draft.draftId))
        for (table in listOf("UserCharacter", "DigimonIndividual", "GameImportItemReceipt")) {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test fun changedRevisionInvalidatesSelectionInsteadOfUsingOldOffsets() = runBlocking {
        val id = source()
        val oldRevision = observation.contentRevision
        observation = observation.copy(contentRevision = "b".repeat(64))
        try {
            previews.prepare(id, oldRevision, listOf(record.recordKey))
            fail("A changed file must require a new roster selection")
        } catch (_: ImportSourceChangedException) { }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM SaveImportDraft").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun failedFreshReadNeverFallsBackToCachedContentForPreview() = runBlocking {
        val id = source()
        sources.refresh(id)
        val failed = LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> throw SecurityException() })
        try {
            SaveImportPreviewRepository(db, failed).prepare(id, observation.contentRevision, listOf(record.recordKey))
            fail("Cached data must not stand in for the required fresh read")
        } catch (_: SecurityException) { }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM SaveImportDraft").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
    }
}
