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

    @Query("SELECT * FROM SpeciesProfile")
    suspend fun getAll(): List<SpeciesProfile>

    /**
     * Distinct species names with a profile row, i.e. loaded in at least one
     * DIM of the app. Used to gate universal Blast/Jogress options: a form or
     * fusion result is only offered when its species is present here.
     */
    @Query("SELECT DISTINCT COALESCE(NULLIF(speciesName, ''), NULLIF(matchedName, '')) FROM SpeciesProfile")
    suspend fun getPresentSpeciesNames(): List<String?>

    @Query("""
        SELECT sp.* FROM SpeciesProfile sp
        JOIN CardCharacter cc ON cc.id = sp.cardCharacterId
        WHERE cc.cardId = :cardId
    """)
    suspend fun getByCardId(cardId: Long): List<SpeciesProfile>
}
