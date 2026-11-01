package com.tamerslab.species.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tamerslab.domain.species.SpeciesProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesProfileDao {
    @Upsert
    suspend fun upsertNormalized(profile: SpeciesProfile)

    @Upsert
    suspend fun upsertNormalized(profiles: List<SpeciesProfile>)

    suspend fun upsert(profile: SpeciesProfile) = upsertNormalized(profile.copy(appearanceKey =
        com.tamerslab.battle.offline.core.BattleSpeciesIdentity.normalize(
            profile.speciesName?.takeIf(String::isNotBlank) ?: profile.matchedName.orEmpty())))

    @Query("SELECT * FROM SpeciesProfile WHERE appearanceKey = :key")
    suspend fun getByAppearanceKey(key: String): List<SpeciesProfile>

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

    /**
     * Species names whose card is currently imported. This is the loaded-species
     * source of truth for VB-mode eligibility, independent of any individual.
     */
    @Query("""
        SELECT DISTINCT COALESCE(NULLIF(sp.speciesName, ''), NULLIF(sp.matchedName, '')) FROM SpeciesProfile sp
        JOIN CardCharacter cc ON cc.id = sp.cardCharacterId
        JOIN Card c ON c.id = cc.cardId
        WHERE COALESCE(NULLIF(sp.speciesName, ''), NULLIF(sp.matchedName, '')) IS NOT NULL
    """)
    suspend fun getLoadedSpeciesNames(): List<String?>

    @Query("""
        SELECT sp.* FROM SpeciesProfile sp
        JOIN CardCharacter cc ON cc.id = sp.cardCharacterId
        WHERE cc.cardId = :cardId
    """)
    suspend fun getByCardId(cardId: Long): List<SpeciesProfile>
}
