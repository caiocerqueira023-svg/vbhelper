package com.tamerslab.storage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tamerslab.domain.species.FirstPartnerEncounter

@Dao
interface FirstPartnerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun record(encounter: FirstPartnerEncounter): Long

    @Query("SELECT * FROM FirstPartnerEncounter WHERE id = 1 LIMIT 1")
    suspend fun current(): FirstPartnerEncounter?

    @Query("UPDATE FirstPartnerEncounter SET step = :step WHERE id = 1")
    suspend fun setStep(step: Int)

    @Query("UPDATE FirstPartnerEncounter SET completedAt = :now WHERE id = 1 AND completedAt IS NULL")
    suspend fun complete(now: Long): Int
}
