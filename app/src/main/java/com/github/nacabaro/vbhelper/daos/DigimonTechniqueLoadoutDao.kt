package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.nacabaro.vbhelper.domain.device_data.DigimonTechniqueLoadout
import kotlinx.coroutines.flow.Flow

@Dao
interface DigimonTechniqueLoadoutDao {
    @Query("SELECT * FROM DigimonTechniqueLoadout WHERE individualId = :individualId ORDER BY slot")
    fun observe(individualId: String): Flow<List<DigimonTechniqueLoadout>>

    @Query("SELECT * FROM DigimonTechniqueLoadout WHERE individualId = :individualId ORDER BY slot")
    suspend fun getForIndividual(individualId: String): List<DigimonTechniqueLoadout>

    @Query("SELECT * FROM DigimonTechniqueLoadout ORDER BY individualId, slot")
    suspend fun getAll(): List<DigimonTechniqueLoadout>

    @Query("DELETE FROM DigimonTechniqueLoadout WHERE individualId = :individualId")
    suspend fun deleteForIndividual(individualId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entries: List<DigimonTechniqueLoadout>)

    @Transaction
    suspend fun replace(individualId: String, techniqueIds: List<String>) {
        deleteForIndividual(individualId)
        insert(techniqueIds.mapIndexed { slot, techniqueId ->
            DigimonTechniqueLoadout(individualId, slot, techniqueId)
        })
    }
}
