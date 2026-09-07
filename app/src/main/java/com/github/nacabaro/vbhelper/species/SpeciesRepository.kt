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
        val cardKeys = numericKeys(card.cardId)
        val speciesForCard = cardKeys
            .asSequence()
            .mapNotNull { databaseSpecies.species[it] }
            .firstOrNull()
        if (speciesForCard == null) {
            Timber.w(
                "No species database entry for card databaseId=${card.id}, dimId=${card.cardId}, " +
                    "triedKeys=$cardKeys, availableKeys=${databaseSpecies.species.keys}"
            )
            return 0
        }
        var matchedCount = 0

        database.characterDao().getCharactersForCard(cardId).forEach { character ->
            val characterKeys = numericKeys(character.charaIndex)
            val matched = characterKeys
                .asSequence()
                .mapNotNull { speciesForCard[it] }
                .firstOrNull()
                ?: return@forEach
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

    private fun numericKeys(value: Int): List<String> {
        val decimalVariants = listOf(value, value + 1, value - 1)
            .filter { it >= 0 }
        return (decimalVariants.map { it.toString() } +
            decimalVariants.map { it.toString(16) } +
            decimalVariants.map { "0x${it.toString(16)}" })
            .distinct()
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
        val remoteUrl = "$SPECIES_DB_URL?cacheBust=${System.currentTimeMillis()}"
        val remote = parseDatabase(service.getSpeciesDatabase(remoteUrl).string())
        remote.also {
            settingsRepository.cacheDatabase(gson.toJson(it), it.version)
        }
    } catch (exception: Exception) {
        Timber.w(exception, "Failed to fetch species database; using cached copy")
        cachedDatabase()
    }

    private suspend fun cachedDatabase(): SpeciesDatabaseDto? {
        val json = settingsRepository.cachedDatabaseJson.first() ?: return null
        return runCatching { parseDatabase(json) }
            .onFailure { Timber.w(it, "Failed to parse cached species database") }
            .getOrNull()
    }

    private fun parseDatabase(rawJson: String): SpeciesDatabaseDto {
        val normalizedJson = rawJson
            .removePrefix("\uFEFF")
            .trimStart()
            .let { content ->
                if (content.startsWith("name=species.json")) {
                    content.substringAfter('\n').trimStart()
                } else {
                    content
                }
            }
        return gson.fromJson(normalizedJson, SpeciesDatabaseDto::class.java)
    }
}
