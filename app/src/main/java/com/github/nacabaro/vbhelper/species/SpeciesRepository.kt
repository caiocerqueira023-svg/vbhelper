package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Locale

class SpeciesRepository(
    private val database: AppDatabase? = null,
    private val settingsRepository: SpeciesSettingsRepository,
    private val service: SpeciesDatabaseService = SpeciesDatabaseClient.create(),
    private val assetLoader: (() -> String?)? = null,
    private val conversationExamplesLoader: (() -> String?)? = null
) {
    companion object {
        const val SPECIES_DB_URL =
            "https://raw.githubusercontent.com/TamerCaio/vbhelper-species-db/main/species.json"
    }

    private val gson = Gson()
    private var conversationExamplesCache: List<SpeciesConversationEntry>? = null

    suspend fun matchOfficialSpeciesForCard(cardId: Long): Int {
        val db = database ?: return 0
        val card = db.cardDao().getCardById(cardId) ?: return 0
        val databaseSpecies = fetchDatabase() ?: return 0
        val speciesForCard = findSpeciesForCard(databaseSpecies, card.cardId, card.isBEm)
        if (speciesForCard == null) {
            Timber.w(
                "No species database entry for card databaseId=${card.id}, dimId=${card.cardId}, " +
                    "isBEm=${card.isBEm}, triedKeys=${cardLookupKeys(card.cardId)}, availableKeys=${databaseSpecies.species.keys}"
            )
            return 0
        }
        var matchedCount = 0

        db.characterDao().getCharactersForCard(cardId).forEach { character ->
            val matched = findCharacterEntry(speciesForCard, character.charaIndex)
                ?: return@forEach
            val current = db.speciesProfileDao().getByCardCharacterId(character.id)
            db.speciesProfileDao().upsert(
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

    internal fun cardLookupKeys(cardId: Int): List<String> {
        val masked = if (cardId > 255) cardId and 0xFF else cardId
        val candidates = mutableListOf<String>()
        candidates.add(cardId.toString())
        if (cardId < 100) {
            candidates.add(String.format(Locale.ROOT, "%02d", cardId))
        }
        if (cardId in 100..999) {
            candidates.add(String.format(Locale.ROOT, "%03d", cardId))
        }
        if (cardId > 255) {
            candidates.add(masked.toString())
            if (masked < 100) {
                candidates.add(String.format(Locale.ROOT, "%02d", masked))
            }
            if (masked in 100..999) {
                candidates.add(String.format(Locale.ROOT, "%03d", masked))
            }
        }
        candidates.add(cardId.toString(16))
        candidates.add("0x${cardId.toString(16)}")
        if (cardId > 255) {
            candidates.add(masked.toString(16))
            candidates.add("0x${masked.toString(16)}")
        }
        return candidates.distinct()
    }

    internal fun findSpeciesForCard(
        databaseSpecies: SpeciesDatabaseDto,
        cardId: Int,
        isBEm: Boolean
    ): Map<String, SpeciesEntryDto>? {
        val keys = cardLookupKeys(cardId)
        for (key in keys) {
            val entry = databaseSpecies.species[key]
            if (entry != null) return entry
        }
        // Fallback: match by integer value of the key in species map
        val masked = if (cardId > 255) cardId and 0xFF else cardId
        for ((key, map) in databaseSpecies.species) {
            val parsedInt = key.toIntOrNull() ?: key.removePrefix("0x").toIntOrNull(16)
            if (parsedInt != null) {
                if (parsedInt == cardId || (cardId > 255 && parsedInt == masked)) {
                    return map
                }
            }
        }
        return null
    }

    internal fun findCharacterEntry(
        speciesForCard: Map<String, SpeciesEntryDto>,
        charaIndex: Int
    ): SpeciesEntryDto? {
        val oneIndexed = charaIndex + 1
        val oneIndexedKeys = listOf(
            oneIndexed.toString(),
            String.format(Locale.ROOT, "%02d", oneIndexed),
            oneIndexed.toString(16),
            "0x${oneIndexed.toString(16)}"
        ).distinct()
        for (key in oneIndexedKeys) {
            val match = speciesForCard[key]
            if (match != null) return match
        }

        val zeroIndexedKeys = listOf(
            charaIndex.toString(),
            String.format(Locale.ROOT, "%02d", charaIndex),
            charaIndex.toString(16),
            "0x${charaIndex.toString(16)}"
        ).distinct()
        for (key in zeroIndexedKeys) {
            val match = speciesForCard[key]
            if (match != null) return match
        }

        for ((key, entry) in speciesForCard) {
            val parsedInt = key.toIntOrNull() ?: key.removePrefix("0x").toIntOrNull(16)
            if (parsedInt == oneIndexed) return entry
        }
        for ((key, entry) in speciesForCard) {
            val parsedInt = key.toIntOrNull() ?: key.removePrefix("0x").toIntOrNull(16)
            if (parsedInt == charaIndex) return entry
        }

        return null
    }

    suspend fun getProfileForCharacter(cardCharacterId: Long): SpeciesProfile? =
        database?.speciesProfileDao()?.getByCardCharacterId(cardCharacterId)

    suspend fun getAllSpeciesNames(): List<String> =
        fetchDatabase()
            ?.species
            ?.values
            ?.asSequence()
            ?.flatMap { it.values.asSequence() }
            ?.map { it.name }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.sorted()
            ?.toList()
            ?: emptyList()

    suspend fun getAllSpeciesEntries(): List<SpeciesEntryDto> =
        fetchDatabase()
            ?.species
            ?.values
            ?.asSequence()
            ?.flatMap { it.values.asSequence() }
            ?.filter { it.name.isNotBlank() }
            ?.distinctBy { it.name.lowercase(Locale.ROOT) }
            ?.toList()
            ?: emptyList()

    /**
     * Returns the source dialogue sets for a species. These are references for
     * interaction rhythm and characterization, never mandatory lines to copy.
     */
    fun getConversationExamples(speciesName: String?): List<SpeciesConversationEntry> {
        val requestedName = speciesName?.takeIf { it.isNotBlank() } ?: return emptyList()
        val entries = conversationExamplesCache ?: loadConversationExamples().also {
            conversationExamplesCache = it
        }
        val normalizedRequestedName = canonicalConversationName(requestedName)
        return entries.filter {
            canonicalConversationName(it.speciesName) == normalizedRequestedName
        }
    }

    suspend fun saveManualProfile(
        cardCharacterId: Long,
        name: String,
        level: String?,
        type: String?,
        profile: String?,
        specialMoves: List<String>
    ) {
        val existing = getProfileForCharacter(cardCharacterId)
        val matched = runCatching {
            fetchDatabase()
                ?.species
                ?.values
                ?.asSequence()
                ?.flatMap { it.values.asSequence() }
                ?.firstOrNull { entry ->
                    normalizeSpeciesName(entry.name) == normalizeSpeciesName(name)
                }
        }.onFailure {
            Timber.w(it, "Failed to find manual species profile for name=$name")
        }.getOrNull()

        database?.speciesProfileDao()?.upsert(
            SpeciesProfile(
                cardCharacterId = cardCharacterId,
                speciesName = name,
                matchedName = matched?.name,
                level = level ?: matched?.level,
                type = type ?: matched?.type,
                profileDescription = profile ?: matched?.profile,
                specialMoves = specialMoves.ifEmpty {
                    matched?.specialMoves ?: existing?.specialMoves ?: emptyList()
                },
                source = SpeciesSource.MANUAL
            )
        )
    }

    private fun normalizeSpeciesName(name: String): String =
        name.trim().lowercase(Locale.ROOT)

    private fun normalizeConversationName(name: String): String =
        name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    /** Handles official-name spelling and form abbreviations used by the source sheet. */
    private fun canonicalConversationName(name: String): String = when (normalizeConversationName(name)) {
        "aegiochusmonholly" -> "aegiochusmonholy"
        "chronomondestroy" -> "chronomondestroymode"
        "lillymon" -> "lilimon"
        "herculeskabuterimon" -> "heraklekabuterimon"
        "lotosmon" -> "lotusmon"
        "rosemonbm" -> "rosemonburstmode"
        "ultimatebrakimon" -> "ultimatebrachimon"
        "ancientwisetmon" -> "ancientwisemon"
        "kerpymonbad" -> "cherubimonvice"
        "grapleomon" -> "grappuleomon"
        "craniamon" -> "craniummon"
        "aeroveedramon" -> "aerovdramon"
        "lucemonfm" -> "lucemonfalldownmode"
        "imperialdramonfm" -> "imperialdramonfightermode"
        "miragegaogamonbm" -> "miragegaogamonburstmode"
        "magnagarurumonseparation" -> "magnagarurumon"
        "dukemoncm" -> "dukemoncrimsonmode"
        "duftmonlm" -> "duftmonleopardmode"
        "beelzemonbm" -> "beelzebumonblastmode"
        "belphemonrm" -> "belphemonragemode"
        "belphemonsm" -> "belphemonsleepmode"
        "lucemonsm" -> "lucemonsatanmode"
        else -> normalizeConversationName(name)
    }

    private fun loadConversationExamples(): List<SpeciesConversationEntry> {
        val json = conversationExamplesLoader?.invoke() ?: return emptyList()
        return runCatching {
            gson.fromJson(json, SpeciesConversationDatabase::class.java)
                ?.entries
                .orEmpty()
                .filter { it.speciesName.isNotBlank() && it.exchanges.isNotEmpty() }
        }.onFailure {
            Timber.w(it, "Failed to parse bundled Digimon conversation examples")
        }.getOrDefault(emptyList())
    }

    internal suspend fun fetchDatabase(): SpeciesDatabaseDto? = try {
        val remoteUrl = "$SPECIES_DB_URL?cacheBust=${System.currentTimeMillis()}"
        val remote = parseDatabase(service.getSpeciesDatabase(remoteUrl).string())
        remote.also {
            settingsRepository.cacheDatabase(gson.toJson(it), it.version)
        }
    } catch (exception: Exception) {
        Timber.w(exception, "Failed to fetch species database; trying cached/bundled copy")
        cachedDatabase() ?: assetDatabase()
    }

    private suspend fun cachedDatabase(): SpeciesDatabaseDto? {
        val json = settingsRepository.cachedDatabaseJson.first() ?: return null
        return runCatching { parseDatabase(json) }
            .onFailure { Timber.w(it, "Failed to parse cached species database") }
            .getOrNull()
    }

    private suspend fun assetDatabase(): SpeciesDatabaseDto? {
        val json = assetLoader?.invoke() ?: return null
        return runCatching { parseDatabase(json) }
            .onFailure { Timber.w(it, "Failed to parse asset species database") }
            .getOrNull()
            ?.also {
                runCatching { settingsRepository.cacheDatabase(gson.toJson(it), it.version) }
            }
    }

    fun parseDatabase(rawJson: String): SpeciesDatabaseDto {
        val normalizedJson = sanitizeJson(rawJson)
        return try {
            gson.fromJson(normalizedJson, SpeciesDatabaseDto::class.java)
        } catch (e: Exception) {
            val extracted = extractBalancedRootObject(normalizedJson)
            gson.fromJson(extracted, SpeciesDatabaseDto::class.java)
        }
    }

    private fun sanitizeJson(rawJson: String): String {
        return rawJson
            .removePrefix("\uFEFF")
            .trimStart()
            .let { content ->
                if (content.startsWith("name=species.json")) {
                    content.substringAfter('\n').trimStart()
                } else {
                    content
                }
            }
    }

    internal fun extractBalancedRootObject(text: String): String {
        val start = text.indexOf('{')
        if (start < 0) return text
        var inString = false
        var escape = false
        var depth = 0
        var endIndex = -1

        for (i in start until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                if (inString) escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        endIndex = i
                        break
                    }
                }
            }
        }
        return if (endIndex >= 0) text.substring(start, endIndex + 1) else text
    }
}

