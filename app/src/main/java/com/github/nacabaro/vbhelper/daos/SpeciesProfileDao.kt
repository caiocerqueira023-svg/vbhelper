package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesProfileDao {
    @Upsert
    suspend fun upsert(profile: SpeciesProfile)

    @Query("SELECT * FROM SpeciesProfile WHERE cardCharacterId = :cardCharacterId")
    suspend fun getByCardCharacterId(cardCharacterId: Long): SpeciesProfile?

    @Query("SELECT * FROM SpeciesProfile WHERE cardCharacterId = :cardCharacterId")
    fun getByCardCharacterIdFlow(cardCharacterId: Long): Flow<SpeciesProfile?>

    @Query("""
        SELECT sp.* FROM SpeciesProfile sp
        JOIN CardCharacter cc ON cc.id = sp.cardCharacterId
        WHERE cc.cardId = :cardId
    """)
    suspend fun getByCardId(cardId: Long): List<SpeciesProfile>
}
