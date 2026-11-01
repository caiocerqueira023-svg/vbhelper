package com.tamerslab.storage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tamerslab.domain.device_data.VitalWearCharacterSettings

@Dao
interface VitalWearSettingsDao {
    @Query("SELECT * FROM VitalWearCharacterSettings WHERE characterId = :characterId LIMIT 1")
    suspend fun getByCharacterId(characterId: Long): VitalWearCharacterSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: VitalWearCharacterSettings)
}

