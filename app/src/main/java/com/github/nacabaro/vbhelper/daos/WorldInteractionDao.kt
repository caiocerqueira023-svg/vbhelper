package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteraction
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionParticipant
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionResult
import com.github.nacabaro.vbhelper.world.ecosystem.WorldParticipationClaim
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionMessage
import com.github.nacabaro.vbhelper.world.ecosystem.WorldDialogueIntent
import com.github.nacabaro.vbhelper.world.ecosystem.WorldNpcBattle
import androidx.room.Upsert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldInteractionDao {
    @Query("SELECT * FROM WorldInteraction WHERE id = :id")
    suspend fun getInteraction(id: String): WorldInteraction?

    @Query("SELECT * FROM WorldInteraction WHERE state NOT IN ('ENDED', 'CANCELLED', 'INTERRUPTED') ORDER BY id")
    suspend fun getOpenInteractions(): List<WorldInteraction>
    @Query("SELECT MAX(startTick) FROM WorldInteraction WHERE publicReason LIKE 'WILD_%'")
    suspend fun getLatestPlayerInitiationTick(): Long?
    @Query("SELECT MAX(e.startTick) FROM WorldInteraction e JOIN WorldInteractionParticipant p ON p.interactionId=e.id WHERE p.individualId=:id AND e.publicReason LIKE 'WILD_%'")
    suspend fun getLatestPlayerInitiationTick(id: String): Long?
    @Query("SELECT * FROM WorldInteraction WHERE NOT (type='CHAT' AND origin='DIRECT_PLAYER') ORDER BY createdAt DESC,id LIMIT 100")
    fun observeRecentPublicInteractions(): Flow<List<WorldInteraction>>

    @Query("SELECT * FROM WorldInteractionParticipant WHERE interactionId = :id ORDER BY individualId")
    suspend fun getParticipants(id: String): List<WorldInteractionParticipant>

    @Query("SELECT * FROM WorldParticipationClaim WHERE individualId = :id")
    suspend fun getClaim(id: String): WorldParticipationClaim?

    @Query("SELECT individualId FROM WorldParticipationClaim")
    suspend fun getClaimedIndividuals(): List<String>

    @Query("SELECT * FROM WorldInteractionResult WHERE interactionId = :id")
    suspend fun getResult(id: String): WorldInteractionResult?

    @Query("SELECT * FROM WorldInteractionMessage WHERE interactionId=:id ORDER BY sequence") fun observeMessages(id: String): Flow<List<WorldInteractionMessage>>
    @Query("SELECT * FROM WorldInteractionMessage WHERE interactionId=:id ORDER BY sequence") suspend fun getMessages(id: String): List<WorldInteractionMessage>
    @Query("SELECT * FROM WorldDialogueIntent WHERE interactionId=:id ORDER BY id") fun observeIntents(id: String): Flow<List<WorldDialogueIntent>>
    @Query("SELECT * FROM WorldDialogueIntent WHERE status='PENDING' ORDER BY id") suspend fun getPendingIntents(): List<WorldDialogueIntent>
    @Query("SELECT * FROM WorldDialogueIntent WHERE id=:id") suspend fun getIntent(id: String): WorldDialogueIntent?
    @Query("SELECT * FROM WorldInteraction WHERE id=:id") fun observeInteraction(id: String): Flow<WorldInteraction?>
    @Query("SELECT * FROM WorldInteraction WHERE parentInteractionId=:id AND type='BATTLE' AND state NOT IN ('ENDED','CANCELLED','INTERRUPTED') ORDER BY createdAt DESC,id LIMIT 1")
    fun observeConversationBattle(id: String): Flow<WorldInteraction?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessage(message: WorldInteractionMessage): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIntent(intent: WorldDialogueIntent): Long
    @Update suspend fun updateIntent(intent: WorldDialogueIntent)
    @Upsert suspend fun saveNpcBattle(battle: WorldNpcBattle)
    @Query("SELECT * FROM WorldNpcBattle WHERE interactionId=:id") suspend fun getNpcBattle(id: String): WorldNpcBattle?
    @Query("SELECT * FROM WorldNpcBattle WHERE interactionId=:id") fun observeNpcBattle(id: String): Flow<WorldNpcBattle?>

    @Insert suspend fun insertInteraction(interaction: WorldInteraction)
    @Insert suspend fun insertParticipants(participants: List<WorldInteractionParticipant>)
    @Insert suspend fun insertClaims(claims: List<WorldParticipationClaim>)
    @Insert suspend fun insertResult(result: WorldInteractionResult)
    @Update suspend fun updateInteraction(interaction: WorldInteraction): Int
    @Update suspend fun updateResult(result: WorldInteractionResult): Int

    @Query("DELETE FROM WorldParticipationClaim WHERE interactionId = :id")
    suspend fun releaseClaims(id: String)

    @Query("""
        DELETE FROM WorldInteraction
        WHERE state IN ('ENDED','CANCELLED','INTERRUPTED')
            AND NOT EXISTS(SELECT 1 FROM WorldInteraction child WHERE child.parentInteractionId = WorldInteraction.id
                AND child.state NOT IN ('ENDED','CANCELLED','INTERRUPTED'))
            AND (endedAt < :cutoff OR id NOT IN (
                SELECT id FROM WorldInteraction WHERE state IN ('ENDED','CANCELLED','INTERRUPTED')
                ORDER BY endedAt DESC, id LIMIT 100
            ))
    """)
    suspend fun pruneEndedInteractions(cutoff: Long)
}
