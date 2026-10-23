package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.gamesave.*
import com.github.nacabaro.vbhelper.source.gameSave.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real byte-edit/journal/Room acquisition path against synthetic data, not retail-game acceptance. */
@RunWith(AndroidJUnit4::class)
class ExperimentalWorldDsMoveTest {
    private lateinit var db: AppDatabase
    private lateinit var directory: File
    private lateinit var sendDirectory: File
    private lateinit var sources: LinkedGameSaveRepository
    private lateinit var moves: ExperimentalWorldDsMoveRepository
    private lateinit var sends: ExperimentalWorldDsSendRepository
    private lateinit var acquisition: GameImportAcquisitionRepository
    private lateinit var sourceId: String
    private var bytes = byteArrayOf()
    private var writes = 0
    private var corruptWrite = false
    private var writable = true
    private var attempted = byteArrayOf()

    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        directory = File(context.cacheDir, "move-test-${UUID.randomUUID()}")
        sendDirectory = File(context.cacheDir, "send-test-${UUID.randomUUID()}")
        bytes = fixture()
        sources = LinkedGameSaveRepository(db, object : SaveDocumentAccess {
            override suspend fun read(uri: String, registry: SaveReaderRegistry) = WorldDsSaveReader().read(bytes)
            override suspend fun capture(uri: String, registry: SaveReaderRegistry) =
                CapturedSave(WorldDsSaveReader().read(bytes), bytes.copyOf())
        })
        val writer = object : SaveDocumentWrite {
            override fun canWrite(documentUri: String, grantUri: String) = writable
            override suspend fun write(documentUri: String, bytes: ByteArray) {
                writes++
                attempted = bytes.copyOf()
                this@ExperimentalWorldDsMoveTest.bytes = bytes.copyOf().also { if (corruptWrite) it[0x1F0] = 42 }
            }
        }
        sourceId = sources.link(SaveDocument("fixture:world-ds", "content://fixture/save", "content://fixture/save", "World DS.sav"))
        sources.refresh(sourceId)
        moves = ExperimentalWorldDsMoveRepository(db, sources, writer, directory)
        sends = ExperimentalWorldDsSendRepository(db, sources, writer, sendDirectory)
        acquisition = GameImportAcquisitionRepository(db, sources, SaveImportPreviewRepository(db, sources))
    }

    @After fun cleanup() { db.close(); directory.deleteRecursively(); sendDirectory.deleteRecursively() }

    @Test fun actualRemovalAcquiresTheExactNativeIndividualAndNicknameOnce() = runBlocking {
        val original = bytes.copyOf()
        val before = sources.readFresh(sourceId)
        val target = before.records.first()
        val result = moves.move(sourceId, before.contentRevision, target.recordKey)
        assertEquals(1, writes)
        assertEquals(1, WorldDsSaveReader().read(bytes).records.size)
        val archive = acquisition.archives.first().single()
        assertEquals(result.individualId, archive.snapshot.individualId)
        assertEquals(target, archive.record)
        assertEquals(target.storedName, archive.nickname)
        assertEquals(target.nativeParameters, archive.record?.nativeParameters)
        assertArrayEquals(original, moves.backupBytes(moves.backups().single().operationId))
        assertArrayEquals(original, NativeContainerRepository(db).originalDocument(archive.snapshot.id))
        moves.recover(sourceId)
        assertEquals(1, writes)
        assertEquals(1, acquisition.archives.first().size)
    }

    @Test fun readbackMismatchRetainsRecoveryDataAndDoesNotAcquireUntilCandidateIsPersisted() = runBlocking {
        val original = bytes.copyOf()
        val before = sources.readFresh(sourceId)
        corruptWrite = true
        try { moves.move(sourceId, before.contentRevision, before.records.first().recordKey); fail("Readback must fail") }
        catch (_: WorldDsMoveRecoveryRequired) { }
        assertTrue(acquisition.archives.first().isEmpty())
        assertArrayEquals(original, moves.backupBytes(moves.backups().single().operationId))
        try { moves.recover(sourceId); fail("External mismatch requires reconciliation") }
        catch (_: WorldDsMoveRecoveryRequired) { }
        assertEquals(1, writes)
        bytes = attempted.copyOf() // Simulate the provider finishing the already-staged write.
        moves.recover(sourceId)
        moves.recover(sourceId)
        assertEquals(1, writes)
        assertEquals(1, acquisition.archives.first().size)
    }

    @Test fun staleSourceAndReadOnlyAccessNeverWrite() = runBlocking {
        val before = sources.readFresh(sourceId)
        writable = false
        val source = sources.sources.first().single()
        assertEquals(WorldDsMoveBlocker.WRITE_ACCESS, moves.blocker(source, before.records.first()))
        try { moves.move(sourceId, before.contentRevision, before.records.first().recordKey); fail("Read-only") }
        catch (_: IllegalArgumentException) { }
        writable = true
        try { moves.move(sourceId, "stale", before.records.first().recordKey); fail("Stale") }
        catch (_: ImportSourceChangedException) { }
        assertEquals(0, writes)
        assertTrue(acquisition.archives.first().isEmpty())
        assertTrue(moves.backups().isEmpty())
    }

    @Test fun lastPartyMemberIsBlockedAfterTheFirstRealMove() = runBlocking {
        val before = sources.readFresh(sourceId)
        moves.move(sourceId, before.contentRevision, before.records.first().recordKey)
        val remaining = sources.readFresh(sourceId)
        assertEquals(WorldDsMoveBlocker.LAST_PARTY_MEMBER,
            moves.blocker(sources.sources.first().single(), remaining.records.single()))
        val unchanged = bytes.copyOf()
        try { moves.move(sourceId, remaining.contentRevision, remaining.records.single().recordKey); fail("Last member") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, writes)
        assertArrayEquals(unchanged, bytes)
        assertEquals(1, acquisition.archives.first().size)
    }

    @Test fun movingAnAlreadyCopiedIndividualReusesItsIdentityAndOriginalArchive() = runBlocking {
        val before = sources.readFresh(sourceId)
        val previews = SaveImportPreviewRepository(db, sources)
        val saved = previews.prepare(sourceId, before.contentRevision, listOf(before.records.first().recordKey))
        val copied = acquisition.commit(saved.draft.draftId)
        val originalArchive = acquisition.archives.first().single()
        val result = moves.move(sourceId, before.contentRevision, before.records.first().recordKey)
        assertEquals(copied.receipts.single().individualId, result.individualId)
        val after = acquisition.archives.first().single()
        assertEquals(originalArchive.snapshot.id, after.snapshot.id)
        assertEquals(originalArchive.record, after.record)
        assertEquals(before.records.first().storedName, after.nickname)
        assertEquals(1, writes)
    }

    @Test fun movingAnImportedIndividualAfterItChangedRefreshesItsSnapshot() = runBlocking {
        val before = sources.readFresh(sourceId)
        val previews = SaveImportPreviewRepository(db, sources)
        val saved = previews.prepare(sourceId, before.contentRevision, listOf(before.records.first().recordKey))
        val copied = acquisition.commit(saved.draft.draftId)
        val originalArchive = acquisition.archives.first().single()
        bytes[0x234 + 8] = 0x64 // The native record is renamed after the import snapshot was taken.
        sources.refresh(sourceId)
        val changed = sources.readFresh(sourceId)
        val target = changed.records.first { it.recordKey == "copy:1:record:0" }
        assertNotEquals(originalArchive.record?.nativeRecordBase64, target.nativeRecordBase64)
        val result = moves.move(sourceId, changed.contentRevision, target.recordKey)
        assertEquals(copied.receipts.single().individualId, result.individualId)
        assertEquals(1, writes)
        val after = acquisition.archives.first().single()
        assertEquals(copied.receipts.single().individualId, after.snapshot.individualId)
        assertNotEquals(originalArchive.snapshot.id, after.snapshot.id)
        assertEquals(target.nativeRecordBase64, after.record?.nativeRecordBase64)
        assertEquals(target.storedName, after.record?.storedName)
    }

    @Test fun aReviewedConsistentSaveWritesEveryPopulatedCopyAtOnce() = runBlocking {
        val single = bytes.copyOf()
        bytes = ByteArray(65536) { 0xFF.toByte() }.also { data ->
            val copy = single.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE)
            copy.copyInto(data, 0)
            copy.copyInto(data, WorldDsSaveReader.COPY_STRIDE)
        }
        sources.refresh(sourceId)
        val raw = sources.readFresh(sourceId)
        assertEquals(4, raw.records.size)
        assertEquals(null, moves.blocker(sources.sources.first().single(), raw.records.first { it.recordKey == "copy:1:record:0" }))
        sources.selectWorldDsCopy(sourceId, raw.contentRevision, 1)
        val reviewed = sources.readFresh(sourceId)
        val chosen = reviewed.records.single { it.recordKey == "copy:1:record:0" }
        assertEquals(null, moves.blocker(sources.sources.first().single(), chosen))
        val result = moves.move(sourceId, reviewed.contentRevision, "copy:1:record:0")
        assertEquals(1, writes)
        for (base in listOf(0, WorldDsSaveReader.COPY_STRIDE)) {
            assertTrue(bytes.copyOfRange(base + 0x234, base + 0x3B4).all { it == 0.toByte() })
            assertEquals(8L, u32(bytes, base + 0x20C))
            assertTrue(bytes.copyOfRange(base + 0x210, base + 0x234).all { it == 0.toByte() })
        }
        val after = acquisition.archives.first().single()
        assertEquals(result.individualId, after.snapshot.individualId)
        assertEquals(chosen, after.record)
    }

    @Test fun divergingCopiesRequireACopyReviewAndThenMoveTheReviewedOne() = runBlocking {
        val single = bytes.copyOf()
        bytes = ByteArray(65536) { 0xFF.toByte() }.also { data ->
            val copy = single.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE)
            copy.copyInto(data, 0)
            copy.copyInto(data, WorldDsSaveReader.COPY_STRIDE)
            data[WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x30] = 0x77
        }
        sources.refresh(sourceId)
        val raw = sources.readFresh(sourceId)
        val rawChosen = raw.records.single { it.recordKey == "copy:1:record:0" }
        assertEquals(WorldDsMoveBlocker.COPY_DIVERGENCE, moves.blocker(sources.sources.first().single(), rawChosen))
        sources.selectWorldDsCopy(sourceId, raw.contentRevision, 1)
        val reviewed = sources.readFresh(sourceId)
        val chosen = reviewed.records.single { it.recordKey == "copy:1:record:0" }
        assertNull(moves.blocker(sources.sources.first().single(), chosen))
        moves.move(sourceId, reviewed.contentRevision, "copy:1:record:0")
        assertEquals(1, writes)
    }

    @Test fun newestValidCopyAutomaticallyMovesDespiteAnOlderDivergence() = runBlocking {
        val single = bytes.copyOf()
        bytes = ByteArray(65536) { 0xFF.toByte() }.also { data ->
            val copy = single.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE)
            copy.copyInto(data, 0)
            copy.copyInto(data, WorldDsSaveReader.COPY_STRIDE)
            put(data, 0x18, 10); put(data, WorldDsSaveReader.COPY_STRIDE + 0x18, 11)
            data[0x234 + 0x30] = 0x77
            seal(data, 0); seal(data, WorldDsSaveReader.COPY_STRIDE)
        }
        sources.refresh(sourceId)
        val raw = sources.readFresh(sourceId)
        val newest = raw.records.single { it.recordKey == "copy:2:record:0" }
        assertEquals(2, newest.nativeParameters?.saveBlock)
        assertNull(moves.blocker(sources.sources.first().single(), newest))
        moves.move(sourceId, raw.contentRevision, "copy:1:record:0")
        assertEquals(1, writes)
        assertTrue(WorldDsSaveReader().read(bytes).records.none { it.nativeIdentity == "save-local:7" })
    }

    @Test fun anIndividualOnlyInAnOlderCopyBlocksWithItsOwnReason() = runBlocking {
        val single = bytes.copyOf()
        bytes = ByteArray(65536) { 0xFF.toByte() }.also { data ->
            val copy = single.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE)
            copy.copyInto(data, 0)
            copy.copyInto(data, WorldDsSaveReader.COPY_STRIDE)
            put(data, 0x18, 10); put(data, WorldDsSaveReader.COPY_STRIDE + 0x18, 11)
            put(data, WorldDsSaveReader.COPY_STRIDE + 0x210, 0)
            put(data, WorldDsSaveReader.COPY_STRIDE + 0x234 + 0x180, 0)
            seal(data, 0); seal(data, WorldDsSaveReader.COPY_STRIDE)
        }
        sources.refresh(sourceId)
        val raw = sources.readFresh(sourceId)
        val older = raw.records.single { it.recordKey == "copy:1:record:1" }
        assertEquals("save-local:8", older.nativeIdentity)
        assertEquals(WorldDsMoveBlocker.ONLY_IN_OLDER_COPY, moves.blocker(sources.sources.first().single(), older))
    }

    @Test fun sendReturnsTheRetainedRecordAndMoveBackClearsAwayState() = runBlocking {
        val before = sources.readFresh(sourceId)
        val moved = moves.move(sourceId, before.contentRevision, before.records.first().recordKey)
        val afterMove = sources.readFresh(sourceId)
        val retained = requireNotNull(sends.retainedRecord(moved.individualId))
        assertNull(sends.awayBlocker(moved.individualId))
        assertNull(sends.blocker(sources.sources.first().single(), moved.individualId, retained))
        sends.send(moved.individualId, sourceId, afterMove.contentRevision)
        assertEquals(2, writes)
        val afterSend = sources.readFresh(sourceId)
        assertEquals(2, afterSend.records.size)
        assertEquals(WorldDsSendBlocker.ALREADY_AWAY, sends.awayBlocker(moved.individualId))
        assertTrue(acquisition.archives.first().isEmpty())
        sends.send(moved.individualId, sourceId, afterSend.contentRevision) // Retry is idempotent.
        assertEquals(2, writes)
        val returned = afterSend.records.single { it.nativeIdentity == "save-local:7" }
        val back = moves.move(sourceId, afterSend.contentRevision, returned.recordKey)
        assertEquals(moved.individualId, back.individualId)
        assertEquals(3, writes)
        assertEquals(1, WorldDsSaveReader().read(bytes).records.size)
        assertNull(sends.awayBlocker(moved.individualId))
        assertEquals(moved.individualId, acquisition.archives.first().single().snapshot.individualId)
    }

    @Test fun sendReadbackMismatchRetainsRecoveryDataUntilCandidateIsPersisted() = runBlocking {
        val before = sources.readFresh(sourceId)
        val moved = moves.move(sourceId, before.contentRevision, before.records.first().recordKey)
        val afterMove = sources.readFresh(sourceId)
        corruptWrite = true
        try { sends.send(moved.individualId, sourceId, afterMove.contentRevision); fail("Readback must fail") }
        catch (_: WorldDsSendRecoveryRequired) { }
        assertEquals(2, writes)
        assertNull(sends.awayBlocker(moved.individualId))
        assertTrue(acquisition.archives.first().isNotEmpty())
        try { sends.recover(sourceId); fail("External mismatch requires reconciliation") }
        catch (_: WorldDsSendRecoveryRequired) { }
        bytes = attempted.copyOf() // Simulate the provider finishing the already-staged write.
        sends.recover(sourceId)
        sends.recover(sourceId)
        assertEquals(2, writes)
        assertEquals(WorldDsSendBlocker.ALREADY_AWAY, sends.awayBlocker(moved.individualId))
        assertTrue(acquisition.archives.first().isEmpty())
    }

    @Test fun admissionMovesAStorageRecordIntoThePartyWithoutAcquiringIt() = runBlocking {
        repeat(4) { bytes[0x210 + it] = 0 } // Unreference record slot 1 without clearing its bytes.
        sources.refresh(sourceId)
        val observation = sources.readFresh(sourceId)
        val storage = observation.records.single { it.recordKey == "copy:1:record:1" }
        assertEquals(SaveContainer.UNASSIGNED, storage.container)
        assertNull(moves.admissionBlocker(sources.sources.first().single(), storage))
        val recordBytes = storage.nativeRecordBase64
        moves.admit(sourceId, observation.contentRevision, storage.recordKey)
        assertEquals(1, writes)
        val after = sources.readFresh(sourceId)
        assertEquals(2, after.records.count { it.container == SaveContainer.PARTY })
        assertEquals(7L, u32(bytes, 0x20C))
        assertEquals(8L, u32(bytes, 0x210))
        assertEquals(recordBytes, after.records.single { it.recordKey == "copy:1:record:1" }.nativeRecordBase64)
        assertTrue(acquisition.archives.first().isEmpty())
        assertTrue(moves.backups().isEmpty())
    }

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, i -> value or ((bytes[at + i].toLong() and 255) shl (8 * i)) }

    private fun put(data: ByteArray, at: Int, value: Long, width: Int = 4) {
        repeat(width) { data[at + it] = (value ushr (it * 8)).toByte() }
    }

    private fun seal(data: ByteArray, base: Int) {
        val digest = java.security.MessageDigest.getInstance("MD5")
        digest.update(ByteArray(16))
        digest.update(data, base + 16, WorldDsSaveReader.SAVE_BYTES - 16)
        digest.digest().copyInto(data, base)
    }

    private fun fixture(): ByteArray = ByteArray(65536) { 0xFF.toByte() }.also { data ->
        fun put(at: Int, value: Long, width: Int = 4) { repeat(width) { data[at + it] = (value ushr (it * 8)).toByte() } }
        data.fill(0, 0, 0x7DC0)
        repeat(16) { data[it] = (it + 1).toByte() }
        put(0x208, 10)
        repeat(2) { index ->
            val at = 0x234 + index * 0x180
            put(0x20C + index * 4, 7L + index)
            put(at, 7L + index); put(at + 4, 20)
            put(at + 8, 0x63, 2); put(at + 10, 0xFFFF, 2)
            put(at + 0x3C, 100); put(at + 0x40, 110)
            listOf(0x44, 0x46, 0x48, 0x4A, 0x4C, 0x4E).forEach { put(at + it, 20, 2) }
            put(at + 0x52, 25, 2); put(at + 0x74, 3, 2)
            repeat(8) { put(at + 0x78 + it * 4, (it * 11).toLong()) }
        }
    }
}
