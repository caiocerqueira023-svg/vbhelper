package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.transfer.NativeCanonicalMapping
import com.github.nacabaro.vbhelper.gamesave.*
import com.github.nacabaro.vbhelper.source.gameSave.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameImportAcquisitionTest {
    private lateinit var db: AppDatabase
    private lateinit var sources: LinkedGameSaveRepository
    private lateinit var previews: SaveImportPreviewRepository
    private lateinit var acquisition: GameImportAcquisitionRepository
    private val first = ObservedDigimon("main:1", SaveContainer.PARTY, 1, 10, "Native name", 42, 16,
        123, RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private val second = first.copy(recordKey = "main:2", identityCandidate = 456, storedName = "Another")
    private var observation = SaveObservation("fixture", "fixture-v1", 1, "a".repeat(64), listOf(first, second),
        listOf(ObservationNotice.SPECIES_MAPPING_PENDING), SaveCapabilities(canImportSnapshot = true))
    private val profile = VerifiedSnapshotImportProfile("fixture", "fixture-v1", 1, "controlled-fixture-id-v1",
        mapOf(16 to DigimonPersonalityType.COMPASSIONATE, 14 to DigimonPersonalityType.FRIENDLY))

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sources = LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> observation })
        previews = SaveImportPreviewRepository(db, sources)
        acquisition = GameImportAcquisitionRepository(db, sources, previews, listOf(profile))
    }
    @After fun cleanup() { db.close() }
    private suspend fun source() = sources.link(SaveDocument("provider:slot", "content://provider/slot", "content://provider/slot", "0001.bin"))
    private suspend fun draft(source: String, vararg records: ObservedDigimon) = previews.prepare(source,
        observation.contentRevision, records.map { it.recordKey }).draft.draftId
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        check(it.moveToFirst()); it.getInt(0)
    }

    @Test fun worldDsRefreshKeepsAppPersonalityAndDistinctDocumentsDoNotMergeLocalIds() = runBlocking {
        val ds = first.copy(recordKey = "copy:1:record:0", personalityId = null, identityCandidate = 0,
            totalExp = null, talentRaw = null, bondRaw = null, nativeIdentity = "save-local:7",
            nativeParameters = NativeStatParameters(100, 110, 20, 25, 30, 30, 30, 30, 25, List(8) { 0L }, 1))
        observation = SaveObservation(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 1, "a".repeat(64),
            listOf(ds), emptyList(), SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true))
        val production = GameImportAcquisitionRepository(db, sources, previews)
        val source = source()
        val receipt = production.commit(draft(source, ds)).receipts.single()
        val personality = requireNotNull(db.digimonIndividualDao().getPersonality(receipt.individualId)).copy(
            personalityType = DigimonPersonalityType.FRIENDLY, generatedAt = 123)
        db.digimonIndividualDao().upsertPersonality(personality)
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(ds.copy(level = 43)))
        val refreshedDraft = draft(source, observation.records.single())
        val review = production.prepareRefresh(refreshedDraft)
        assertFalse(review.items.single().fields.personalityConflict)
        production.commitRefresh(review, review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() }, identityConfirmed = true)
        assertEquals(personality, db.digimonIndividualDao().getPersonality(receipt.individualId))
        assertNull(production.archives.first().single().snapshot.nativePersonalityId)
        assertEquals(1, count("PendingNativeProgress"))
        val unrelated = sources.link(SaveDocument("provider:other-ds", "content://provider/other-ds", "content://provider/other-ds", "Other.sav"))
        val other = production.commit(draft(unrelated, observation.records.single())).receipts.single()
        assertNotEquals(receipt.individualId, other.individualId)
        assertEquals(2, count("DigimonIndividual"))
    }

    @Test fun archiveAcquisitionPreservesNativePersonalityWithoutGameplayOrWatchRows() = runBlocking {
        val source = source()
        val result = acquisition.commit(draft(source, first))
        val id = result.receipts.single().individualId
        assertEquals(1, result.createdCount)
        val individual = requireNotNull(db.digimonIndividualDao().getIndividual(id))
        assertEquals("IMPORTED", individual.originKind)
        assertNull(individual.nickname) // Stored source names are not guessed to be user nicknames.
        assertNull(individual.speciesName)
        assertEquals(DigimonPersonalityType.COMPASSIONATE, db.digimonIndividualDao().getPersonality(id)?.personalityType)
        val archive = acquisition.archives.first().single()
        assertEquals(first, archive.record)
        for (table in listOf("UserCharacter", "VBCharacterData", "BECharacterData", "TransformationHistory")) assertEquals(0, count(table))
        sources.unlink(source)
        assertEquals(id, acquisition.archives.first().single().snapshot.individualId)
        assertEquals(1, count("GameImportItemReceipt"))
    }

    @Test fun retriesAndLaterSelectionsDoNotDuplicateIndividuals() = runBlocking {
        val source = source()
        val draft = draft(source, first)
        val initial = acquisition.commit(draft)
        val retry = acquisition.commit(draft)
        assertEquals(0, retry.createdCount)
        assertEquals(initial.receipts, retry.receipts)
        acquisition.commit(draft(source, second))
        assertEquals(2, count("DigimonIndividual"))
        assertEquals(2, count("NativeGameSnapshot"))
        assertEquals(2, count("GameImportItemReceipt"))
    }

    @Test fun unlinkAndReselectingTheSameDocumentRequiresIdentityReviewInsteadOfCloning() = runBlocking {
        val source = source()
        acquisition.commit(draft(source, first))
        sources.unlink(source)
        val relinked = source()
        assertNotEquals(source, relinked)
        try { acquisition.commit(draft(relinked, first)); fail("Reselecting the same document must not bypass the binding") }
        catch (_: ImportIdentityReviewRequiredException) { }
        assertEquals(1, count("DigimonIndividual"))
    }

    @Test fun concurrentDoubleConfirmationAcquiresOnce() = runBlocking {
        val draft = draft(source(), first)
        val a = async { acquisition.commit(draft) }
        val b = async { acquisition.commit(draft) }
        assertEquals(1, a.await().createdCount + b.await().createdCount)
        assertEquals(1, count("DigimonIndividual"))
    }

    @Test fun staleSourceInvalidatesCommit() = runBlocking {
        val draft = draft(source(), first)
        observation = observation.copy(contentRevision = "b".repeat(64))
        try { acquisition.commit(draft); fail("Changed source must require a new preview") }
        catch (_: ImportSourceChangedException) { }
        assertEquals(0, count("DigimonIndividual"))
    }

    @Test fun unregisteredProfilesCannotAcquireEvenWhenDraftLooksReady() = runBlocking {
        val draft = draft(source(), first)
        try { GameImportAcquisitionRepository(db, sources, previews).commit(draft); fail("Unverified profile") }
        catch (_: IllegalStateException) { }
        assertEquals(0, count("DigimonIndividual"))
        assertEquals(0, count("GameImportItemReceipt"))
    }

    @Test fun identityCollisionRollsBackTheEntireSelectedBatch() = runBlocking {
        val draft = draft(source(), first, second)
        val collisions = GameImportAcquisitionRepository(db, sources, previews, listOf(profile), newIndividualId = { "collision" })
        try { collisions.commit(draft); fail("A failed second item must roll back the first") }
        catch (_: IllegalStateException) { }
        for (table in listOf("DigimonIndividual", "DigimonPersonalityTraits", "ExternalIndividualBinding", "NativeGameSnapshot", "GameImportItemReceipt")) {
            assertEquals(table, 0, count(table))
        }
    }

    @Test fun laterRevisionOfBoundIdentityRequiresReviewAndPreservesAppPersonalityAndName() = runBlocking {
        val source = source()
        val id = acquisition.commit(draft(source, first)).receipts.single().individualId
        db.openHelper.writableDatabase.execSQL("UPDATE DigimonIndividual SET nickname='App name' WHERE individualId=?", arrayOf(id))
        db.digimonIndividualDao().upsertPersonality(requireNotNull(db.digimonIndividualDao().getPersonality(id)).copy(personalityType = DigimonPersonalityType.FRIENDLY))
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(first.copy(storedName = "Game changed"), second))
        try { acquisition.commit(draft(source, first)); fail("Native refresh must not create a clone or overwrite app values") }
        catch (_: ImportIdentityReviewRequiredException) { }
        assertEquals(1, count("DigimonIndividual"))
        assertEquals(1, count("NativeGameSnapshot"))
        assertEquals("App name", db.digimonIndividualDao().getIndividual(id)?.nickname)
        assertEquals(DigimonPersonalityType.FRIENDLY, db.digimonIndividualDao().getPersonality(id)?.personalityType)
    }

    @Test fun reviewedSourceOnlyRefreshPreservesIdentityAndArchivesThePriorNativeBaseline() = runBlocking {
        val source = source()
        val id = acquisition.commit(draft(source, first)).receipts.single().individualId
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(first.copy(
            storedName = "New native name", personalityId = 14, nativeSpeciesId = 11, level = 43)))
        val review = acquisition.prepareRefresh(draft(source, first))
        val choices = review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() }
        val result = acquisition.commitRefresh(review, choices)
        assertEquals(1, result.refreshedCount)
        assertEquals(id, result.receipts.single().individualId)
        assertEquals(1, count("DigimonIndividual"))
        assertEquals(2, count("NativeGameSnapshot"))
        assertNull(db.digimonIndividualDao().getIndividual(id)?.nickname)
        assertEquals(DigimonPersonalityType.FRIENDLY, db.digimonIndividualDao().getPersonality(id)?.personalityType)
        assertNotNull(acquisition.archives.first().single().snapshot.reviewAuditJson)
    }

    @Test fun conflictingRefreshRequiresChoicesAndKeepingAppValuesPreservesHistoryAndProgress() = runBlocking {
        val source = source()
        val id = acquisition.commit(draft(source, first)).receipts.single().individualId
        db.openHelper.writableDatabase.execSQL("UPDATE DigimonIndividual SET nickname='App name', lastDiaryEntryAt=44, lastCelebratedWinsMilestone=7 WHERE individualId=?", arrayOf(id))
        db.openHelper.writableDatabase.execSQL("INSERT INTO ChatMessageEntity(individualId,role,content,timestamp,isRead) VALUES (?,'user','Keep chat',1,1)", arrayOf(id))
        db.digimonIndividualDao().upsertPersonality(requireNotNull(db.digimonIndividualDao().getPersonality(id)).copy(personalityType = DigimonPersonalityType.STRATEGIC))
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(first.copy(storedName = "Game name", personalityId = 14)))
        val review = acquisition.prepareRefresh(draft(source, first))
        assertTrue(review.items.single().fields.nameConflict)
        assertTrue(review.items.single().fields.personalityConflict)
        try { acquisition.commitRefresh(review, mapOf(first.recordKey to RefreshChoices(null, null))); fail("Conflicts require choices") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, count("NativeGameSnapshot"))
        acquisition.commitRefresh(review, mapOf(first.recordKey to RefreshChoices(RefreshChoice.KEEP_APP, RefreshChoice.KEEP_APP)))
        val individual = requireNotNull(db.digimonIndividualDao().getIndividual(id))
        assertEquals("App name", individual.nickname)
        assertEquals(44L, individual.lastDiaryEntryAt)
        assertEquals(7, individual.lastCelebratedWinsMilestone)
        assertEquals(1, count("ChatMessageEntity"))
        assertEquals(DigimonPersonalityType.STRATEGIC, db.digimonIndividualDao().getPersonality(id)?.personalityType)
        assertEquals("Game name", acquisition.archives.first().single().record?.storedName)
        assertEquals("App name", acquisition.archives.first().single().nickname)
    }

    @Test fun appEditAfterReviewInvalidatesConfirmationWithoutOverwritingTheEdit() = runBlocking {
        val source = source()
        val id = acquisition.commit(draft(source, first)).receipts.single().individualId
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(first.copy(personalityId = 14)))
        val review = acquisition.prepareRefresh(draft(source, first))
        db.openHelper.writableDatabase.execSQL("UPDATE DigimonIndividual SET nickname='Changed during review' WHERE individualId=?", arrayOf(id))
        try { acquisition.commitRefresh(review, review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() }); fail("App edits invalidate review") }
        catch (_: ImportPreviewInvalidException) { }
        assertEquals(1, count("NativeGameSnapshot"))
        assertEquals("Changed during review", db.digimonIndividualDao().getIndividual(id)?.nickname)
    }

    @Test fun repeatedRefreshConfirmationIsIdempotentAndDoesNotReapplyPersonality() = runBlocking {
        val source = source()
        val id = acquisition.commit(draft(source, first)).receipts.single().individualId
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(first.copy(personalityId = 14)))
        val review = acquisition.prepareRefresh(draft(source, first))
        val choices = review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() }
        acquisition.commitRefresh(review, choices)
        db.digimonIndividualDao().upsertPersonality(requireNotNull(db.digimonIndividualDao().getPersonality(id)).copy(personalityType = DigimonPersonalityType.STRATEGIC))
        assertEquals(0, acquisition.commitRefresh(review, choices).refreshedCount)
        assertEquals(2, count("NativeGameSnapshot"))
        assertEquals(DigimonPersonalityType.STRATEGIC, db.digimonIndividualDao().getPersonality(id)?.personalityType)
    }

    @Test fun sourceChangeAfterRefreshReviewInvalidatesTheEntireOperation() = runBlocking {
        val source = source()
        acquisition.commit(draft(source, first))
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(first.copy(personalityId = 14)))
        val review = acquisition.prepareRefresh(draft(source, first))
        observation = observation.copy(contentRevision = "c".repeat(64))
        try { acquisition.commitRefresh(review, review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() }); fail("Source changed") }
        catch (_: ImportSourceChangedException) { }
        assertEquals(1, count("NativeGameSnapshot"))
        assertEquals(1, count("GameImportItemReceipt"))
    }

    @Test fun provisionalCopiesRequireIdentityReviewAndDoNotCloneAcrossSlots() = runBlocking {
        observation = observation.copy(capabilities = SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true),
            notices = listOf(ObservationNotice.IDENTITY_RESEARCH_PENDING, ObservationNotice.SPECIES_MAPPING_PENDING))
        val provisional = GameImportAcquisitionRepository(db, sources, previews, listOf(VerifiedSnapshotImportProfile(
            "fixture", "fixture-v1", 1, "candidate-main", mapOf(16 to DigimonPersonalityType.COMPASSIONATE),
            nativeLevelRange = 1..99, provisionalIdentity = true, allowedSnapshotContainers = setOf(SaveContainer.PARTY, SaveContainer.BANK))))
        val source = source()
        val initial = provisional.commit(draft(source, first))
        val individual = initial.receipts.single().individualId
        val saved = provisional.archives.first().single()
        assertEquals("PROVISIONAL", db.nativeMaterializationDao().binding(saved.snapshot.bindingId)?.identityConfidence)
        val anotherSlot = sources.link(SaveDocument("provider:other-slot", "content://provider/other-slot", "content://provider/other-slot", "0002.bin"))
        val reviewDraft = draft(anotherSlot, first)
        try { provisional.commit(reviewDraft); fail("Another slot must not silently create another individual") }
        catch (_: ImportIdentityReviewRequiredException) { }
        val review = provisional.prepareRefresh(reviewDraft)
        assertTrue(review.requiresIdentityConfirmation)
        val choices = review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() }
        try { provisional.commitRefresh(review, choices); fail("Candidate identity needs explicit confirmation") }
        catch (_: IllegalArgumentException) { }
        val refreshed = provisional.commitRefresh(review, choices, identityConfirmed = true)
        assertEquals(individual, refreshed.receipts.single().individualId)
        assertEquals(1, count("DigimonIndividual"))
        assertEquals(0, count("UserCharacter"))
        assertTrue(requireNotNull(provisional.archives.first().single().snapshot.reviewAuditJson).contains("\"identityContinuityConfirmed\":true"))
    }

    @Test fun aReviewedSpeciesMappingCarriesAcrossRefreshUnlessTheSpeciesChanged() = runBlocking {
        val ds = first.copy(recordKey = "copy:1:record:0", personalityId = null, identityCandidate = 0,
            totalExp = null, talentRaw = null, bondRaw = null, nativeIdentity = "save-local:7",
            nativeParameters = NativeStatParameters(100, 110, 20, 25, 30, 30, 30, 30, 25, List(8) { 0L }, 1))
        observation = SaveObservation(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 1, "a".repeat(64),
            listOf(ds), emptyList(), SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true))
        val production = GameImportAcquisitionRepository(db, sources, previews)
        val source = source()
        val receipt = production.commit(draft(source, ds)).receipts.single()
        val firstSnapshot = requireNotNull(db.nativeMaterializationDao().latestSnapshotId(receipt.individualId))
        db.nativeMaterializationDao().saveMapping(NativeCanonicalMapping(firstSnapshot, ds.nativeSpeciesId,
            "digimon.fixture", "USER_CONFIRMED", 1))
        observation = observation.copy(contentRevision = "b".repeat(64), records = listOf(ds.copy(level = 43)))
        val review = production.prepareRefresh(draft(source, observation.records.single()))
        production.commitRefresh(review, review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() },
            identityConfirmed = true)
        val refreshed = requireNotNull(db.nativeMaterializationDao().latestSnapshotId(receipt.individualId))
        assertNotEquals(firstSnapshot, refreshed)
        assertEquals(NativeCanonicalMapping(refreshed, ds.nativeSpeciesId, "digimon.fixture", "USER_CONFIRMED", 1),
            db.nativeMaterializationDao().mapping(refreshed))
        observation = observation.copy(contentRevision = "c".repeat(64),
            records = listOf(ds.copy(level = 43, nativeSpeciesId = ds.nativeSpeciesId + 1)))
        val evolved = production.prepareRefresh(draft(source, observation.records.single()))
        production.commitRefresh(evolved, evolved.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() },
            identityConfirmed = true)
        val afterEvolution = requireNotNull(db.nativeMaterializationDao().latestSnapshotId(receipt.individualId))
        assertNotEquals(refreshed, afterEvolution)
        assertNull(db.nativeMaterializationDao().mapping(afterEvolution))
    }
}
