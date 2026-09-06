package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import timber.log.Timber

class SpeciesRepository(
    private val database: AppDatabase,
    private val settingsRepository: SpeciesSettingsRepository,
    private val service: SpeciesDatabaseService = SpeciesDatabaseClient.create()
) {
    companion object {
        const val SPECIES_DB_URL =
            "https://raw.githubusercontent.com/TamerCaio/vbhelper-species-db/main/species.json"
    }

    private val gson = Gson()

    suspend fun matchOfficialSpeciesForCard(cardId: Long): Int {
        val card = database.cardDao().getCardById(cardId) ?: return 0
        val databaseSpecies = fetchDatabase() ?: return 0
        val speciesForCard = databaseSpecies.species[card.cardId.toString()] ?: return 0
        var matchedCount = 0

        database.characterDao().getCharactersForCard(cardId).forEach { character ->
            val matched = speciesForCard[character.charaIndex.toString()] ?: return@forEach
            val current = database.speciesProfileDao().getByCardCharacterId(character.id)
            database.speciesProfileDao().upsert(
                SpeciesProfile(
                    cardCharacterId = character.id,
                    speciesName = matched.name,
                    matchedName = matched.name,
                    level = matched.level ?: current?.level,
                    type = matched.type ?: current?.type,
                    profileDescription = matched.profile ?: current?.profileDescription,
                    specialMoves = matched.specialMoves.ifEmpty { current?.specialMoves ?: emptyList() },
                    source = SpeciesSource.OFFICIAL_MATCHED
                )
            )
            matchedCount++
        }
        return matchedCount
    }

    suspend fun getProfileForCharacter(cardCharacterId: Long): SpeciesProfile? =
        database.speciesProfileDao().getByCardCharacterId(cardCharacterId)

    suspend fun saveManualProfile(
        cardCharacterId: Long,
        name: String,
        level: String?,
        type: String?,
        profile: String?,
        specialMoves: List<String>
    ) {
        val existing = getProfileForCharacter(cardCharacterId)
        database.speciesProfileDao().upsert(
            SpeciesProfile(
                cardCharacterId = cardCharacterId,
                speciesName = name,
                // Preserve this audit field if an official profile is manually refined.
                matchedName = existing?.matchedName,
                level = level,
                type = type,
                profileDescription = profile,
                specialMoves = specialMoves,
                source = SpeciesSource.MANUAL
            )
        )
    }

    private suspend fun fetchDatabase(): SpeciesDatabaseDto? = try {
        service.getSpeciesDatabase(SPECIES_DB_URL).also { remote ->
            settingsRepository.cacheDatabase(gson.toJson(remote), remote.version)
        }
    } catch (exception: Exception) {
        Timber.w(exception, "Failed to fetch species database; using cached copy")
        cachedDatabase()
    }

    private suspend fun cachedDatabase(): SpeciesDatabaseDto? {
        val json = settingsRepository.cachedDatabaseJson.first() ?: return null
        return runCatching { gson.fromJson(json, SpeciesDatabaseDto::class.java) }
            .onFailure { Timber.w(it, "Failed to parse cached species database") }
            .getOrNull()
    }
}
