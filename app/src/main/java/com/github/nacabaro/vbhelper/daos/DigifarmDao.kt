package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.github.nacabaro.vbhelper.domain.digifarm.Farm
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMessage
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMessageRecipient
import com.github.nacabaro.vbhelper.domain.digifarm.FarmReadState
import com.github.nacabaro.vbhelper.domain.digifarm.FarmRelationship
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMemory
import com.github.nacabaro.vbhelper.domain.digifarm.FarmResident
import com.github.nacabaro.vbhelper.dtos.DigilineFarmThread
import com.github.nacabaro.vbhelper.dtos.DigilineStorageThread
import com.github.nacabaro.vbhelper.dtos.FarmResidentWithDetails
import com.github.nacabaro.vbhelper.dtos.FarmAssignment
import kotlinx.coroutines.flow.Flow

@Dao
interface DigifarmDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFarm(farm: Farm)

    @Update suspend fun updateFarm(farm: Farm)

    @Query("UPDATE Farm SET cameraScale = :scale, cameraX = :x, cameraY = :y WHERE id = :farmId")
    suspend fun updateCamera(farmId: String, scale: Float, x: Float, y: Float)

    @Query("UPDATE Farm SET cameraYaw = :yaw, cameraPitch = :pitch, cameraDistance = :distance, cameraTargetX = :targetX, cameraTargetZ = :targetZ WHERE id = :farmId")
    suspend fun update3dCamera(
        farmId: String,
        yaw: Float,
        pitch: Float,
        distance: Float,
        targetX: Float,
        targetZ: Float
    )

    @Query("UPDATE Farm SET autonomousDialogueEnabled = :enabled WHERE id = :farmId")
    suspend fun setAutonomousDialogue(farmId: String, enabled: Boolean)

    @Query("SELECT * FROM Farm WHERE archivedAt IS NULL ORDER BY createdAt")
    fun observeFarms(): Flow<List<Farm>>

    @Query("SELECT * FROM Farm WHERE id = :farmId LIMIT 1")
    suspend fun getFarm(farmId: String): Farm?

    @Query("UPDATE Farm SET archivedAt = :now WHERE id = :farmId")
    suspend fun archiveFarm(farmId: String, now: Long)

    @Query("DELETE FROM FarmResident WHERE farmId = :farmId")
    suspend fun clearResidents(farmId: String)

    @Query("SELECT * FROM FarmResident WHERE individualId = :individualId LIMIT 1")
    suspend fun getResident(individualId: String): FarmResident?

    @Query("SELECT COUNT(*) FROM FarmResident WHERE farmId = :farmId")
    suspend fun residentCount(farmId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResident(resident: FarmResident)

    @Update suspend fun updateResident(resident: FarmResident)

    @Query("DELETE FROM FarmResident WHERE individualId = :individualId")
    suspend fun removeResident(individualId: String)

    @Query("SELECT individualId FROM FarmResident")
    fun observeResidentIds(): Flow<List<String>>

    @Query("SELECT uc.id FROM UserCharacter uc JOIN FarmResident fr ON fr.individualId = uc.individualId")
    fun observeResidentCharacterIds(): Flow<List<Long>>

    @Query("SELECT uc.id AS characterId, f.id AS farmId, f.name AS farmName FROM FarmResident fr JOIN Farm f ON f.id = fr.farmId JOIN UserCharacter uc ON uc.individualId = fr.individualId WHERE f.archivedAt IS NULL")
    fun observeAssignments(): Flow<List<FarmAssignment>>

    @Query("SELECT * FROM FarmResident WHERE farmId = :farmId")
    suspend fun getResidentEntities(farmId: String): List<FarmResident>

    @Query(
        """
        SELECT fr.*, uc.id AS characterId, uc.charId AS cardCharacterId,
               di.nickname AS nickname, sp.speciesName AS speciesName,
               s.spriteIdle1 AS spriteIdle, s.spriteIdle2 AS spriteIdle2,
               s.spriteWalk1 AS spriteWalk, s.spriteWalk2 AS spriteWalk2,
               s.spriteTrain1 AS spriteTrain, s.spriteTrain2 AS spriteTrain2,
               s.spriteHappy AS spriteHappy, s.spriteSleep AS spriteSleep,
               s.width AS spriteWidth, s.height AS spriteHeight
        FROM FarmResident fr
        JOIN UserCharacter uc ON uc.individualId = fr.individualId
        JOIN DigimonIndividual di ON di.individualId = fr.individualId
        JOIN CardCharacter cc ON cc.id = uc.charId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        WHERE fr.farmId = :farmId
        ORDER BY fr.positionY, fr.positionX
        """
    )
    fun observeResidents(farmId: String): Flow<List<FarmResidentWithDetails>>

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM FarmMessage WHERE farmId = :farmId")
    suspend fun nextSequence(farmId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: FarmMessage)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipients(recipients: List<FarmMessageRecipient>)

    @Transaction
    suspend fun insertMessageWithRecipients(message: FarmMessage, recipients: List<FarmMessageRecipient>) {
        insertMessage(message)
        if (recipients.isNotEmpty()) insertRecipients(recipients)
    }

    @Query("SELECT * FROM FarmMessage WHERE farmId = :farmId ORDER BY sequence DESC LIMIT :limit")
    fun observeMessages(farmId: String, limit: Int = 50): Flow<List<FarmMessage>>

    @Query("SELECT individualId FROM FarmMessageRecipient WHERE messageId = :messageId")
    suspend fun recipientIds(messageId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadState(state: FarmReadState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelationship(relationship: FarmRelationship)

    @Query(
        """
        INSERT INTO FarmRelationship(observerId, otherId, affinity, familiarity, lastInteractionAt)
        VALUES(:observerId, :otherId, 51, 1, :now)
        ON CONFLICT(observerId, otherId) DO UPDATE SET
            affinity = MIN(100, affinity + 1),
            familiarity = MIN(100, familiarity + 1),
            lastInteractionAt = :now
        """
    )
    suspend fun recordInteraction(observerId: String, otherId: String, now: Long)

    @Query("""
        INSERT INTO FarmRelationship(observerId,otherId,affinity,familiarity,lastInteractionAt)
        VALUES(:observer,:other,MIN(100,MAX(0,50 + :delta)),1,:now)
        ON CONFLICT(observerId,otherId) DO UPDATE SET
            affinity=MIN(100,MAX(0,affinity + :delta)), familiarity=MIN(100,familiarity+1), lastInteractionAt=:now
    """)
    suspend fun applySocialInteraction(observer: String, other: String, delta: Int, now: Long)

    @Query("SELECT * FROM FarmRelationship WHERE observerId = :id")
    suspend fun relationships(id: String): List<com.github.nacabaro.vbhelper.domain.digifarm.FarmRelationship>

    @Query("SELECT * FROM FarmMemory WHERE observerId = :id AND relatedIndividualId = :other ORDER BY relevance DESC,createdAt DESC LIMIT :limit")
    suspend fun memoriesWith(id: String, other: String, limit: Int = 5): List<FarmMemory>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemory(memory: FarmMemory)

    @Query("SELECT * FROM FarmMemory WHERE observerId = :observerId ORDER BY relevance DESC, createdAt DESC LIMIT :limit")
    suspend fun getMemories(observerId: String, limit: Int = 5): List<FarmMemory>

    @Query("DELETE FROM FarmMemory WHERE observerId = :observerId AND id NOT IN (SELECT id FROM FarmMemory WHERE observerId = :observerId ORDER BY relevance DESC, createdAt DESC LIMIT 50)")
    suspend fun pruneMemories(observerId: String)

    @Query(
        """
        SELECT uc.id AS characterId, uc.individualId AS individualId,
               di.nickname AS nickname, sp.speciesName AS speciesName,
               s.spriteIdle1 AS spriteIdle, s.width AS spriteWidth, s.height AS spriteHeight,
               (SELECT content FROM ChatMessageEntity cm WHERE cm.individualId = uc.individualId ORDER BY cm.id DESC LIMIT 1) AS lastMessage,
               (SELECT timestamp FROM ChatMessageEntity cm WHERE cm.individualId = uc.individualId ORDER BY cm.id DESC LIMIT 1) AS lastTimestamp,
               (SELECT COUNT(*) FROM ChatMessageEntity cm WHERE cm.individualId = uc.individualId AND cm.role = 'assistant' AND cm.isRead = 0) AS unreadCount
        FROM UserCharacter uc
        JOIN DigimonIndividual di ON di.individualId = uc.individualId
        JOIN CardCharacter cc ON cc.id = uc.charId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        WHERE EXISTS(SELECT 1 FROM ChatMessageEntity cm WHERE cm.individualId = uc.individualId AND cm.role IN ('user','assistant'))
        ORDER BY COALESCE(lastTimestamp, 0) DESC
        """
    )
    fun observeStorageThreads(): Flow<List<DigilineStorageThread>>

    @Query(
        """
        SELECT f.id AS farmId, f.name AS farmName,
               (SELECT COUNT(*) FROM FarmResident fr WHERE fr.farmId = f.id) AS residentCount,
               (SELECT body FROM FarmMessage fm WHERE fm.farmId = f.id ORDER BY fm.sequence DESC LIMIT 1) AS lastMessage,
               (SELECT timestamp FROM FarmMessage fm WHERE fm.farmId = f.id ORDER BY fm.sequence DESC LIMIT 1) AS lastTimestamp,
               MAX(0, (SELECT COALESCE(MAX(sequence), 0) FROM FarmMessage fm WHERE fm.farmId = f.id) -
                      COALESCE((SELECT lastReadSequence FROM FarmReadState rs WHERE rs.farmId = f.id), 0)) AS unreadCount
        FROM Farm f WHERE f.archivedAt IS NULL
        ORDER BY COALESCE(lastTimestamp, f.createdAt) DESC
        """
    )
    fun observeFarmThreads(): Flow<List<DigilineFarmThread>>
}
