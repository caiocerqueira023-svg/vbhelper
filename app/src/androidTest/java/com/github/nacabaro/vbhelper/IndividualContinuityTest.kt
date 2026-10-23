package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.personality.*
import com.github.nacabaro.vbhelper.source.StorageRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndividualContinuityTest {
    private lateinit var db: AppDatabase
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).addCallback(IndividualIntegrity.callback).build()
    }
    @After fun cleanup() { db.close() }
    @Test fun importedPersonalityUpgradePreservesTypeAndOriginalTimestamp() = runBlocking {
        db.digimonIndividualDao().insert(DigimonIndividual("kept", 55, originKind = "IMPORTED"))
        db.digimonIndividualDao().upsertPersonality(DigimonPersonalityTraits("kept", DigimonPersonalityType.STRATEGIC, 66, systemVersion = 1))
        val value = StorageRepository(db).getOrCreatePersonalityForIndividual("kept", null, 5)
        assertEquals(DigimonPersonalityType.STRATEGIC, value.personalityType)
        assertEquals(66L, value.generatedAt)
        assertEquals(CURRENT_PERSONALITY_SYSTEM_VERSION, value.systemVersion)
    }

    @Test fun opaqueSourceContainerRoundTripsThroughBoundedChunksWithoutChangingNativeRecords() = runBlocking {
        val original = ByteArray(3_098_176) { (it % 251).toByte() }
        val revision = com.github.nacabaro.vbhelper.companionprotocol.CompanionProtocol.sha256(original)
        val observed = com.github.nacabaro.vbhelper.gamesave.SaveObservation("fixture", "fixture-v1", 1, revision, emptyList(), emptyList())
        val captured = com.github.nacabaro.vbhelper.gamesave.CapturedSave(observed, original.copyOf())
        val repository = com.github.nacabaro.vbhelper.source.gameSave.NativeContainerRepository(db)
        var snapshotId = 0L
        db.withTransaction {
            db.digimonIndividualDao().insert(DigimonIndividual("kept", 55, originKind = "IMPORTED"))
            db.nativeGameArchiveDao().insertBinding(com.github.nacabaro.vbhelper.domain.transfer.ExternalIndividualBinding(
                "binding", "source", "document", "fixture", "fixture-v1", "fixture-id", "123", "kept", 55))
            snapshotId = db.nativeGameArchiveDao().insertSnapshot(com.github.nacabaro.vbhelper.domain.transfer.NativeGameSnapshot(
                individualId = "kept", bindingId = "binding", sourceLabel = "Fixture", contentRevision = revision,
                codecVersion = 1, recordKey = "main:1", recordJson = "{}", nativePersonalityId = 16, mappedPersonality = "COMPASSIONATE", importedAt = 55))
            repository.retain(captured, listOf(snapshotId))
        }
        captured.close()
        assertArrayEquals(original, repository.originalDocument(snapshotId))
        assertTrue(db.transferContinuityDao().chunks(revision).all { it.bytes.size <= 65536 })
        assertTrue(db.transferContinuityDao().history("kept").first().isNotEmpty())
    }

    @Test fun journeyProgressDeduplicatesObservationsAndKeepsZeroTransferEntriesNeutral() = runBlocking {
        db.digimonIndividualDao().insert(DigimonIndividual("journey", 55, originKind = "IMPORTED"))
        val repository = com.github.nacabaro.vbhelper.source.gameSave.UniversalJourneyRepository(db)
        fun vector(value: Long) = com.github.nacabaro.vbhelper.gamesave.ProgressVector.of(mapOf("growth" to value))
        repository.acceptVerifiedEntry("journey", "A", "a-v1", byteArrayOf(1,2,3), vector(1234))
        repository.acceptVerifiedEntry("journey", "B", "b-v1", byteArrayOf(4,5,6), vector(1200))
        assertEquals(vector(100), repository.observeVerifiedProgress("journey", "b-revision-2", "B", "b-v1", 1, byteArrayOf(7,8,9), vector(1300)))
        assertTrue(repository.observeVerifiedProgress("journey", "b-revision-2", "B", "b-v1", 1, byteArrayOf(7,8,9), vector(1300)).isZero)
        val account = repository.synchronizeAppChanges("journey")
        val state = com.google.gson.Gson().fromJson(account.stateJson, com.github.nacabaro.vbhelper.gamesave.UniversalProgressState::class.java)
        assertEquals(vector(100), state.total)
        assertEquals(vector(100), com.github.nacabaro.vbhelper.gamesave.UniversalProgress.pendingFor(state, "A"))
        assertEquals(vector(50), repository.observeVerifiedProgress("journey", "b-revision-2", "A", "a-v1", 1, byteArrayOf(1,2,4), vector(1284)))
    }

    @Test fun representationProjectionDoesNotBecomeEarnedAppTraining() = runBlocking {
        db.digimonIndividualDao().insert(DigimonIndividual("journey", 55, originKind = "IMPORTED"))
        db.openHelper.writableDatabase.execSQL("INSERT INTO AppPartnerProgress(individualId,trainingHp,trainingAp,trainingBp) VALUES ('journey',0,0,0)")
        val repository = com.github.nacabaro.vbhelper.source.gameSave.UniversalJourneyRepository(db)
        repository.projection("journey", "game-b-return") {
            db.openHelper.writableDatabase.execSQL("UPDATE AppPartnerProgress SET trainingHp=100 WHERE individualId='journey'")
        }
        assertTrue(com.google.gson.Gson().fromJson(repository.synchronizeAppChanges("journey").stateJson,
            com.github.nacabaro.vbhelper.gamesave.UniversalProgressState::class.java).total.isZero)
        db.openHelper.writableDatabase.execSQL("UPDATE AppPartnerProgress SET trainingHp=105 WHERE individualId='journey'")
        val state = com.google.gson.Gson().fromJson(repository.synchronizeAppChanges("journey").stateJson,
            com.github.nacabaro.vbhelper.gamesave.UniversalProgressState::class.java)
        assertEquals(5L, state.total.units["training.hp"])
    }

    @Test fun appProgressAdvancesItsOwnAnchorRatherThanBecomingPendingForItself() = runBlocking {
        db.digimonIndividualDao().insert(DigimonIndividual("journey", 55, originKind = "IMPORTED"))
        db.openHelper.writableDatabase.execSQL("INSERT INTO AppPartnerProgress(individualId,trainingHp,trainingAp,trainingBp) VALUES ('journey',0,0,0)")
        val repository = com.github.nacabaro.vbhelper.source.gameSave.UniversalJourneyRepository(db)
        repository.acceptVerifiedEntry("journey", "vbhelper-storage", "app-state-v1", byteArrayOf(1), com.github.nacabaro.vbhelper.gamesave.ProgressVector.of())
        db.openHelper.writableDatabase.execSQL("UPDATE AppPartnerProgress SET trainingHp=5 WHERE individualId='journey'")
        val state = com.google.gson.Gson().fromJson(repository.synchronizeAppChanges("journey").stateJson,
            com.github.nacabaro.vbhelper.gamesave.UniversalProgressState::class.java)
        assertEquals(5L, state.total.units["training.hp"])
        assertTrue(com.github.nacabaro.vbhelper.gamesave.UniversalProgress.pendingFor(state, "vbhelper-storage").isZero)
    }
}
