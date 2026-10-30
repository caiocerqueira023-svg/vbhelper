package com.github.nacabaro.vbhelper

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.characters.Sprite
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.transfer.GameImportItemReceipt
import com.github.nacabaro.vbhelper.domain.transfer.NativeGameSnapshot
import com.github.nacabaro.vbhelper.gamesave.*
import com.github.nacabaro.vbhelper.source.SpritePreferenceRepository
import com.github.nacabaro.vbhelper.source.gameSave.*
import com.github.nacabaro.vbhelper.species.*
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeMaterializationTest {
    private lateinit var db: AppDatabase
    private lateinit var graphics: SpritePreferenceRepository
    private lateinit var acquisition: GameImportAcquisitionRepository
    private lateinit var repository: NativeMaterializationRepository
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("native-materialization-test", Context.MODE_PRIVATE)
    private val catalog = CanonicalSpeciesCatalog(CanonicalSpeciesDatabaseDto(species = listOf(
        CanonicalSpeciesDto("digimon.agumon", "Agumon", level = "Child", dotSprites = DotSpriteAssociationDto("dot", listOf("dot"))),
        CanonicalSpeciesDto("digimon.greymon", "Greymon", level = "Adult"),
        CanonicalSpeciesDto("digimon.noart", "No art")), spriteSets = listOf(DotSpriteSetDto("dot", -100, "Agumon", "Fixture",
        width = 1, height = 1, frames = mapOf("idle_1" to "frames/${"a".repeat(64)}.bin")))))
    private lateinit var archive: NativeArchiveView
    private val speciesTable = WorldDsSpeciesTable.parse(Gson().toJson(mapOf(
        "gameId" to WorldDsSaveReader.GAME_ID, "formatProfile" to WorldDsSaveReader.PROFILE,
        "names" to MutableList(WorldDsSaveReader.SPECIES_COUNT) { "" }.also {
            it[20] = "Agumon"; it[21] = "Greymon"
        })))

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".integritycheck"))
        preferences.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(IndividualIntegrity.callback).build()
        graphics = SpritePreferenceRepository(preferences)
        db.participation = PartnerParticipationGate(db, { catalog })
        val record = ObservedDigimon("main:1", SaveContainer.PARTY, 1, 10, "Source name", 42, 16, 123,
            RecordMembership.ACTIVE_RECORD, totalExp = 999999, nativeRecordBase64 = "AQID")
        val observed = SaveObservation("fixture", "fixture-v1", 1, "a".repeat(64), listOf(record), emptyList(), SaveCapabilities(canImportSnapshot = true))
        val sources = LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> observed })
        val previews = SaveImportPreviewRepository(db, sources)
        acquisition = GameImportAcquisitionRepository(db, sources, previews, listOf(VerifiedSnapshotImportProfile(
            "fixture", "fixture-v1", 1, "fixture-id-v1", mapOf(16 to DigimonPersonalityType.COMPASSIONATE), nativeLevelRange = 1..99)))
        val source = sources.link(SaveDocument("provider:slot", "content://provider/slot", "content://provider/slot", "0001.bin"))
        acquisition.commit(previews.prepare(source, observed.contentRevision, listOf(record.recordKey)).draft.draftId)
        archive = acquisition.archives.first().single()
        repository = NativeMaterializationRepository(db, acquisition, { catalog }, { definition(it) }, speciesTable)
    }
    @After fun cleanup() { db.close(); preferences.edit().clear().commit() }

    private suspend fun definition(id: String): CardCharacter {
        val bytes = byteArrayOf(0, 0)
        val sprite = if (id == "digimon.noart") PlaceholderSprite.create() else
            Sprite(-100, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, 1, 1)
        db.spriteDao().insertSprite(sprite)
        val model = CardCharacter(ExpandedCatalogRepository.definitionId(id), null, sprite.id.toLong(), 0, 2,
            com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.Vaccine, 3, 10, 2, bytes, 1, 1, id, id)
        db.characterDao().insertCharacter(model)
        return model
    }
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { check(it.moveToFirst()); it.getInt(0) }

    @Test fun worldDsAcquisitionReusesSharedAppRepresentationAndRetainsAbsentNativePersonality() = runBlocking {
        val record = ObservedDigimon("copy:1:record:0", SaveContainer.PARTY, 1, 20, "Test", 3, null, 0,
            RecordMembership.ACTIVE_RECORD, totalExp = null, talentRaw = null, bondRaw = null,
            nativeRecordBase64 = "AQID", nativeIdentity = "save-local:7",
            nativeParameters = NativeStatParameters(100, 110, 20, 25, 30, 30, 30, 30, 25, List(8) { 11L }, 1))
        val observed = SaveObservation(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 1, "d".repeat(64),
            listOf(record), emptyList(), SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true))
        val sources = LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> observed })
        val previews = SaveImportPreviewRepository(db, sources)
        val production = GameImportAcquisitionRepository(db, sources, previews)
        val source = sources.link(SaveDocument("provider:ds", "content://provider/ds", "content://provider/ds", "World DS.sav"))
        val draft = previews.prepare(source, observed.contentRevision, listOf(record.recordKey))
        val created = production.commit(draft.draft.draftId)
        val id = created.receipts.single().individualId
        assertEquals(0, production.commit(draft.draft.draftId).createdCount)
        val native = production.archives.first().single { it.snapshot.individualId == id }
        assertNull(native.snapshot.nativePersonalityId)
        assertEquals(record, native.record)
        val personality = db.digimonIndividualDao().getPersonality(id)
        val materialization = NativeMaterializationRepository(db, production, { catalog }, { definition(it) })
        val mapping = materialization.saveMapping(native.snapshot.id, "digimon.agumon")
        val result = materialization.materialize(mapping)
        assertTrue(result.created)
        assertEquals("APP", db.userCharacterDao().getByIndividualIdSync(id).single().characterType.name)
        assertEquals(personality, db.digimonIndividualDao().getPersonality(id))
        assertEquals(OriginStatInfluence.fromKnownLevel(3, 1, 99), db.appPartnerStateDao().state(id)?.originBasisPoints)
        assertEquals(record, production.archives.first().single { it.snapshot.individualId == id }.record)
        assertEquals(0, count("VBCharacterData")); assertEquals(0, count("BECharacterData"))
        val nativeStats = WorldDsStatConversion.NativeStats(3, 25, 100, 110, 20, 25, 30, 30, 30, 30, List(8) { 11L })
        val converted = WorldDsStatConversion.toApp(nativeStats,
            WorldDsStatConversion.AppStats(stage = 2, baseHp = 3, baseBp = 10, baseAp = 2))
        val seeded = db.userCharacterDao().getByIndividualIdSync(id).single()
        assertEquals(converted.vitalPoints, seeded.vitalPoints)
        assertEquals(converted.mood, seeded.mood)
        assertEquals(11, seeded.trophies)
        assertEquals(11, seeded.totalBattlesWon)
        assertEquals(11, seeded.totalBattlesLost)
        assertEquals(11, seeded.activityLevel)
        val seededProgress = requireNotNull(db.appPartnerStateDao().state(id))
        assertEquals(converted.trainingHp, seededProgress.trainingHp)
        assertEquals(converted.trainingBp, seededProgress.trainingBp)
        assertEquals(converted.trainingAp, seededProgress.trainingAp)
        assertEquals(11, seededProgress.totalTrophies)
        val changedNative = NativeStatParameters(200, 200, 25, 25, 30, 30, 30, 30, 25, listOf(1, 2, 3, 4200, 4, 5, 6, 7), 1)
        val changed = record.copy(level = 30, nativeParameters = changedNative)
        val changedId = db.nativeGameArchiveDao().insertSnapshot(NativeGameSnapshot(individualId = id,
            bindingId = native.snapshot.bindingId, sourceLabel = "World DS.sav", contentRevision = "e".repeat(64),
            codecVersion = 1, recordKey = record.recordKey, recordJson = Gson().toJson(changed), nativePersonalityId = null,
            mappedPersonality = requireNotNull(personality).personalityType.name, importedAt = 1))
        assertTrue(materialization.applyWorldDsReturn(changedId))
        val returned = db.userCharacterDao().getByIndividualIdSync(id).single()
        val expected = WorldDsStatConversion.applyDelta(nativeStats,
            WorldDsStatConversion.NativeStats(30, 25, 200, 200, 25, 25, 30, 30, 30, 30, listOf(1, 2, 3, 4200, 4, 5, 6, 7)),
            WorldDsStatConversion.AppStats(stage = 2, baseHp = 3, baseBp = 10, baseAp = 2,
                trainingHp = converted.trainingHp, trainingBp = converted.trainingBp, trainingAp = converted.trainingAp,
                vitalPoints = converted.vitalPoints, mood = converted.mood, trophies = 11, wins = 11, losses = 11, activityLevel = 11))
        assertEquals(expected.vitalPoints, returned.vitalPoints)
        assertEquals(expected.trophies, returned.trophies)
        assertEquals(expected.wins, returned.totalBattlesWon)
        assertEquals(expected.losses, returned.totalBattlesLost)
        assertEquals(expected.activityLevel, returned.activityLevel)
        assertEquals(changedId, db.transferContinuityDao().projection(id)?.nativeSnapshotId)
        val sendDirectory = File(context.cacheDir, "send-${UUID.randomUUID()}")
        val sends = ExperimentalWorldDsSendRepository(db, sources, object : SaveDocumentWrite {
            override fun canWrite(documentUri: String, grantUri: String) = false
            override suspend fun write(documentUri: String, bytes: ByteArray) = Unit
        }, sendDirectory, speciesTable, { catalog })
        try {
            // After the return the projection matches the app state: the exact retained bytes are used.
            val exact = requireNotNull(sends.outbound(id))
            assertEquals("save-local:7", exact.nativeIdentity)
            // App progress regenerates the stat windows but keeps the native identity and species.
            val progressed = db.userCharacterDao().getByIndividualIdSync(id).single()
            db.userCharacterDao().updateCharacter(progressed.copy(vitalPoints = progressed.vitalPoints + 500))
            val generated = requireNotNull(sends.outbound(id))
            assertEquals("save-local:7", generated.nativeIdentity)
            assertEquals(20L, generated.nativeSpeciesId)
            assertNotNull(generated.nativeRecordBase64)
            // A watch-imported partner often has no species name; the card's species reference still resolves it.
            db.openHelper.writableDatabase.execSQL("UPDATE DigimonIndividual SET speciesName=NULL WHERE individualId=?", arrayOf<Any?>(id))
            val fromCard = requireNotNull(sends.outbound(id))
            assertEquals(20L, fromCard.nativeSpeciesId)
            // A form change without a checkpoint must seed the app level from the evolved record,
            // and the send must keep that level instead of restoring the native record's level.
            db.openHelper.writableDatabase.execSQL("DELETE FROM NativeAppProjection WHERE individualId=?", arrayOf<Any?>(id))
            db.userCharacterDao().updateCharacter(db.userCharacterDao().getByIndividualIdSync(id).single().copy(vitalPoints = 0))
            val evolved = changed.copy(nativeSpeciesId = 21)
            val evolvedId = db.nativeGameArchiveDao().insertSnapshot(NativeGameSnapshot(individualId = id,
                bindingId = native.snapshot.bindingId, sourceLabel = "World DS.sav", contentRevision = "f".repeat(64),
                codecVersion = 1, recordKey = record.recordKey, recordJson = Gson().toJson(evolved), nativePersonalityId = null,
                mappedPersonality = requireNotNull(personality).personalityType.name, importedAt = 2))
            assertTrue(materialization.applyWorldDsReturn(evolvedId, "digimon.agumon", "GAME_TABLE"))
            assertEquals(WorldDsStatConversion.vitalPointsForLevel(30),
                db.userCharacterDao().getByIndividualIdSync(id).single().vitalPoints)
            assertEquals(ExpandedCatalogRepository.definitionId("digimon.greymon"),
                db.userCharacterDao().getByIndividualIdSync(id).single().charId)
            assertEquals("digimon.greymon", db.digimonIndividualDao().getIndividual(id)?.speciesName)
            assertEquals(evolvedId, db.transferContinuityDao().projection(id)?.nativeSnapshotId)
            val preserved = requireNotNull(sends.outbound(id))
            assertEquals(30, preserved.level)
            assertEquals(21L, preserved.nativeSpeciesId)
            db.userCharacterDao().updateCharacter(db.userCharacterDao().getByIndividualIdSync(id).single()
                .copy(vitalPoints = WorldDsStatConversion.vitalPointsForLevel(50)))
            assertEquals(50, requireNotNull(sends.outbound(id)).level)
        } finally { sendDirectory.deleteRecursively() }
    }

    @Test fun materializationPreservesIdentityPersonalityNameAndNativeData() = runBlocking {
        val id = archive.snapshot.individualId
        val original = requireNotNull(db.digimonIndividualDao().getIndividual(id))
        val personality = requireNotNull(db.digimonIndividualDao().getPersonality(id)).copy(personalityType = DigimonPersonalityType.FRIENDLY)
        db.digimonIndividualDao().upsertPersonality(personality)
        db.openHelper.writableDatabase.execSQL("UPDATE DigimonIndividual SET nickname='My name' WHERE individualId=?", arrayOf(id))
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val result = repository.materialize(mapping)
        assertTrue(result.created)
        assertEquals(1, count("DigimonIndividual"))
        assertEquals(personality, db.digimonIndividualDao().getPersonality(id))
        val individual = requireNotNull(db.digimonIndividualDao().getIndividual(id))
        assertEquals(original.createdAt, individual.createdAt)
        assertEquals("My name", individual.nickname)
        assertEquals("digimon.agumon", individual.speciesName)
        assertEquals("IMPORTED", individual.originKind)
        val character = db.userCharacterDao().getByIndividualIdSync(id).single()
        assertEquals("APP", character.characterType.name)
        assertEquals(0, character.vitalPoints)
        assertFalse(character.isActive)
        assertEquals(42, acquisition.archives.first().single().record?.level)
        assertEquals(0, count("VBCharacterData")); assertEquals(0, count("BECharacterData"))
    }

    @Test fun missingArtworkCreatesAUsablePartnerWithANeutralPlaceholderAndRetainsNativeData() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.noart")
        assertEquals(NativeMaterializationReadiness.READY, repository.readiness(mapping))
        val created = repository.materialize(mapping)
        assertTrue(created.created)
        assertTrue(db.participation!!.snapshot().owned(created.receipt.characterId).usable)
        assertEquals(PlaceholderSprite.ID, db.spriteDao().getForCharacter(created.receipt.definitionId)?.id)
        assertEquals(archive.record, acquisition.archives.first().single().record)
    }

    @Test fun noLoadedDimIsRequiredForAUsableImportedPartner() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        assertEquals(NativeMaterializationReadiness.READY, repository.readiness(mapping))
        val created = repository.materialize(mapping)
        assertTrue(db.participation!!.snapshot().owned(created.receipt.characterId).usable)
        assertEquals(1, count("NativeMaterializationReceipt"))
    }

    @Test fun existingMappedArchiveAutomaticallyBecomesUsableOnceWithoutChangingIdentityOrProgress() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val original = db.digimonIndividualDao().getIndividual(archive.snapshot.individualId)
        assertEquals(1, repository.materializeRetainedMappings())
        assertEquals(0, repository.materializeRetainedMappings())
        val character = db.userCharacterDao().getByIndividualIdSync(archive.snapshot.individualId).single()
        assertTrue(db.participation!!.snapshot().owned(character.id).usable)
        assertEquals(original?.nickname, db.digimonIndividualDao().getIndividual(character.individualId)?.nickname)
        assertEquals(mapping, repository.mapping(archive.snapshot.id))
        assertEquals(archive.record, acquisition.archives.first().single().record)
        assertEquals(1, count("NativeMaterializationReceipt"))
    }

    @Test fun retryAfterRepresentationDeletionDoesNotRecreateOrResetTheIndividual() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val created = repository.materialize(mapping)
        db.openHelper.writableDatabase.execSQL("DELETE FROM UserCharacter WHERE id=?", arrayOf(created.receipt.characterId))
        assertFalse(repository.materialize(mapping).created)
        assertEquals(0, count("UserCharacter"))
        assertEquals(1, count("NativeMaterializationReceipt"))
    }

    @Test fun reacquiredAfterDeletionRestoresThePlayableRepresentation() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val created = repository.materialize(mapping)
        db.openHelper.writableDatabase.execSQL("DELETE FROM UserCharacter WHERE id=?", arrayOf(created.receipt.characterId))
        val newerRevision = "e".repeat(64)
        val newerId = db.nativeGameArchiveDao().insertSnapshot(archive.snapshot.copy(id = 0,
            contentRevision = newerRevision, importedAt = archive.snapshot.importedAt + 1))
        db.saveImportDraftDao().insertReceipt(GameImportItemReceipt(UUID.randomUUID().toString(), "test-source",
            newerRevision, "fixture-v1", 1, archive.snapshot.recordKey, archive.snapshot.individualId,
            archive.snapshot.importedAt + 1))
        assertEquals(1, repository.reconcileOrphanedDeletions())
        assertEquals(newerId, db.nativeMaterializationDao().receipt(archive.snapshot.individualId)?.snapshotId)
        val restored = db.userCharacterDao().getByIndividualIdSync(archive.snapshot.individualId).single()
        assertEquals(created.receipt.individualId, restored.individualId)
        assertEquals(restored.id, db.transferContinuityDao()
            .materializedEvent(archive.snapshot.individualId)?.representationId)
    }

    @Test fun interruptedRestoreWithLostReceiptIsRecovered() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val created = repository.materialize(mapping)
        db.openHelper.writableDatabase.execSQL("DELETE FROM UserCharacter WHERE id=?", arrayOf(created.receipt.characterId))
        db.nativeMaterializationDao().deleteReceipt(archive.snapshot.individualId)
        val newerRevision = "f".repeat(64)
        val newerId = db.nativeGameArchiveDao().insertSnapshot(archive.snapshot.copy(id = 0,
            contentRevision = newerRevision, importedAt = archive.snapshot.importedAt + 1))
        db.saveImportDraftDao().insertReceipt(GameImportItemReceipt(UUID.randomUUID().toString(), "test-source",
            newerRevision, "fixture-v1", 1, archive.snapshot.recordKey, archive.snapshot.individualId,
            archive.snapshot.importedAt + 1))
        assertEquals(1, repository.reconcileOrphanedDeletions())
        assertEquals(newerId, db.nativeMaterializationDao().receipt(archive.snapshot.individualId)?.snapshotId)
        assertEquals(1, db.userCharacterDao().getByIndividualIdSync(archive.snapshot.individualId).size)
    }

    @Test fun deletingARepresentationWithNoNewerSnapshotRemovesTheOrphanedArchive() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val created = repository.materialize(mapping)
        db.userCharacterDao().deleteCharacterById(created.receipt.characterId)
        assertEquals(1, repository.reconcileOrphanedDeletions())
        assertTrue(acquisition.archives.first().isEmpty())
        assertNull(db.digimonIndividualDao().getIndividual(archive.snapshot.individualId))
        assertEquals(0, count("NativeGameSnapshot"))
        assertEquals(0, count("GameImportItemReceipt"))
        assertEquals(0, count("UserCharacter"))
    }

    @Test fun explicitSourceNameChoiceBecomesNicknameWithoutChangingNativeRecord() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        repository.materialize(mapping, useSourceName = true)
        assertEquals("Source name", db.digimonIndividualDao().getIndividual(archive.snapshot.individualId)?.nickname)
        assertEquals(archive.record, acquisition.archives.first().single().record)
    }

    @Test fun cosmeticChangesDuringPreparationDoNotInvalidateMaterialization() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val changing = NativeMaterializationRepository(db, acquisition, { catalog }, {
            definition(it).also { graphics.setPreference(SpritePreference.DOT_FIRST); graphics.setPreference(SpritePreference.VB_FIRST) }
        })
        assertTrue(changing.materialize(mapping).created)
        assertEquals(1, count("UserCharacter"))
    }

    @Test fun assignedBemSuppressesExtraAppStatsWithoutDeletingGrowthOrNativeBaselines() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val created = repository.materialize(mapping).receipt
        com.github.nacabaro.vbhelper.database.TransferContinuitySchema.install(db.openHelper.writableDatabase)
        val progress = db.appPartnerStateDao().state(created.individualId)!!.copy(
            trainingHp = 222, trainingAp = 111, trainingBp = 333, originBasisPoints = 11000)
        db.appPartnerStateDao().update(progress)
        val journey = UniversalJourneyRepository(db)
        val ledger = journey.synchronizeAppChanges(created.individualId).stateJson
        seedCard()
        val definition = db.characterDao().getById(created.definitionId)!!
        db.characterDao().insertCharacter(definition.copy(id = 100, cardId = 1, catalogSpeciesId = null,
            baseHp = 3000, baseBp = 4500, baseAp = 1000))
        val conversion = com.github.nacabaro.vbhelper.source.BraceletConversionService(db)
        conversion.convert(created.characterId, 100)
        val assigned = db.userCharacterDao().getAllBattleParticipantProfiles().single()
        assertTrue(assigned.usesAssignedDim)
        assertTrue(assigned.isBemCard)
        assertEquals(3000, assigned.baseHp)
        assertEquals(0, assigned.trainingHp)
        assertEquals(0, assigned.trainingAp)
        assertEquals(0, assigned.trainingBp)
        assertEquals(10000, assigned.originBasisPoints)
        assertEquals(progress, db.appPartnerStateDao().state(created.individualId))
        assertEquals(ledger, journey.synchronizeAppChanges(created.individualId).stateJson)
        val outgoing = com.github.nacabaro.vbhelper.screens.scanScreen.converters.ToNfcConverter(db).characterToNfc(created.characterId)
        assertTrue(outgoing is com.github.cfogrady.vbnfc.be.BENfcCharacter)
        assertEquals(0, (outgoing as com.github.cfogrady.vbnfc.be.BENfcCharacter).trainingHp.toInt())
        assertEquals(progress, db.appPartnerStateDao().state(created.individualId))
        conversion.clearConversion(created.characterId)
        val restored = db.userCharacterDao().getAllBattleParticipantProfiles().single()
        assertFalse(restored.usesAssignedDim)
        assertEquals(222, restored.trainingHp)
        assertEquals(111, restored.trainingAp)
        assertEquals(333, restored.trainingBp)
        assertEquals(11000, restored.originBasisPoints)
        assertEquals(ledger, journey.synchronizeAppChanges(created.individualId).stateJson)
        assertEquals(archive.record, acquisition.archives.first().single().record)
        assertEquals(created.individualId, db.userCharacterDao().getCharacter(created.characterId).individualId)
    }

    private fun seedCard() {
        val values = linkedMapOf<String, Any?>("id" to 1, "cardId" to 1, "name" to "Fixture BEM", "isBEm" to 1,
            "officialStatus" to "UNKNOWN")
        db.openHelper.writableDatabase.query("PRAGMA table_info(Card)").use { columns ->
            while (columns.moveToNext()) {
                val column = columns.getString(1)
                if (column !in values && columns.getInt(3) != 0 && columns.isNull(4)) values[column] = when (columns.getString(2)) {
                    "TEXT" -> ""; "BLOB" -> byteArrayOf(0, 0); else -> 0
                }
            }
        }
        db.openHelper.writableDatabase.execSQL("INSERT INTO Card (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray())
    }

    @Test fun changingTheReviewedMappingDuringPreparationInvalidatesTheRequest() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val changing = NativeMaterializationRepository(db, acquisition, { catalog }, {
            definition(it).also { repository.saveMapping(archive.snapshot.id, "digimon.noart") }
        })
        try { changing.materialize(mapping); fail("Reviewed mapping changed") } catch (_: NativeMaterializationStaleException) { }
        assertEquals(0, count("UserCharacter"))
    }

    @Test fun unverifiedProfileCannotMaterializeEvenAnAlreadyArchivedSnapshot() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        val blocked = GameImportAcquisitionRepository(db,
            LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> error("Not a source read") }),
            SaveImportPreviewRepository(db, LinkedGameSaveRepository(db, SaveDocumentAccess { _, _ -> error("Not a source read") })))
        val guarded = NativeMaterializationRepository(db, blocked, { catalog }, { definition(it) })
        assertEquals(NativeMaterializationReadiness.PROFILE_UNVERIFIED, guarded.readiness(mapping))
        try { guarded.materialize(mapping); fail("Profile must remain verified") } catch (_: IllegalStateException) { }
        assertEquals(0, count("UserCharacter"))
    }

    @Test fun watchPresenceBlocksMaterializationAndHidesOwnedArchiveEntry() = runBlocking {
        val mapping = repository.saveMapping(archive.snapshot.id, "digimon.agumon")
        db.openHelper.writableDatabase.execSQL("INSERT INTO WatchTransfer(token,individualId,deviceType,dimId,generation,ageInDays,totalBattlesWon,totalBattlesLost,history,deviceKey) VALUES ('native-test-token',?,0,0,NULL,0,0,0,'kept','')",
            arrayOf(archive.snapshot.individualId))
        try { repository.materialize(mapping); fail("Away at bracelet") } catch (_: IllegalStateException) { }
        assertEquals(0, count("UserCharacter"))
        assertTrue(acquisition.archives.first().isEmpty())
    }
}
