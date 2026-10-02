package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.WorldRepository
import com.github.nacabaro.vbhelper.world.ecosystem.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldInteractionPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: WorldInteractionRepository
    private var now = 100_000L
    private val fix get() = WorldPlayerFix(GeoPoint(0.0, 0.0), now)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(IndividualIntegrity.callback).build()
        repository = WorldInteractionRepository(db) { now }
        seed("Card", mapOf("id" to 1, "name" to "Fixture"))
        seed("Sprite", mapOf("id" to 1))
        seed("CardCharacter", mapOf("id" to 100, "cardId" to 1, "spriteId" to 1, "attribute" to "Virus"))
        listOf("owned", "wild-a", "wild-b").forEach { id ->
            seed("DigimonIndividual", mapOf("individualId" to id))
            seed("ChatMessageEntity", mapOf("individualId" to id, "role" to "user", "content" to "private-$id"))
        }
        seed("UserCharacter", mapOf("id" to 10, "individualId" to "owned", "charId" to 100,
            "characterType" to "VBDevice", "injuryStatus" to "None", "isActive" to 1, "vitalPoints" to 6_000))
        listOf("wild-a", "wild-b").forEachIndexed { index, id ->
            seed("WorldSpawn", mapOf("id" to index + 1, "individualId" to id, "cardCharacterId" to 100,
                "latitude" to 0.0, "longitude" to 0.0, "expiresAt" to 101_000L, "recruitmentState" to "WILD"))
            seed("WildRelationship", mapOf("individualId" to id, "cardCharacterId" to 100,
                "trust" to 82, "recruitmentState" to "WILD"))
        }
    }

    @After fun cleanup() { db.close() }

    private fun seed(table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                if (column in overrides) values[column] = overrides[column]
                else if (cursor.getInt(3) != 0 && cursor.isNull(4) && column != "id") {
                    values[column] = when (cursor.getString(2)) { "TEXT" -> ""; "BLOB" -> byteArrayOf(0); else -> 0 }
                }
            }
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray()
        )
    }

    private suspend fun prepare(id: String = "battle", spawnId: Long = 1) =
        repository.reservePlayerBattle(id, 10, spawnId, fix, seed = 42L, tick = 17L)

    @Test fun racingReservationsHaveOneWinnerAndNoPartialParticipantRows() = runBlocking {
        val results = coroutineScope {
            listOf("first", "second").map { id -> async { runCatching { prepare(id) } } }.map { it.await() }
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, count("WorldInteraction"))
        assertEquals(2, count("WorldInteractionParticipant"))
        assertEquals(1, count("WorldParticipationClaim"))
    }

    @Test fun claimedParticipantSurvivesExpiryUntilTheBoundedInteractionEnds() = runBlocking {
        prepare()
        now += 2_000L
        db.worldSpawnDao().deleteExpired(now)
        assertNotNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertNull(db.worldSpawnDao().getByIndividualId("wild-b"))
        now += WorldInteractionPolicy.RESERVATION_MILLIS
        repository.reconcile()
        db.worldSpawnDao().deleteExpired(now)
        assertNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertEquals(0, count("WorldParticipationClaim"))
        assertEquals(InteractionState.CANCELLED, db.worldInteractionDao().getInteraction("battle")!!.state)
        assertEquals(3, count("ChatMessageEntity"))
    }

    @Test fun aWildCanOpenAChatWithoutAnyTargetAcceptance() = runBlocking {
        val greeting=repository.initiateWildPlayerInteraction("greeting",InteractionType.CHAT,1,fix,42,17)
        assertEquals(InteractionState.ACTIVE,greeting.state)
        assertEquals(listOf("wild-a"),db.worldInteractionDao().getParticipants(greeting.id).map { it.individualId })
        assertEquals(greeting.id,db.worldInteractionDao().getClaim("wild-a")!!.interactionId)
        assertEquals(0,count("WorldInteractionResult"))
        assertEquals(82,db.wildRelationshipDao().get("wild-a")!!.trust)
    }

    @Test fun wildInitiatedGreetingAndTalkShareOnePrivateHistoryWithoutDuplicatingMessages() = runBlocking {
        val greeting=repository.initiateWildPlayerInteraction("greeting",InteractionType.CHAT,1,fix,42,17)
        db.worldInteractionDao().insertMessage(WorldInteractionMessage("hello",greeting.id,1,"wild-a","Hackmon","Hello, trainer!",
            DialogueTextSource.AUTHORED,"hello",17,now))
        val memories=WorldChatMemoryRepository(db) { now }
        memories.adoptPlayerConversation(greeting.id,close=false)
        memories.adoptPlayerConversation(greeting.id,close=false)
        repository.beginPrivateChat("talk","wild-a")
        assertEquals(1,db.chatDao().getMessagesSync("wild-a").count { it.content=="Hello, trainer!" })
        assertEquals("talk",db.worldInteractionDao().getClaim("wild-a")!!.interactionId)
        assertEquals(InteractionState.ENDED,db.worldInteractionDao().getInteraction(greeting.id)!!.state)
    }

    @Test fun aFriendlyPlayerWinPreservesTheWildAndRecordsWagerMemoriesOnce() = runBlocking {
        val reserved=prepare()
        val reason="If the trainer wins Hackmon obeys; if Hackmon wins the trainer reveals their name."
        db.worldChatMemoryDao().saveContext(WorldBattleContext(reserved.id,true,"wild-a",reason,
            """[{"role":"user","speaker":"Trainer","text":"If you win I tell you my name."},{"role":"assistant","speaker":"Hackmon","text":"If you win I obey you."}]""",now))
        repository.commitPlayerBattle(reserved.id,reserved.revision,fix)
        assertTrue(repository.completeBattle(reserved.id,BattleOutcome.ALLIED_VICTORY))
        assertFalse(repository.completeBattle(reserved.id,BattleOutcome.ALLIED_VICTORY))
        assertNotNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertEquals(1,db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
        assertEquals(BattleMemoryPerspective.LOST,db.worldChatMemoryDao().getMemory(reserved.id,"wild-a")!!.perspective)
        assertEquals(BattleMemoryPerspective.WON,db.worldChatMemoryDao().getMemory(reserved.id,"owned")!!.perspective)
        assertEquals(reason,db.worldChatMemoryDao().getMemory(reserved.id,"wild-a")!!.reason)
        assertEquals(1,db.worldChatMemoryDao().getPendingReactions("wild-a").size)
        assertEquals(82,db.wildRelationshipDao().get("wild-a")!!.trust)
    }

    @Test fun wildAttackHandoffRestoresItsSourceIfPreparationFailsAndRecordsOneResultWhenCommitted() = runBlocking {
        val attack=repository.initiateWildPlayerInteraction("attack",InteractionType.BATTLE,1,fix,42,17)
        assertTrue(runCatching { repository.reserveWildAttack("early",attack.id,attack.revision,10,fix,20) }.isFailure)
        val reserved=repository.reserveWildAttack("defense",attack.id,attack.revision,10,fix,21)
        assertEquals("defense",db.worldInteractionDao().getClaim("wild-a")!!.interactionId)
        assertNull(db.worldInteractionDao().getNpcBattle(attack.id))
        repository.cancel(reserved.id)
        val restored=db.worldInteractionDao().getInteraction(attack.id)!!
        assertEquals(InteractionState.ACTIVE,restored.state)
        assertEquals(attack.seed,restored.seed)
        val committed=repository.reserveWildAttack("retry",restored.id,restored.revision,10,fix,21)
        assertTrue(repository.commitPlayerBattle(committed.id,committed.revision,fix))
        assertTrue(repository.completeBattle(committed.id,BattleOutcome.ALLIED_VICTORY))
        assertFalse(repository.completeBattle(committed.id,BattleOutcome.ALLIED_VICTORY))
        assertEquals(1,db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
        assertEquals(InteractionState.CANCELLED,db.worldInteractionDao().getInteraction(attack.id)!!.state)
        assertNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertNotNull(db.worldSpawnDao().getByIndividualId("wild-b"))
    }

    @Test fun anActivePartnerChangeDuringWildAttackPreparationRejectsTheOldRoster() = runBlocking {
        val attack=repository.initiateWildPlayerInteraction("attack",InteractionType.BATTLE,1,fix,42,17)
        val reserved=repository.reserveWildAttack("defense",attack.id,attack.revision,10,fix,21)
        db.openHelper.writableDatabase.execSQL("UPDATE UserCharacter SET isActive=0 WHERE id=10")
        assertTrue(runCatching { repository.commitPlayerBattle(reserved.id,reserved.revision,fix,10) }.isFailure)
        assertEquals(InteractionState.RESERVED,db.worldInteractionDao().getInteraction(reserved.id)!!.state)
        assertEquals(0,count("WorldInteractionResult"))
        repository.cancel(reserved.id)
        assertEquals(attack.id,db.worldInteractionDao().getClaim("wild-a")!!.interactionId)
    }

    @Test fun acceptedSingleWildSparringReturnsThroughTheSamePlayerBattleOwner() = runBlocking {
        val conversation=repository.initiateWildPlayerInteraction("greeting",InteractionType.CHAT,1,fix,42,17)
        val sparring=db.withTransaction { repository.transferWildAttackLocked(conversation,"wild-a","Friendly practice",18,sparring=true) }!!
        assertTrue(sparring.isPlayerBattle)
        assertFalse(sparring.isWildAttack)
        val prepared=repository.reserveWildAttack("sparring",sparring.id,sparring.revision,10,fix,22)
        assertTrue(repository.commitPlayerBattle(prepared.id,prepared.revision,fix,10))
        assertTrue(repository.completeBattle(prepared.id,BattleOutcome.DRAW))
        assertEquals(0,db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
        assertNotNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertEquals(InteractionState.ENDED,db.worldInteractionDao().getInteraction(conversation.id)!!.state)
    }

    @Test fun lossesAreRecordedExactlyOnceEvenThoughTheEncounterRemains() = runBlocking {
        val reserved = prepare()
        assertTrue(repository.commitPlayerBattle("battle", reserved.revision, fix))
        assertTrue(repository.completeBattle("battle", BattleOutcome.OPPOSING_VICTORY))
        assertFalse(repository.completeBattle("battle", BattleOutcome.OPPOSING_VICTORY))
        assertFalse(repository.completeBattle("battle", BattleOutcome.ALLIED_VICTORY))
        assertEquals(1, db.userCharacterDao().getCharacterSync(10)!!.totalBattlesLost)
        assertEquals(0, db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
        assertNotNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertEquals(1, count("WorldInteractionResult"))
        assertEquals(0, count("WorldParticipationClaim"))
    }

    @Test fun victoryConsumesTheEnemyButKeepsArchivedParticipantIdentityAndPrivateHistory() = runBlocking {
        val reserved = prepare()
        repository.commitPlayerBattle("battle", reserved.revision, fix)
        repository.completeBattle("battle", BattleOutcome.ALLIED_VICTORY)
        assertNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        val wild = db.worldInteractionDao().getParticipants("battle").single { it.individualId == "wild-a" }
        assertNull(wild.spawnId)
        assertEquals("wild-a", wild.individualId)
        assertEquals(3, count("ChatMessageEntity"))
        assertEquals(1, db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
    }

    @Test fun staleRevisionCannotCommitAReservationAndCancellationIsIdempotent() = runBlocking {
        val reserved = prepare()
        assertFalse(repository.commitPlayerBattle("battle", reserved.revision - 1, fix))
        assertTrue(repository.cancel("battle"))
        assertFalse(repository.cancel("battle"))
        assertFalse(repository.commitPlayerBattle("battle", reserved.revision, fix))
        assertEquals(0, count("WorldParticipationClaim"))
        assertEquals(0, count("WorldInteractionResult"))
    }

    @Test fun privateChatOwnsTheSameWildClaimAndCannotShareItWithABattle() = runBlocking {
        val chat = repository.beginPrivateChat("chat", "wild-a")!!
        assertTrue(runCatching { prepare() }.isFailure)
        repository.finishPrivateChat(chat.id, "wild-a")
        prepare()
        assertEquals(1, count("WorldParticipationClaim"))
        assertEquals(InteractionState.ENDED, db.worldInteractionDao().getInteraction("chat")!!.state)
    }

    @Test fun cardDeletionCancelsAffectedEventsAndReleasesEveryClaim() = runBlocking {
        prepare()
        db.cardDao().deleteCard(1)
        assertEquals(InteractionState.CANCELLED, db.worldInteractionDao().getInteraction("battle")!!.state)
        assertEquals(0, count("WorldParticipationClaim"))
        assertFalse(repository.completeBattle("battle", BattleOutcome.ALLIED_VICTORY))
        assertEquals(3, count("ChatMessageEntity"))
        assertEquals(0, db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { it.count })
    }

    @Test fun interruptedPlayerOwnershipCannotAwardAResultAfterRecovery() = runBlocking {
        val reserved = prepare()
        repository.commitPlayerBattle("battle", reserved.revision, fix)
        repository.recoverInterruptedPlayerBattles()
        assertEquals(InteractionState.INTERRUPTED, db.worldInteractionDao().getInteraction("battle")!!.state)
        assertEquals(0, count("WorldParticipationClaim"))
        assertFalse(repository.completeBattle("battle", BattleOutcome.ALLIED_VICTORY))
        assertEquals(0, db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
    }

    @Test fun staleLocationRejectsBattleBeforeCreatingAnyPersistentEvent() = runBlocking {
        val stale = WorldPlayerFix(GeoPoint(0.0, 0.0), now - 30_001L)
        assertTrue(runCatching { repository.reservePlayerBattle("battle", 10, 1, stale, 42L, 17L) }.isFailure)
        assertEquals(0, count("WorldInteraction"))
        assertEquals(0, count("WorldParticipationClaim"))
    }

    @Test fun claimedWildCannotBeRecruitedOrMovedToPendingByAnotherPlayerPath() = runBlocking {
        prepare()
        val world = WorldRepository(db) { now }
        assertTrue(world.recruitSpawn(1).isFailure)
        assertTrue(world.recruitIndividual("wild-a").isFailure)
        assertTrue(runCatching { world.markPendingRecruitment("wild-a") }.isFailure)
        assertTrue(runCatching { world.applyWildMoodDelta("wild-a", 20) }.isFailure)
        assertEquals(82, db.wildRelationshipDao().get("wild-a")!!.trust)
        assertEquals(1, count("WorldParticipationClaim"))
        assertEquals(1, count("UserCharacter"))
    }

    @Test fun failedEffectApplicationRollsBackTheResultLedgerAndAllParticipantEffects() = runBlocking {
        val reserved = prepare()
        repository.commitPlayerBattle("battle", reserved.revision, fix)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_battle_update BEFORE UPDATE OF totalBattlesWon ON UserCharacter
            BEGIN SELECT RAISE(ABORT, 'Injected result failure'); END
        """.trimIndent())
        assertTrue(runCatching { repository.completeBattle("battle", BattleOutcome.ALLIED_VICTORY) }.isFailure)
        assertEquals(0, count("WorldInteractionResult"))
        assertEquals(0, db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
        assertEquals(1, count("WorldParticipationClaim"))
        assertNotNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertEquals(InteractionState.PLAYER_CONTROLLED, db.worldInteractionDao().getInteraction("battle")!!.state)
    }

    @Test fun autonomousOutcomesRetainBothWildsAndCannotAwardOrChangePlayerTrust() = runBlocking {
        val proposed = repository.proposeNpcInteraction("npc", InteractionType.BATTLE, listOf(2, 1), 42L, 17L)
        assertTrue(repository.activateNpcInteraction("npc", proposed.revision, 18L))
        assertTrue(repository.completeBattle("npc", BattleOutcome.ALLIED_VICTORY))
        assertEquals(2, count("WorldSpawn"))
        assertEquals(82, db.wildRelationshipDao().get("wild-a")!!.trust)
        assertEquals(82, db.wildRelationshipDao().get("wild-b")!!.trust)
        assertEquals(0, db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
        assertEquals(0, count("WorldParticipationClaim"))
    }

    @Test fun unknownEventRulesCancelOwnershipWithoutReplayingAnOutcome() = runBlocking {
        val reserved = prepare()
        db.worldInteractionDao().updateInteraction(reserved.copy(rulesVersion = 99))
        assertFalse(repository.commitPlayerBattle("battle", reserved.revision, fix))
        assertEquals(InteractionState.CANCELLED, db.worldInteractionDao().getInteraction("battle")!!.state)
        assertEquals("UNSUPPORTED_RULES", db.worldInteractionDao().getInteraction("battle")!!.terminalReason)
        assertEquals(0, count("WorldParticipationClaim"))
    }

    @Test fun proposedNeighborsCannotActivateBeforePhysicallyMeeting() = runBlocking {
        val proposed = repository.proposeNpcInteraction("npc", InteractionType.CHAT, listOf(1, 2), 42L, 17L)
        db.openHelper.writableDatabase.execSQL("UPDATE WorldSpawn SET latitude=0.0001, homeLatitude=0.0001 WHERE id=2")
        assertFalse(repository.activateNpcInteraction("npc", proposed.revision, 18L))
        assertEquals(InteractionState.PROPOSED, db.worldInteractionDao().getInteraction("npc")!!.state)
        assertEquals(2, count("WorldParticipationClaim"))
    }

    @Test fun privateReplyCannotChangeTrustAfterItsClaimExpires() = runBlocking {
        val chat = repository.beginPrivateChat("chat", "wild-a")!!
        now += WorldInteractionPolicy.MAX_CHAT_MILLIS + 1
        val world = WorldRepository(db) { now }
        assertTrue(runCatching { world.applyWildMoodDelta("wild-a", 20, chat.id) }.isFailure)
        assertEquals(82, db.wildRelationshipDao().get("wild-a")!!.trust)
        repository.reconcile()
        assertEquals(0, count("WorldParticipationClaim"))
    }

    @Test fun commandCheckpointFailureCannotLeaveAHalfReservedBattle() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val world = WorldEcosystemCoordinator(RoomWorldEcosystemStore(db) { now }, scope, { now }, { 42L })
            world.acquire("radar")
            world.acceptPlayerFix("radar", world.snapshot.value.leaseEpoch, fix)
            val checkpoint = db.worldEcosystemDao().getSession()
            db.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER reject_world_checkpoint BEFORE UPDATE ON WorldEcosystemSession
                BEGIN SELECT RAISE(ABORT, 'Injected checkpoint failure'); END
            """.trimIndent())
            val result = world.executeCommand("radar", RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.BATTLE_RESERVATION, "wild-a")) { command ->
                repository.reservePlayerBattle("gated", 10, 1, command.playerFix, 42L, command.session.tickIndex)
            }
            assertTrue(result is RadarCommandResult.Failed)
            assertNull(db.worldInteractionDao().getInteraction("gated"))
            assertEquals(0, count("WorldParticipationClaim"))
            assertEquals(checkpoint, db.worldEcosystemDao().getSession())
            assertEquals(EcosystemStatus.FAILED, world.snapshot.value.status)
        } finally { scope.cancel() }
    }

    @Test fun ownedEvolutionDuringPreparationCannotCommitAnOlderRoster() = runBlocking {
        val reserved = prepare()
        seed("CardCharacter", mapOf("id" to 101, "cardId" to 1, "spriteId" to 1, "attribute" to "Virus"))
        db.openHelper.writableDatabase.execSQL("UPDATE UserCharacter SET charId=101 WHERE id=10")
        assertTrue(runCatching { repository.commitPlayerBattle("battle", reserved.revision, fix) }.isFailure)
        assertEquals(InteractionState.RESERVED, db.worldInteractionDao().getInteraction("battle")!!.state)
        assertEquals(0, count("WorldInteractionResult"))
    }

    private suspend fun npcSource():WorldInteraction {
        val event=repository.proposeNpcInteraction("npc",InteractionType.BATTLE,listOf(1,2),42,17)
        repository.activateNpcInteraction(event.id,event.revision,18)
        val definitions=listOf(
            com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory.definition(
                com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput("wild-a",displayName="A",stage=3,maxHealth=2000,attack=1000),
                com.github.nacabaro.vbhelper.battle.offline.core.BattleSide.ALLIED),
            com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory.definition(
                com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput("wild-b",displayName="B",stage=3,maxHealth=2000,attack=1000),
                com.github.nacabaro.vbhelper.battle.offline.core.BattleSide.OPPOSING))
        val record=WorldNpcBattle(event.id,com.google.gson.Gson().toJson(definitions),startTick=18,nextRoundTick=18)
        db.worldInteractionDao().saveNpcBattle(record)
        return db.worldInteractionDao().getInteraction(event.id)!!
    }

    @Test fun failedJoinedPreparationRestoresTheExactNpcBattleSeedAndCheckpoint() = runBlocking {
        val source=npcSource()
        val checkpoint=db.worldInteractionDao().getNpcBattle(source.id)
        repository.reserveJoinedBattle("joined",source.id,source.revision,10,null,"wild-a",fix,18)
        assertTrue(repository.cancel("joined"))
        val restored=db.worldInteractionDao().getInteraction(source.id)!!
        assertEquals(InteractionState.ACTIVE,restored.state)
        assertEquals(source.seed,restored.seed)
        assertEquals(checkpoint,db.worldInteractionDao().getNpcBattle(source.id))
        assertEquals(2,count("WorldParticipationClaim"))
        assertEquals(source.id,db.worldInteractionDao().getClaim("wild-a")!!.interactionId)
    }

    @Test fun joinedPreparationExpiryRestoresTheNpcEvenWhenTheWholeTimeoutWasMissed() = runBlocking {
        val source=npcSource()
        val checkpoint=db.worldInteractionDao().getNpcBattle(source.id)
        repository.reserveJoinedBattle("joined",source.id,source.revision,10,null,"wild-a",fix,18)
        now+=WorldInteractionPolicy.MAX_BATTLE_MILLIS+1
        repository.reconcile()
        assertEquals(InteractionState.CANCELLED,db.worldInteractionDao().getInteraction("joined")!!.state)
        assertEquals(InteractionState.ACTIVE,db.worldInteractionDao().getInteraction(source.id)!!.state)
        assertEquals(checkpoint,db.worldInteractionDao().getNpcBattle(source.id))
        assertEquals(source.id,db.worldInteractionDao().getClaim("wild-a")!!.interactionId)
    }

    @Test fun explicitProcessRecoveryRestoresPreparationButNeverRestartsACommittedFight() = runBlocking {
        val source=npcSource()
        repository.reserveJoinedBattle("joined",source.id,source.revision,10,null,"wild-a",fix,18)
        repository.recoverInterruptedPlayerBattles()
        val restored=db.worldInteractionDao().getInteraction(source.id)!!
        assertEquals(InteractionState.ACTIVE,restored.state)
        assertEquals(InteractionState.CANCELLED,db.worldInteractionDao().getInteraction("joined")!!.state)
        val committed=repository.reserveJoinedBattle("committed",source.id,restored.revision,10,null,"wild-a",fix,18)
        repository.commitPlayerBattle(committed.id,committed.revision,fix)
        repository.recoverInterruptedPlayerBattles()
        assertEquals(InteractionState.INTERRUPTED,db.worldInteractionDao().getInteraction(committed.id)!!.state)
        assertEquals(InteractionState.CANCELLED,db.worldInteractionDao().getInteraction(source.id)!!.state)
        assertEquals(0,count("WorldParticipationClaim"))
        assertEquals(0,count("WorldInteractionResult"))
    }

    @Test fun sideWithWildRecordsOwnedResultOnceAndDoesNotDeleteTheAlliedWild() = runBlocking {
        val source=npcSource()
        val joined=repository.reserveJoinedBattle("joined",source.id,source.revision,10,null,"wild-a",fix,18)
        repository.commitPlayerBattle(joined.id,joined.revision,fix)
        assertTrue(repository.completeBattle(joined.id,BattleOutcome.ALLIED_VICTORY))
        assertFalse(repository.completeBattle(joined.id,BattleOutcome.ALLIED_VICTORY))
        assertNotNull(db.worldSpawnDao().getByIndividualId("wild-a"))
        assertNull(db.worldSpawnDao().getByIndividualId("wild-b"))
        assertEquals(1,db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
    }

    @Test fun challengeBothRequiresDistinctOwnedPartnersAndRecordsOneResultEach() = runBlocking {
        val source=npcSource()
        assertTrue(runCatching { repository.reserveJoinedBattle("missing",source.id,source.revision,10,null,null,fix,18) }.isFailure)
        seed("DigimonIndividual",mapOf("individualId" to "owned-two"))
        seed("UserCharacter",mapOf("id" to 11,"individualId" to "owned-two","charId" to 100,"characterType" to "VBDevice","injuryStatus" to "None"))
        val joined=repository.reserveJoinedBattle("joined",source.id,source.revision,10,11,null,fix,18)
        val participants=db.worldInteractionDao().getParticipants(joined.id)
        assertEquals(2,participants.count { it.role==InteractionRole.OWNED && it.side==InteractionSide.ALLIED })
        assertEquals(2,participants.count { it.role==InteractionRole.WILD && it.side==InteractionSide.OPPOSING })
        assertTrue(repository.commitPlayerBattle(joined.id,joined.revision,fix))
        assertTrue(repository.completeBattle(joined.id,BattleOutcome.ALLIED_VICTORY))
        assertEquals(1,db.userCharacterDao().getCharacterSync(10)!!.totalBattlesWon)
        assertEquals(1,db.userCharacterDao().getCharacterSync(11)!!.totalBattlesWon)
        assertEquals(0,count("WorldSpawn"))
    }

    private fun count(table: String) = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use {
        it.moveToFirst(); it.getInt(0)
    }
}
