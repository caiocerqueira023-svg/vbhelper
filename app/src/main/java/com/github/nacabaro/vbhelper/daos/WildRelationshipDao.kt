package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.digifarm.WildRelationship
import com.github.nacabaro.vbhelper.dtos.DigilineWildThread
import kotlinx.coroutines.flow.Flow

@Dao
interface WildRelationshipDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(relationship: WildRelationship)

    @Query("SELECT * FROM WildRelationship WHERE individualId = :individualId")
    suspend fun get(individualId: String): WildRelationship?

    @Query("SELECT trust FROM WildRelationship WHERE individualId = :individualId")
    fun observeTrust(individualId: String): Flow<Int?>

    @Query("UPDATE WildRelationship SET trust = :trust, contactUnlockedAt = CASE WHEN contactUnlockedAt IS NULL AND :trust > 75 THEN :now ELSE contactUnlockedAt END, updatedAt = :now WHERE individualId = :individualId")
    suspend fun updateTrust(individualId: String, trust: Int, now: Long)

    @Query("UPDATE WildRelationship SET recruitmentState = :state, updatedAt = :now WHERE individualId = :individualId")
    suspend fun updateRecruitmentState(individualId: String, state: String, now: Long)

    /**
     * Atomically claims an unlocked contact for recruitment. Returns 1 for the
     * winner; concurrent double-taps get 0 and must fail instead of cloning.
     */
    @Query("UPDATE WildRelationship SET recruitmentState = 'RECRUITED', updatedAt = :now WHERE individualId = :individualId AND recruitmentState != 'RECRUITED'")
    suspend fun claimForRecruitment(individualId: String, now: Long): Int

    @Query(
        """
        SELECT wr.individualId AS individualId, wr.cardCharacterId AS cardCharacterId,
               wr.speciesNameSnapshot AS speciesName, wr.trust AS trust,
               wr.recruitmentState AS recruitmentState,
               (SELECT content FROM ChatMessageEntity cm WHERE cm.individualId = wr.individualId ORDER BY cm.id DESC LIMIT 1) AS lastMessage,
               (SELECT timestamp FROM ChatMessageEntity cm WHERE cm.individualId = wr.individualId ORDER BY cm.id DESC LIMIT 1) AS lastTimestamp,
               (SELECT COUNT(*) FROM ChatMessageEntity cm WHERE cm.individualId = wr.individualId AND cm.role = 'assistant' AND cm.isRead = 0) AS unreadCount
        FROM WildRelationship wr
        WHERE wr.contactUnlockedAt IS NOT NULL AND wr.recruitmentState != 'RECRUITED'
        ORDER BY COALESCE(lastTimestamp, wr.updatedAt) DESC
        """
    )
    fun observeUnlocked(): Flow<List<DigilineWildThread>>
}
