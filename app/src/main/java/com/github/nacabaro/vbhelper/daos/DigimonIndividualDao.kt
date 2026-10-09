package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits

@Dao
interface DigimonIndividualDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(individual: DigimonIndividual): Long

    @Query("SELECT EXISTS(SELECT 1 FROM DigimonIndividual WHERE individualId = :individualId)")
    fun exists(individualId: String): Boolean

    @Query("SELECT * FROM DigimonPersonalityTraits WHERE individualId = :individualId")
    suspend fun getPersonality(individualId: String): DigimonPersonalityTraits?

    @Query("SELECT * FROM DigimonPersonalityTraits WHERE individualId = :individualId")
    fun getPersonalitySync(individualId: String): DigimonPersonalityTraits?

    @Query("SELECT * FROM DigimonIndividual WHERE individualId = :individualId")
    suspend fun getIndividual(individualId: String): DigimonIndividual?

    @Query("UPDATE DigimonIndividual SET lastDiaryEntryAt = :timestamp WHERE individualId = :individualId")
    suspend fun updateLastDiaryEntry(individualId: String, timestamp: Long)

    @Query(
        """
        UPDATE DigimonIndividual
        SET lastCelebratedWinsMilestone = :winsMilestone,
            lastCelebratedTrophyMilestone = :trophyMilestone
        WHERE individualId = :individualId
        """
    )
    suspend fun updateMilestones(individualId: String, winsMilestone: Int, trophyMilestone: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonality(personality: DigimonPersonalityTraits)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertPersonalitySync(personality: DigimonPersonalityTraits)

    @Query(
        """
        UPDATE DigimonIndividual
        SET blastMode = :mode,
            blastTargetSpecies = :targetSpecies
        WHERE individualId = :individualId
        """
    )
    suspend fun updateBlastChoice(individualId: String, mode: String, targetSpecies: String?)

    @Query(
        """
        UPDATE DigimonIndividual
        SET jogressResultSpecies = :resultSpecies
        WHERE individualId = :individualId
        """
    )
    suspend fun updateJogressChoice(individualId: String, resultSpecies: String?)

    @Query(
        """
        UPDATE DigimonIndividual
        SET nickname = :nickname
        WHERE individualId = (
            SELECT individualId FROM UserCharacter WHERE id = :characterId
        )
        """
    )
    suspend fun updateNicknameForCharacter(characterId: Long, nickname: String?)
}
