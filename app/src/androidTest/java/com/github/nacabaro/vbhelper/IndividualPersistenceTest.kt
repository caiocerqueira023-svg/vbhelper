package com.github.nacabaro.vbhelper

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.domain.identity.TransferFingerprint
import com.github.nacabaro.vbhelper.domain.identity.WatchTransfer
import com.github.nacabaro.vbhelper.screens.scanScreen.converters.FromNfcConverter
import com.github.nacabaro.vbhelper.screens.scanScreen.converters.ToNfcConverter
import com.github.nacabaro.vbhelper.source.EvolutionHistoryRepository
import com.github.nacabaro.vbhelper.source.WatchTransferRepository
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Runs against real Android SQLite/Room in a separate .integritycheck installation. */
@RunWith(AndroidJUnit4::class)
class IndividualPersistenceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val name = "individual-integrity-tests.db"
    private val individual = "00112233445566778899aa"

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_17_18)
        .addCallback(IndividualIntegrity.callback).build()

    @Before fun setup() {
        check(context.packageName.endsWith(".integritycheck")) { "Never run against the user's installed app." }
        context.deleteDatabase(name)
        db = open()
        seed("Card", mapOf("id" to 1, "cardId" to 27, "name" to "MadDragonicMetal", "officialStatus" to "UNKNOWN"))
        seed("CardProgress", mapOf("cardId" to 1))
        seed("Sprite", mapOf("id" to 1))
        for (slot in 0..6) {
            seed("CardCharacter", mapOf("id" to 100 + slot, "cardId" to 1, "spriteId" to 1,
                "charaIndex" to slot, "stage" to minOf(slot, 3), "attribute" to "Virus"))
        }
        for ((from, to) in listOf(100 to 101, 101 to 102, 102 to 103, 102 to 104, 102 to 105, 102 to 106)) {
            seed("PossibleTransformations", mapOf("charaId" to from, "toCharaId" to to))
        }
    }

    @After fun teardown() { db.close(); context.deleteDatabase(name) }

    private fun seed(table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                if (column in overrides) values[column] = overrides[column]
                else if (cursor.getInt(3) != 0 && cursor.isNull(4) && column != "id") {
                    values[column] = when (cursor.getString(2)) {
                        "TEXT" -> ""
                        "BLOB" -> byteArrayOf(0)
                        else -> 0
                    }
                }
            }
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray(),
        )
    }

    private fun store(id: String = individual): Long {
        val key = db.userCharacterDao().insertCharacterData(UserCharacter(
            individualId = id, charId = 102, ageInDays = 4, mood = 80, vitalPoints = 9999,
            transformationCountdown = 1, injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 30, currentPhaseBattlesWon = 25, currentPhaseBattlesLost = 1,
            totalBattlesWon = 40, totalBattlesLost = 10, activityLevel = 0, heartRateCurrent = 0,
            characterType = DeviceType.VBDevice, isActive = false,
        ))
        db.userCharacterDao().insertVBCharacterData(VBCharacterData(key, 2, 30))
        repeat(4) { slot -> seed("SpecialMissions", mapOf("characterId" to key, "watchId" to slot + 1,
            "status" to "UNAVAILABLE", "missionType" to "NONE")) }
        EvolutionHistoryRepository(db).repairCharacter(key)
        return key
    }

    // Replaces obsolete helper-only tests with the current public converters and real Room data.
    private fun markAsBe(key: Long, minutes: Int = 37) {
        db.openHelper.writableDatabase.execSQL("UPDATE UserCharacter SET characterType='BEDevice' WHERE id=?", arrayOf(key))
        seed("BECharacterData", mapOf("id" to key, "remainingTrainingTimeInMinutes" to minutes,
            "trainingHp" to 12, "trainingAp" to 34, "trainingBp" to 56,
            "abilityRarity" to NfcCharacter.AbilityRarity.entries.first().name))
    }

    @Test fun nfcExportUsesStoredVbProfileAndPreservesSource() = runBlocking {
        val key = store()
        personalData()
        val before = TransferFingerprint.of(db.userCharacterDao().getCharacterSync(key)!!)
        val nfc = ToNfcConverter(db).characterToNfc(key)
        assertTrue(nfc is VBNfcCharacter)
        assertEquals(9999, nfc.vitalPoints.toInt())
        assertEquals(30, (nfc as VBNfcCharacter).trophies.toInt())
        assertEquals(before, TransferFingerprint.of(db.userCharacterDao().getCharacterSync(key)!!))
    }

    @Test fun nfcExportUsesStoredBeProfileEvenWithDimCard() = runBlocking {
        val key = store()
        markAsBe(key)
        val before = TransferFingerprint.of(db.userCharacterDao().getCharacterSync(key)!!)
        val nfc = ToNfcConverter(db).characterToNfc(key)
        assertTrue(nfc is com.github.cfogrady.vbnfc.be.BENfcCharacter)
        nfc as com.github.cfogrady.vbnfc.be.BENfcCharacter
        assertEquals(37, nfc.remainingTrainingTimeInMinutes.toInt())
        assertEquals(30, nfc.trophies.toInt())
        assertEquals(56, nfc.trainingBp.toInt())
        assertEquals(before, TransferFingerprint.of(db.userCharacterDao().getCharacterSync(key)!!))
    }

    @Test fun vitalWearExportPreservesBeTrainingAndStats() {
        val key = store()
        markAsBe(key)
        val proto = com.github.nacabaro.vbhelper.source.VitalWearCharacterExporter(context, db).buildCharacterProto(key)
        assertEquals(37L * 60, proto.characterStats.trainingTimeRemainingInSeconds)
        assertEquals(12, proto.characterStats.trainedHp)
        assertEquals(34, proto.characterStats.trainedAp)
        assertEquals(56, proto.characterStats.trainedBp)
        assertEquals(30, proto.characterStats.trainedPp)
        assertEquals(9999, proto.characterStats.vitals)
        assertEquals(40, proto.characterStats.totalWins)
        assertEquals(individual, db.userCharacterDao().getCharacterSync(key)!!.individualId)
    }

    @Test fun vitalWearExportKeepsCurrentZeroTrainingForVb() {
        val key = store()
        val proto = com.github.nacabaro.vbhelper.source.VitalWearCharacterExporter(context, db).buildCharacterProto(key)
        assertEquals(0L, proto.characterStats.trainingTimeRemainingInSeconds)
        assertEquals(30, proto.characterStats.trainedPp)
    }

    @Test fun vitalWearExportRejectsMissingBeDataWithoutChangingIndividual() {
        val key = store()
        db.openHelper.writableDatabase.execSQL("UPDATE UserCharacter SET characterType='BEDevice' WHERE id=?", arrayOf(key))
        val before = TransferFingerprint.of(db.userCharacterDao().getCharacterSync(key)!!)
        assertThrows(IllegalStateException::class.java) {
            com.github.nacabaro.vbhelper.source.VitalWearCharacterExporter(context, db).buildCharacterProto(key)
        }
        assertEquals(before, TransferFingerprint.of(db.userCharacterDao().getCharacterSync(key)!!))
    }

    private fun incomingVitalWear(cardName: String, cardId: Int) =
        com.github.cfogrady.vitalwear.protos.Character.newBuilder().setCardName(cardName).setCardId(cardId)
            .setCharacterStats(com.github.cfogrady.vitalwear.protos.Character.CharacterStats.newBuilder()
                .setSlotId(2).setVitals(1300).setTrainedPp(10).setTotalBattles(8).setTotalWins(8)).build()

    @Test fun vitalWearImportFindsRenamedCardByUniqueDimIdAndRejectsReplayDuplicates() {
        val importer = com.github.nacabaro.vbhelper.source.VitalWearCharacterImporter(db)
        val incoming = incomingVitalWear("Old card name", 27)
        assertTrue(importer.importCharacter(incoming).success)
        assertTrue(importer.importCharacter(incoming).success)
        db.openHelper.writableDatabase.query("SELECT individualId, vitalPoints, trophies FROM UserCharacter").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getString(0).isNotBlank())
            assertEquals(1300, it.getInt(1))
            assertEquals(10, it.getInt(2))
            assertFalse(it.moveToNext())
        }
    }

    @Test fun vitalWearImportUsesExactNameWhenDimIdIsUnavailable() {
        val importer = com.github.nacabaro.vbhelper.source.VitalWearCharacterImporter(db)
        assertTrue(importer.importCharacter(incomingVitalWear("MadDragonicMetal", 999)).success)
    }

    @Test fun vitalWearImportDoesNotGuessNormalizedNameOrAmbiguousDim() {
        val importer = com.github.nacabaro.vbhelper.source.VitalWearCharacterImporter(db)
        assertFalse(importer.importCharacter(incomingVitalWear("mad-dragonic-metal", 999)).success)
        seed("Card", mapOf("id" to 2, "cardId" to 27, "name" to "Other custom", "officialStatus" to "UNKNOWN"))
        assertFalse(importer.importCharacter(incomingVitalWear("Unknown", 27)).success)
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM UserCharacter").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
    }

    private fun personalData() {
        db.openHelper.writableDatabase.execSQL("UPDATE DigimonIndividual SET nickname='1stmaru' WHERE individualId=?", arrayOf(individual))
        seed("ChatMessageEntity", mapOf("individualId" to individual, "role" to "user", "content" to "My own conversation"))
        seed("DigimonPersonalityTraits", mapOf("individualId" to individual, "temperament" to "CALM",
            "socialStyle" to "LOYAL_WARM", "speechQuirk" to "SHORT_DIRECT"))
    }

    private fun prepare(key: Long): Pair<VBNfcCharacter, WatchTransfer> = runBlocking {
        val character = ToNfcConverter(db).characterToNfc(key) as VBNfcCharacter
        val token = IndividualIdentity.generate()
        character.appReserved1 = IndividualIdentity.encode(token)
        val source = requireNotNull(db.userCharacterDao().getCharacterSync(key))
        val receipt = WatchTransfer.capture(token, source.individualId, character).copy(
            sourceCharacterId = key, cardId = 1, sourceFingerprint = TransferFingerprint.of(source), deviceKey = "watch-A",
        )
        WatchTransferRepository(db).prepare(receipt)
        character to receipt
    }

    private fun receive(character: NfcCharacter, watch: String = "watch-A") =
        FromNfcConverter(db, watch).addCharacter(character) { _, _ -> error("Unexpected ambiguous DIM") }

    private fun count(table: String): Int = db.openHelper.writableDatabase.query("SELECT count(*) FROM `$table`").use {
        it.moveToFirst(); it.getInt(0)
    }

    private fun fails(action: () -> Unit) {
        try { action() } catch (_: Exception) { return }
        fail("Expected failure preserving the source")
    }

    @Test fun fullRoundTripsPreserveIdentityStatsChatsAndPersonalityAcrossRestarts() = runBlocking {
        var key = store()
        personalData()
        repeat(20) {
            val before = requireNotNull(db.userCharacterDao().getCharacterSync(key))
            val (wire, receipt) = prepare(key)
            WatchTransferRepository(db).complete(receipt)
            assertNull(db.userCharacterDao().getCharacterSync(key))
            db.close(); db = open()
            assertEquals("Done reading character!", receive(wire))
            val returned = db.userCharacterDao().getByIndividualIdSync(individual).single()
            assertEquals(before.copy(id = returned.id, isActive = true), returned)
            key = returned.id
            assertEquals("1stmaru", db.digimonIndividualDao().getIndividual(individual)?.nickname)
            assertEquals(1, db.chatDao().getMessagesSync(individual).size)
            assertNotNull(db.digimonIndividualDao().getPersonality(individual))
        }
        assertEquals(1, count("UserCharacter"))
        assertEquals(1, count("DigimonIndividual"))
    }

    @Test fun interruptedSendCanReturnWithoutCreatingAnotherIndividual() {
        val key = store(); personalData()
        val (wire, _) = prepare(key)
        // Device accepted data, process died before local completion.
        db.close(); db = open()
        receive(wire)
        assertEquals(1, count("UserCharacter"))
        assertEquals(1, count("DigimonIndividual"))
        assertEquals(1, count("ChatMessageEntity"))
        assertNull(db.userCharacterDao().getCharacterSync(key))
    }

    @Test fun failedWriteKeepsSourceAndDurableReceipt() {
        val key = store()
        val (_, receipt) = prepare(key)
        db.close(); db = open()
        assertNotNull(db.userCharacterDao().getCharacterSync(key))
        assertEquals(receipt, db.watchTransferDao().get(receipt.token))
        WatchTransferRepository(db).prepare(receipt) // Retry uses the same token.
    }

    @Test fun repeatedReceiveAfterLostAcknowledgementIsIdempotent() {
        val (wire, receipt) = prepare(store())
        WatchTransferRepository(db).complete(receipt)
        receive(wire)
        val saved = db.userCharacterDao().getByIndividualIdSync(individual).single()
        db.close(); db = open()
        repeat(3) { receive(wire) }
        assertEquals(listOf(saved), db.userCharacterDao().getByIndividualIdSync(individual))
        assertEquals(1, count("DigimonIndividual"))
    }

    @Test fun importFailureRollsBackIdentityReceiptAndPartialCharacter() {
        val key = store()
        val (wire, receipt) = prepare(key)
        WatchTransferRepository(db).complete(receipt)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER inject_failure BEFORE INSERT ON SpecialMissions BEGIN SELECT RAISE(ABORT, 'injected disk failure'); END")
        fails { receive(wire) }
        assertEquals(0, count("UserCharacter"))
        assertEquals(0, count("VBCharacterData"))
        assertEquals(0, count("WatchImportReceipt"))
        assertEquals(receipt, db.watchTransferDao().get(receipt.token))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER inject_failure")
        receive(wire)
        assertEquals(individual, db.userCharacterDao().getByIndividualIdSync(individual).single().individualId)
    }

    @Test fun editedLocalSourceIsNeverDeletedByAnOldPreparedTransfer() {
        val key = store()
        val (wire, receipt) = prepare(key)
        db.openHelper.writableDatabase.execSQL("UPDATE UserCharacter SET trophies=31 WHERE id=?", arrayOf(key))
        fails { WatchTransferRepository(db).complete(receipt) }
        fails { receive(wire) }
        assertEquals(31, db.userCharacterDao().getCharacterSync(key)?.trophies)
        assertEquals(receipt, db.watchTransferDao().get(receipt.token))
    }

    @Test fun anotherPhysicalWatchCannotClaimThisIndividual() {
        val key = store()
        val (wire, receipt) = prepare(key)
        WatchTransferRepository(db).complete(receipt)
        fails { receive(wire, "watch-B") }
        assertEquals(0, count("UserCharacter"))
        assertEquals(receipt, db.watchTransferDao().get(receipt.token))
    }

    @Test fun wrongHistoryCannotSilentlyBecomeANewIndividual() {
        val (wire, receipt) = prepare(store())
        WatchTransferRepository(db).complete(receipt)
        wire.transformationHistory = emptyArray()
        fails { receive(wire) }
        assertEquals(1, count("DigimonIndividual"))
        assertEquals(receipt, db.watchTransferDao().get(receipt.token))
    }

    @Test fun identicalSpeciesKeepSeparateChats() = runBlocking {
        val first = store(); personalData()
        val otherId = IndividualIdentity.generate()
        val second = store(otherId)
        seed("ChatMessageEntity", mapOf("individualId" to otherId, "role" to "user", "content" to "Other conversation"))
        val (wire, receipt) = prepare(first)
        WatchTransferRepository(db).complete(receipt)
        receive(wire)
        assertEquals(otherId, db.userCharacterDao().getCharacterSync(second)?.individualId)
        assertEquals("Other conversation", db.chatDao().getMessagesSync(otherId).single().content)
        assertEquals("My own conversation", db.chatDao().getMessagesSync(individual).single().content)
    }

    @Test fun expiredReservedCardHintCannotOverrideRecordedCustomDim() {
        val (wire, receipt) = prepare(store())
        WatchTransferRepository(db).complete(receipt)
        seed("Card", mapOf("id" to 2, "cardId" to 27, "name" to "Another custom", "officialStatus" to "UNKNOWN"))
        wire.appReserved2[0] = 2u
        receive(wire)
        assertEquals(102L, db.userCharacterDao().getByIndividualIdSync(individual).single().charId)
    }

    @Test fun individualCannotBeDuplicatedOrReassignedByUpsert() {
        val key = store()
        fails { store() }
        val before = requireNotNull(db.userCharacterDao().getCharacterSync(key))
        fails { db.userCharacterDao().updateCharacter(before.copy(individualId = IndividualIdentity.generate())) }
        assertEquals(before, db.userCharacterDao().getCharacterSync(key))
    }

    @Test fun ordinaryStatsUpsertKeepsTheSameIndividual() {
        val key = store()
        val updated = requireNotNull(db.userCharacterDao().getCharacterSync(key)).copy(trophies = 55)
        db.userCharacterDao().updateCharacter(updated)
        assertEquals(updated, db.userCharacterDao().getCharacterSync(key))
    }

    @Test fun concurrentInsertsCannotCloneTheSameIndividual() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val outcomes = pool.invokeAll(List(2) { Callable { runCatching { store() }.isSuccess } }).map { it.get() }
            assertEquals(1, outcomes.count { it })
            assertEquals(1, count("UserCharacter"))
        } finally { pool.shutdownNow() }
    }

    @Test fun worldAndStorageCannotSimultaneouslyOwnAnIndividual() {
        store()
        fails { seed("WorldSpawn", mapOf("individualId" to individual, "cardCharacterId" to 102, "recruitmentState" to "WILD")) }
        assertEquals(0, count("WorldSpawn"))
    }

    @Test fun historyRepairAndDegenerationLeaveIdentityAndChatIntact() = runBlocking {
        val key = store(); personalData()
        db.openHelper.writableDatabase.execSQL("UPDATE UserCharacter SET charId=106 WHERE id=?", arrayOf(key))
        EvolutionHistoryRepository(db).repairAll()
        db.userCharacterDao().degenerateCharacter(key, 102)
        EvolutionHistoryRepository(db).repairAll()
        assertEquals(listOf(100L, 101L, 102L), db.evolutionHistoryDao().getHistory(key).map { it.speciesId })
        assertEquals(individual, db.userCharacterDao().getCharacterSync(key)?.individualId)
        assertEquals(30, db.userCharacterDao().getCharacterSync(key)?.trophies)
        assertEquals(1, db.chatDao().getMessagesSync(individual).size)
    }

    @Test fun migrationFrom17PreservesChatsStatsAndOutstandingTransfer() = runBlocking {
        val key = store(); personalData()
        val before = requireNotNull(db.userCharacterDao().getCharacterSync(key))
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DROP TABLE WatchImportReceipt")
        sql.execSQL("DROP TABLE WatchTransfer")
        AppDatabase.MIGRATION_16_17.migrate(sql)
        sql.execSQL("INSERT INTO WatchTransfer VALUES('old-token', ?, 1, 27, 2, 4, 40, 10, '2:2026:9:12;')", arrayOf(individual))
        sql.version = 17
        db.close(); db = open()
        assertEquals(before, db.userCharacterDao().getCharacterSync(key))
        assertEquals("1stmaru", db.digimonIndividualDao().getIndividual(individual)?.nickname)
        assertEquals(1, db.chatDao().getMessagesSync(individual).size)
        assertEquals(individual, db.watchTransferDao().get("old-token")?.individualId)
        assertEquals(18, db.openHelper.writableDatabase.version)
    }

    @Test fun missingMigrationFailsWithoutErasingCharacters() {
        val key = store(); personalData()
        db.openHelper.writableDatabase.version = 2
        db.close(); db = open()
        fails { db.userCharacterDao().getCharacterSync(key) }
        db.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { sql ->
            sql.rawQuery("SELECT individualId FROM UserCharacter WHERE id=?", arrayOf(key.toString())).use {
                assertTrue(it.moveToFirst()); assertEquals(individual, it.getString(0))
            }
            sql.version = 18
        }
        db = open()
    }

    @Test fun worldRecruitmentPreservesItsOwnIdentityAndConversationUnderDoubleTap() {
        val active = store()
        db.userCharacterDao().setActiveCharacter(active)
        val wild = IndividualIdentity.generate()
        seed("DigimonIndividual", mapOf("individualId" to wild, "nickname" to "World recruit"))
        seed("ChatMessageEntity", mapOf("individualId" to wild, "role" to "user", "content" to "Before recruitment"))
        seed("WorldSpawn", mapOf("id" to 500, "individualId" to wild, "cardCharacterId" to 106, "recruitmentState" to "WILD"))
        val repository = com.github.nacabaro.vbhelper.world.WorldRepository(db)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val outcomes = pool.invokeAll(List(2) { Callable { runBlocking { repository.recruitSpawn(500) }.isSuccess } }).map { it.get() }
            assertEquals(1, outcomes.count { it })
            val recruited = db.userCharacterDao().getByIndividualIdSync(wild).single()
            assertEquals(106L, recruited.charId)
            assertEquals(listOf(100L, 101L, 102L, 106L), db.evolutionHistoryDao().getHistory(recruited.id).map { it.speciesId })
            runBlocking {
                assertEquals("Before recruitment", db.chatDao().getMessagesSync(wild).single().content)
                assertEquals("World recruit", db.digimonIndividualDao().getIndividual(wild)?.nickname)
            }
            assertEquals(0, count("WorldSpawn"))
        } finally { pool.shutdownNow() }
    }

    @Test fun evolutionOnWatchKeepsTheSameIndividualAndUpdatedStats() {
        val (wire, receipt) = prepare(store())
        WatchTransferRepository(db).complete(receipt)
        wire.charIndex = 3u
        wire.stage = 3
        wire.totalBattlesWon = 45u
        wire.ageInDays = 5
        wire.transformationHistory[3] = NfcCharacter.Transformation(3u, 2026u, 9u, 13u)
        receive(wire)
        val returned = db.userCharacterDao().getByIndividualIdSync(individual).single()
        assertEquals(103L, returned.charId)
        assertEquals(45, returned.totalBattlesWon)
        assertEquals(9999, returned.vitalPoints)
    }

    @Test fun staleReplayedImportCannotReviveAnIndividualAlreadySentAgain() {
        val (firstWire, firstReceipt) = prepare(store())
        WatchTransferRepository(db).complete(firstReceipt)
        receive(firstWire)
        val (_, secondReceipt) = prepare(db.userCharacterDao().getByIndividualIdSync(individual).single().id)
        WatchTransferRepository(db).complete(secondReceipt)
        fails { receive(firstWire) }
        assertEquals(0, count("UserCharacter"))
        assertEquals(secondReceipt, db.watchTransferDao().get(secondReceipt.token))
    }

    @Test fun legacyPermanentIdInReservedBytesCannotStealOldConversation() = runBlocking {
        val key = store(); personalData()
        val wire = ToNfcConverter(db).characterToNfc(key)
        wire.appReserved1 = IndividualIdentity.encode(individual)
        db.userCharacterDao().deleteCharacterById(key)
        receive(wire)
        val fresh = db.userCharacterDao().getCharacterSync(db.evolutionHistoryDao().getStoredCharacterIds().single())!!
        assertNotEquals(individual, fresh.individualId)
        assertTrue(db.chatDao().getMessagesSync(fresh.individualId).isEmpty())
        assertEquals(1, db.chatDao().getMessagesSync(individual).size)
    }

    @Test fun failedLocalCompletionPreservesTheRecoveryReceipt() {
        val key = store()
        val (_, receipt) = prepare(key)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER inject_delete_failure BEFORE DELETE ON UserCharacter BEGIN SELECT RAISE(ABORT, 'injected completion failure'); END")
        fails { WatchTransferRepository(db).complete(receipt) }
        assertNotNull(db.userCharacterDao().getCharacterSync(key))
        assertEquals(receipt, db.watchTransferDao().get(receipt.token))
    }
}
