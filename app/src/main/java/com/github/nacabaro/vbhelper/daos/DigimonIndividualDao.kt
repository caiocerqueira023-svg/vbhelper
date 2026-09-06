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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPersonality(personality: DigimonPersonalityTraits)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPersonalitySync(personality: DigimonPersonalityTraits)

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
