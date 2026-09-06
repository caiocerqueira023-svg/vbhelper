package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Dao
interface DigimonIndividualDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(individual: DigimonIndividual): Long

    @Query("SELECT EXISTS(SELECT 1 FROM DigimonIndividual WHERE individualId = :individualId)")
    fun exists(individualId: String): Boolean

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
