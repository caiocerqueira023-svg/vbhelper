package com.tamerslab.transfer.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tamerslab.domain.device_data.CharacterTransferPolicy

@Dao
interface CharacterTransferPolicyDao {
    @Query("SELECT * FROM CharacterTransferPolicy WHERE characterId = :characterId")
    fun getByCharacterId(characterId: Long): CharacterTransferPolicy?

    @Upsert
    fun upsert(policy: CharacterTransferPolicy)
}


