package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.github.nacabaro.vbhelper.domain.reactions.DigimonStateSnapshot

@Dao
interface DigimonStateSnapshotDao {
    @Upsert
    suspend fun upsert(snapshot: DigimonStateSnapshot)

    @Query("SELECT * FROM DigimonStateSnapshot WHERE individualId = :individualId")
    suspend fun getByIndividualId(individualId: String): DigimonStateSnapshot?

    @Query("DELETE FROM DigimonStateSnapshot WHERE individualId = :individualId")
    suspend fun delete(individualId: String)
}
