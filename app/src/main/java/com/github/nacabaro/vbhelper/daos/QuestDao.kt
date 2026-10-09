package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.github.nacabaro.vbhelper.quests.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Insert fun insertQuest(quest: QuestInstance)
    @Insert fun insertObjectives(objectives: List<QuestObjective>)
    @Update fun updateQuest(quest: QuestInstance): Int
    @Update fun updateObjective(objective: QuestObjective): Int
    @Query("SELECT * FROM QuestInstance WHERE id = :id") fun getQuest(id: String): QuestInstance?
    @Query("SELECT * FROM QuestObjective WHERE questId = :id ORDER BY position") fun objectives(id: String): List<QuestObjective>
    @Query("SELECT stage FROM CardCharacter WHERE id = :id") fun stageForCardCharacter(id: Long): Int?
    @Query("SELECT cardId FROM CardCharacter WHERE id = :id") fun cardForCharacter(id: Long): Long?
    @Query("SELECT cc.cardId FROM UserCharacter uc JOIN CardCharacter cc ON cc.id = uc.charId WHERE uc.individualId = :id LIMIT 1")
    fun cardForIndividual(id: String): Long?
    @Query("SELECT id FROM CardCharacter ORDER BY id") fun observeCardCharacters(): Flow<List<Long>>
    @Query("SELECT * FROM QuestInstance WHERE giverId = :giverId ORDER BY createdAt DESC") fun forGiver(giverId: String): List<QuestInstance>
    @Query("SELECT * FROM QuestInstance WHERE parentQuestId = :parentId LIMIT 1") fun child(parentId: String): QuestInstance?
    @Query("SELECT * FROM QuestInstance WHERE giverId = :giverId AND state = 'COMPLETED' AND followUpTemplateId IS NOT NULL AND followUpQuestId IS NULL AND followUpClosed = 0 ORDER BY finishedAt, createdAt")
    fun pendingFollowUps(giverId: String): List<QuestInstance>
    @Query("SELECT * FROM QuestInstance WHERE state IN ('ACTIVE', 'READY')") fun active(): List<QuestInstance>
    @Query("SELECT * FROM QuestInstance WHERE partnerId = :individualId AND state IN ('ACTIVE', 'READY')")
    fun forPartner(individualId: String): List<QuestInstance>
    @Transaction
    @Query("SELECT * FROM QuestInstance ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuestWithObjectives>>
    @Transaction
    @Query("SELECT * FROM QuestInstance WHERE giverId = :giverId ORDER BY createdAt DESC")
    fun observeGiver(giverId: String): Flow<List<QuestWithObjectives>>
    @Transaction
    @Query("""SELECT * FROM QuestInstance q WHERE q.giverId = :individualId OR
        (q.state IN ('ACTIVE','READY') AND EXISTS(SELECT 1 FROM QuestObjective o
            WHERE o.questId = q.id AND o.targetIndividualId = :individualId)) ORDER BY q.createdAt DESC""")
    fun observeContact(individualId: String): Flow<List<QuestWithObjectives>>
    @Query("SELECT COUNT(*) FROM QuestObjective o JOIN QuestInstance q ON q.id = o.questId WHERE o.targetIndividualId = :individualId AND o.progress < o.required AND q.state IN ('ACTIVE', 'READY') AND q.availabilityIssue IS NULL")
    fun unfinishedTargetCount(individualId: String): Int
    @Query("SELECT o.* FROM QuestObjective o JOIN QuestInstance q ON q.id = o.questId WHERE o.targetIndividualId IS NOT NULL AND o.progress < o.required AND q.state = 'ACTIVE' AND o.phase = q.currentPhase AND q.availabilityIssue IS NULL")
    fun pendingTargets(): List<QuestObjective>
    @Query("""SELECT q.id AS questId, o.id AS objectiveId, q.revision AS revision, o.type AS type,
        q.giverId AS giverId, q.giverName AS giverName, o.targetIndividualId AS targetIndividualId,
        o.targetName AS targetName, o.phase AS phase, o.itemName AS itemName, o.required - o.progress AS itemQuantity,
        CASE WHEN o.type = 'DELIVER_TOKEN' THEN EXISTS(SELECT 1 FROM QuestToken t
            WHERE t.id = o.tokenId AND t.questId = q.id AND t.state = 'HELD')
            WHEN o.type = 'DELIVER_ITEM' THEN EXISTS(SELECT 1 FROM Items i WHERE i.id = o.itemId AND i.quantity >= o.required - o.progress)
            ELSE 1 END AS canInteract
        FROM QuestObjective o JOIN QuestInstance q ON q.id = o.questId
        WHERE q.state = 'ACTIVE' AND o.phase = q.currentPhase AND o.progress < o.required
        AND o.targetIndividualId IS NOT NULL AND o.type IN ('MEET_TARGET','RECOVER_PROPERTY','DELIVER_TOKEN','DELIVER_ITEM')""")
    fun observeTargetTasks(): Flow<List<QuestTargetTask>>
    @Query("""SELECT o.*, q.giverName AS giverName FROM QuestObjective o
        JOIN QuestInstance q ON q.id = o.questId WHERE q.state = 'ACTIVE' AND o.phase = q.currentPhase
        AND o.progress < o.required AND o.targetIndividualId IS NOT NULL AND o.type = 'WIN_BATTLE_CONDITION'""")
    fun observeBattleTargets(): Flow<List<QuestBattleTargetTask>>
    @Query("""SELECT o.*, q.giverName AS giverName FROM QuestObjective o
        JOIN QuestInstance q ON q.id = o.questId WHERE q.state = 'ACTIVE' AND o.phase = q.currentPhase
        AND o.progress < o.required AND o.targetIndividualId = :individualId
        AND o.type IN ('DEFEAT_TARGET','WIN_BATTLE_CONDITION') LIMIT 1""")
    fun battleTargetFor(individualId: String): QuestBattleTargetTask?
    @Query("""SELECT uc.individualId AS individualId, uc.id AS characterId,
        COALESCE(NULLIF(di.nickname,''), NULLIF(sp.speciesName,''), NULLIF(sp.matchedName,''), 'Digimon') AS displayName,
        cc.stage AS stage, uc.characterType AS deviceType, uc.isActive AS isActive, cc.cardId AS cardId
        FROM UserCharacter uc JOIN CardCharacter cc ON cc.id = uc.charId
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        ORDER BY uc.isActive DESC, displayName COLLATE NOCASE, uc.id DESC""")
    fun observePartners(): Flow<List<QuestPartnerOption>>
    @Query("""SELECT uc.individualId AS individualId, uc.id AS characterId,
        COALESCE(NULLIF(di.nickname,''), NULLIF(sp.speciesName,''), NULLIF(sp.matchedName,''), 'Digimon') AS displayName,
        cc.stage AS stage, uc.characterType AS deviceType, uc.isActive AS isActive, cc.cardId AS cardId
        FROM UserCharacter uc JOIN CardCharacter cc ON cc.id = uc.charId
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        ORDER BY uc.isActive DESC, uc.id DESC""")
    fun partners(): List<QuestPartnerOption>
    @Query("SELECT totalTrophies FROM VBCharacterData WHERE id = :characterId")
    fun lifetimeTrophies(characterId: Long): Int?
    @Insert fun insertTokens(tokens: List<QuestToken>)
    @Query("SELECT * FROM QuestToken WHERE id = :id") fun token(id: String): QuestToken?
    @Query("SELECT * FROM QuestToken WHERE questId = :id") fun tokens(id: String): List<QuestToken>
    @Query("UPDATE QuestToken SET state = 'HELD', acquiredAt = :now WHERE id = :id AND questId = :questId AND state = 'LOCKED'")
    fun acquireToken(id: String, questId: String, now: Long): Int
    @Query("UPDATE QuestToken SET state = 'DELIVERED', deliveredAt = :now WHERE id = :id AND questId = :questId AND state = 'HELD'")
    fun deliverToken(id: String, questId: String, now: Long): Int
    @Query("UPDATE QuestToken SET state = 'VOID' WHERE questId = :questId AND state IN ('LOCKED','HELD')")
    fun voidTokens(questId: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun recordPhase(receipt: QuestPhaseReceipt): Long
    @Query("SELECT * FROM QuestPhaseReceipt WHERE questId = :questId AND sourceId = :sourceId")
    fun phaseReceipt(questId: String, sourceId: String): QuestPhaseReceipt?
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertBattleReport(report: QuestBattleReport): Long
    @Insert fun insertBattleMembers(members: List<QuestBattleMember>)
    @Insert fun insertBattleHits(hits: List<QuestBattleTechniqueHit>)
    @Query("SELECT * FROM QuestBattleReport WHERE interactionId = :id") fun battleReport(id: String): QuestBattleReport?
    @Query("SELECT * FROM QuestBattleMember WHERE interactionId = :id AND individualId = :individualId")
    fun battleMember(id: String, individualId: String): QuestBattleMember?
    @Query("SELECT SUM(hits) FROM QuestBattleTechniqueHit WHERE interactionId = :id AND actorId = :actorId AND techniqueId = :techniqueId AND (:targetId IS NULL OR targetId = :targetId)")
    fun techniqueHits(id: String, actorId: String, techniqueId: String, targetId: String?): Int?
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun recordBattleAttempt(attempt: QuestBattleAttempt): Long
    @Query("SELECT * FROM QuestBattleAttempt WHERE questId = :id ORDER BY recordedAt DESC")
    fun battleAttempts(id: String): List<QuestBattleAttempt>
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun recordEvidence(evidence: QuestEvidence): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun recordReward(receipt: QuestRewardReceipt): Long
    @Query("SELECT * FROM QuestRewardReceipt WHERE questId = :id") fun reward(id: String): QuestRewardReceipt?
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertBaseline(baseline: QuestWatchBaseline): Long
    @Query("SELECT * FROM QuestWatchBaseline WHERE token = :token") fun baseline(token: String): QuestWatchBaseline?
    @Query("DELETE FROM QuestWatchBaseline WHERE token = :token") fun deleteBaseline(token: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun initializeWallet(wallet: QuestWallet): Long
    @Query("SELECT * FROM QuestWallet WHERE id = 1") fun wallet(): QuestWallet?
    @Query("SELECT balance FROM QuestWallet WHERE id = 1") fun observeBalance(): Flow<Int?>
    @Query("UPDATE QuestWallet SET balance = :balance WHERE id = 1") fun setBalance(balance: Int): Int
    @Query("UPDATE QuestWallet SET balance = balance + :amount WHERE id = 1 AND balance <= 2147483647 - :amount")
    fun addBits(amount: Int): Int
    @Query("UPDATE QuestWallet SET balance = balance - :amount WHERE id = 1 AND :amount >= 0 AND balance >= :amount")
    fun spendBits(amount: Int): Int
    @Query("SELECT * FROM QuestBattleStock ORDER BY itemId") fun battleStock(): List<QuestBattleStock>
    @Query("SELECT * FROM QuestBattleStock ORDER BY itemId") fun observeBattleStock(): Flow<List<QuestBattleStock>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun initializeStock(stock: QuestBattleStock): Long
    @Query("UPDATE QuestBattleStock SET quantity = quantity + :amount WHERE itemId = :itemId AND quantity + :amount BETWEEN 0 AND 2147483647")
    fun adjustStock(itemId: String, amount: Int): Int
    @Insert fun reserve(reservations: List<QuestBattleReservation>)
    @Query("SELECT * FROM QuestBattleReservation WHERE interactionId = :id") fun reservations(id: String): List<QuestBattleReservation>
    @Query("UPDATE QuestBattleReservation SET used = :used WHERE interactionId = :id AND itemId = :itemId AND used <= :used AND quantity >= :used")
    fun recordUsed(id: String, itemId: String, used: Int): Int
    @Query("DELETE FROM QuestBattleReservation WHERE interactionId = :id") fun clearReservations(id: String)
}
