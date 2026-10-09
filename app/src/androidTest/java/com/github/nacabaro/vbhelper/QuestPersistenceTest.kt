package com.github.nacabaro.vbhelper

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.digifarm.WildRelationship
import com.github.nacabaro.vbhelper.quests.*
import com.github.nacabaro.vbhelper.utils.DeviceType
import com.github.nacabaro.vbhelper.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room tests use a separate database in the isolated integritycheck application. */
@RunWith(AndroidJUnit4::class)
class QuestPersistenceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val databaseName = "quest-persistence-tests.db"
    private val now = 1_000_000L
    private val partner = "00112233445566778899aa"

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".integritycheck"))
        context.deleteDatabase(databaseName)
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).addCallback(IndividualIntegrity.callback).build()
        seed("Card", mapOf("id" to 7, "cardId" to 28, "name" to "Fixture", "stageCount" to 15, "officialStatus" to "UNKNOWN", "worldSpawnsEnabled" to 1))
        seed("CardProgress", mapOf("cardId" to 7, "currentStage" to 1))
        seed("Sprite", mapOf("id" to 1))
        for ((id, stage) in listOf(700 to 3, 701 to 4, 702 to 2)) seed("CardCharacter", mapOf(
            "id" to id, "cardId" to 7, "spriteId" to 1, "charaIndex" to id - 698, "stage" to stage, "attribute" to "Virus"))
        seed("DigimonIndividual", mapOf("individualId" to "giver"))
        db.wildRelationshipDao().insert(WildRelationship("giver", 701, "Giver", 90, now, createdAt = now, updatedAt = now))
        val stored = UserCharacter(individualId = partner, charId = 700, ageInDays = 1, mood = 80, vitalPoints = 3000,
            transformationCountdown = 1, injuryStatus = NfcCharacter.InjuryStatus.None, trophies = 8,
            currentPhaseBattlesWon = 20, currentPhaseBattlesLost = 5, totalBattlesWon = 20, totalBattlesLost = 5,
            activityLevel = 0, heartRateCurrent = 0, characterType = DeviceType.VBDevice, isActive = true)
        val id = db.userCharacterDao().insertCharacterData(stored)
        db.userCharacterDao().insertVBCharacterData(VBCharacterData(id, 0, 30))
        db.questDao().initializeWallet(QuestWallet(balance = 100))
        seed("Items", mapOf("id" to 1, "name" to "Supply", "quantity" to 5, "price" to 10, "itemType" to "UNIVERSAL"))
    }

    @After fun teardown() { db.close(); context.deleteDatabase(databaseName) }

    private fun seed(table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                if (column in overrides) values[column] = overrides[column]
                else if (cursor.getInt(3) != 0 && cursor.isNull(4) && column != "id") values[column] = when (cursor.getString(2)) {
                    "TEXT" -> ""; "BLOB" -> byteArrayOf(0); else -> 0
                }
            }
        }
        db.openHelper.writableDatabase.execSQL("INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})", values.values.toTypedArray())
    }

    private fun quest(id: String = "q", state: QuestState = QuestState.ACTIVE, category: QuestCategory = QuestCategory.NORMAL,
                      template: String = "supplies") = QuestInstance(id, "giver", 701, "Giver", category, template, 5, 42, 4,
        state = state, partnerId = partner, createdAt = now - 100, acceptedAt = now - 50, phaseStartedAt = now - 50)
    private fun repository() = QuestRepository(db, clock = { now })
    private fun action(q: QuestInstance, type: QuestActionType) = QuestDialogueAction(type, q.id, q.revision, "button")

    @Test fun normalRewardAndFollowUpAreAtomicAndCannotBeClaimedTwice() = runBlocking {
        val q = quest(state = QuestState.READY, template = "missing").copy(rewardBits = 50, rewardTrust = 8, followUpTemplateId = "rescue")
        seed("DigimonIndividual", mapOf("individualId" to "friend"))
        db.wildRelationshipDao().insert(WildRelationship("friend", 702, "Friend", createdAt = now, updatedAt = now))
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.MEET_TARGET, 1, progress = 1,
            targetIndividualId = "friend", targetCardCharacterId = 702, targetName = "Friend")))
        repository().act("giver", action(q, QuestActionType.TURN_IN))
        assertEquals(150, db.questDao().wallet()!!.balance)
        val child = requireNotNull(db.questDao().child("q"))
        assertEquals(1, child.chainDepth)
        assertEquals(QuestState.OFFERED, child.state)
        assertEquals(setOf("friend"), db.questDao().objectives(child.id).filter { it.type == QuestObjectiveType.MEET_TARGET }.map { it.targetIndividualId }.toSet())
        val finished = requireNotNull(db.questDao().getQuest("q"))
        assertTrue(runCatching { repository().act("giver", action(finished, QuestActionType.TURN_IN)) }.isFailure)
        repository().act("giver", action(finished, QuestActionType.STATUS))
        assertEquals(child.id, db.questDao().child("q")!!.id)
        assertEquals(150, db.questDao().wallet()!!.balance)
    }

    @Test fun failedMultiItemHandoverRollsBackEveryQuantityAndEvidence() = runBlocking {
        val q = quest()
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(
            QuestObjective("a", "q", 0, QuestObjectiveType.DELIVER_ITEM, 3, itemId = 1),
            QuestObjective("b", "q", 1, QuestObjectiveType.DELIVER_ITEM, 3, itemId = 1)))
        assertTrue(runCatching { repository().act("giver", action(q, QuestActionType.DELIVER)) }.isFailure)
        assertEquals(5, db.itemDao().questItems().single().quantity)
        assertTrue(db.questDao().objectives("q").all { it.progress == 0 })
    }

    @Test fun anotherQuestsObjectCannotSatisfyDelivery() = runBlocking {
        val q = quest(template = "courier")
        db.questDao().insertQuest(q)
        db.questDao().insertQuest(quest("other"))
        db.questDao().insertTokens(listOf(QuestToken("letter", "other", QuestTokenKind.LETTER, QuestTokenState.HELD)))
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.DELIVER_TOKEN, 1, tokenId = "letter")))
        assertTrue(runCatching { repository().act("giver", action(q, QuestActionType.DELIVER)) }.isFailure)
        assertEquals(QuestTokenState.HELD, db.questDao().token("letter")!!.state)
    }

    @Test fun aSourceEventCannotAdvanceTwoDifferentPhases() = runBlocking {
        val q = quest()
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("a", "q", 0, QuestObjectiveType.MEET_TARGET, 1),
            QuestObjective("b", "q", 1, QuestObjectiveType.MEET_TARGET, 1, phase = 1)))
        db.withTransaction {
            val progress = QuestProgress(db)
            assertTrue(progress.beginSourceLocked(q, "event", now, now))
            progress.creditLocked(q, db.questDao().objectives("q")[0], "event", 1, now)
            progress.refreshLocked("q", now)
            val next = db.questDao().getQuest("q")!!
            assertEquals(1, next.currentPhase)
            assertFalse(progress.beginSourceLocked(next, "event", now, now))
            assertFalse(progress.beginSourceLocked(next, "older", now - 1, now))
        }
        assertEquals(0, db.questDao().objectives("q")[1].progress)
    }

    @Test fun duplicateWatchReturnAndWrongIdentityDoNotAwardProgress() = runBlocking {
        val q = quest()
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.WATCH_BATTLES, 5)))
        val wire = VBNfcCharacter(dimId = 28u, charIndex = 2u, totalBattlesWon = 20u, totalBattlesLost = 5u)
        db.withTransaction { QuestProgress(db).captureExportLocked("token", partner, wire, now) }
        wire.totalBattlesWon = 23u
        wire.totalBattlesLost = 7u
        db.withTransaction { QuestProgress(db).watchImportLocked("token", "wrong", wire, "receipt", now + 1) }
        assertEquals(0, db.questDao().objectives("q").single().progress)
        db.withTransaction { QuestProgress(db).watchImportLocked("token", partner, wire, "receipt", now + 1) }
        db.withTransaction { QuestProgress(db).watchImportLocked("token", partner, wire, "receipt", now + 1) }
        assertEquals(5, db.questDao().objectives("q").single().progress)
        assertEquals(QuestState.READY, db.questDao().getQuest("q")!!.state)
    }

    @Test fun legacyRecruitmentGateCannotBypassAnIncompleteQuest() = runBlocking {
        val q = quest(category = QuestCategory.RECRUITMENT, template = "recruitment")
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.WATCH_BATTLES, 12)))
        assertTrue(WorldRepository(db).recruitIndividual("giver").isFailure)
        assertTrue(db.userCharacterDao().getByIndividualIdSync("giver").isEmpty())
    }

    @Test fun recruitmentCreatesCompleteBeProfileAndKeepsTheIndividual() = runBlocking {
        val q = quest(state = QuestState.READY, category = QuestCategory.RECRUITMENT, template = "recruitment").copy(partnerDeviceType = DeviceType.BEDevice)
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.WATCH_BATTLES, 1, progress = 1)))
        val id = WorldRepository(db).recruitIndividual("giver").getOrThrow()
        assertEquals("giver", db.userCharacterDao().getCharacter(id).individualId)
        assertEquals(DeviceType.BEDevice, db.userCharacterDao().getCharacter(id).characterType)
        assertEquals(6000, db.userCharacterDao().getBeDataOrNull(id)!!.remainingTrainingTimeInMinutes)
        assertEquals(QuestState.COMPLETED, db.questDao().getQuest("q")!!.state)
        assertTrue(WorldRepository(db).recruitIndividual("giver").isFailure)
    }

    @Test fun missingCardDataPreservesProgressAndAllowsCancellation() = runBlocking {
        val q = quest()
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.WATCH_BATTLES, 5, progress = 2)))
        db.openHelper.writableDatabase.execSQL("DELETE FROM Card WHERE id=7")
        repository().refreshGiver("giver")
        val unavailable = db.questDao().getQuest("q")!!
        assertEquals(QuestAvailabilityIssue.GIVER_CARD_MISSING, unavailable.availabilityIssue)
        assertEquals(2, db.questDao().objectives("q").single().progress)
        repository().act("giver", action(unavailable, QuestActionType.ABANDON))
        assertEquals(QuestState.ABANDONED, db.questDao().getQuest("q")!!.state)
    }

    private fun storeInactive(individualId: String) = runBlocking {
        seed("DigimonIndividual", mapOf("individualId" to individualId))
        db.userCharacterDao().insertCharacterData(UserCharacter(individualId = individualId, charId = 700, ageInDays = 1, mood = 80, vitalPoints = 100,
            transformationCountdown = 1, injuryStatus = NfcCharacter.InjuryStatus.None, trophies = 0,
            currentPhaseBattlesWon = 0, currentPhaseBattlesLost = 0, totalBattlesWon = 0, totalBattlesLost = 0,
            activityLevel = 0, heartRateCurrent = 0, characterType = DeviceType.VBDevice, isActive = false))
    }

    private fun activeIndividual(): String = runBlocking {
        val active = db.userCharacterDao().getActiveCharacter().first()!!
        db.userCharacterDao().getCharacter(active.id).individualId
    }

    @Test fun questTargetBattleFieldsTheBoundPartnerAutomatically() = runBlocking {
        storeInactive("quest-partner")
        val q = quest(template = "rival").copy(partnerId = "quest-partner")
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.DEFEAT_TARGET, 1,
            targetIndividualId = "threat", targetCardCharacterId = 701, targetName = "Threat")))
        assertEquals("Digimon", repository().useBoundPartnerForBattle("threat"))
        assertEquals("quest-partner", activeIndividual())
    }

    @Test fun untargetedBattlesSwitchOnlyForExactlyOneBattleQuest() = runBlocking {
        storeInactive("quest-partner")
        val q = quest(template = "patrol").copy(partnerId = "quest-partner")
        db.questDao().insertQuest(q)
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.RADAR_VICTORIES, 2)))
        assertEquals("Digimon", repository().useBoundPartnerForBattle("random-spawn"))
        assertEquals("quest-partner", activeIndividual())
    }

    @Test fun ambiguousBattlesNeverGuessAPartner() = runBlocking {
        storeInactive("quest-partner-a")
        storeInactive("quest-partner-b")
        db.questDao().insertQuest(quest(id = "a", template = "patrol").copy(partnerId = "quest-partner-a"))
        db.questDao().insertObjectives(listOf(QuestObjective("a0", "a", 0, QuestObjectiveType.RADAR_VICTORIES, 2)))
        db.questDao().insertQuest(quest(id = "b", template = "patrol").copy(partnerId = "quest-partner-b"))
        db.questDao().insertObjectives(listOf(QuestObjective("b0", "b", 0, QuestObjectiveType.RADAR_VICTORIES, 2)))
        assertNull(repository().useBoundPartnerForBattle("random-spawn"))
        assertEquals(partner, activeIndividual())
    }

    @Test fun missingOrAlreadyActivePartnersCauseNoSwitch() = runBlocking {
        db.questDao().insertQuest(quest(id = "c", template = "patrol").copy(partnerId = "ghost-partner"))
        db.questDao().insertObjectives(listOf(QuestObjective("c0", "c", 0, QuestObjectiveType.RADAR_VICTORIES, 2)))
        assertNull(repository().useBoundPartnerForBattle("random-spawn"))
        assertEquals(partner, activeIndividual())
        val current = db.questDao().getQuest("c")!!
        db.questDao().updateQuest(current.copy(partnerId = partner))
        assertNull(repository().useBoundPartnerForBattle("random-spawn"))
        assertEquals(partner, activeIndividual())
    }

    @Test fun questChatsExplainBlocksRolesAndPartnerAssignments() = runBlocking {
        val repo = repository()
        db.questDao().insertQuest(quest(template = "missing"))
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.MEET_TARGET, 1,
            targetIndividualId = "friend", targetCardCharacterId = 702, targetName = "Friend")))
        val participant = repo.contextForContact("friend")
        assertTrue(participant.contains("Your recorded role"))
        assertTrue(participant.contains("MEET_TARGET"))
        val partnerView = repo.contextForPartner(partner)
        assertTrue(partnerView.contains("bound partner"))
        assertTrue(partnerView.contains("q"))
        assertEquals("", repo.contextForPartner("stranger"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM Card WHERE id=7")
        repo.refreshGiver("giver")
        val blocked = repo.contextFor(db.questDao().getQuest("q")!!.id)
        assertTrue(blocked.contains("GIVER_CARD_MISSING") || blocked.contains("card data is missing"))
    }

    @Test fun storedObjectsAndBoundIdentitySurviveReopening() = runBlocking {
        db.questDao().insertQuest(quest(template = "courier"))
        db.questDao().insertObjectives(listOf(QuestObjective("o", "q", 0, QuestObjectiveType.DELIVER_TOKEN, 1, tokenId = "letter")))
        db.questDao().insertTokens(listOf(QuestToken("letter", "q", QuestTokenKind.LETTER, QuestTokenState.HELD)))
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).addCallback(IndividualIntegrity.callback).build()
        assertEquals(partner, db.questDao().getQuest("q")!!.partnerId)
        assertEquals(QuestTokenState.HELD, db.questDao().token("letter")!!.state)
    }
}
