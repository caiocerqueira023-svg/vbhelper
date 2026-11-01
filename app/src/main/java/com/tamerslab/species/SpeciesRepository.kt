package com.tamerslab.species

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.card.OfficialStatus
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.domain.species.SpeciesProfile
import com.tamerslab.domain.species.SpeciesSource
import com.tamerslab.settings.data.SpeciesSettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale

class SpeciesRepository(
    private val database: AppDatabase? = null,
    private val settingsRepository: SpeciesSettingsRepository,
    private val service: SpeciesDatabaseService = SpeciesDatabaseClient.create(),
    private val assetLoader: (() -> String?)? = null,
    private val conversationExamplesLoader: (() -> String?)? = null,
    private val spriteMatcher: OfficialSpriteMatcher? = null,
    private val debugEnabled: () -> Boolean = { false },
    private val canonicalCatalogLoader: (suspend () -> CanonicalSpeciesCatalog?)? = null
) {
    companion object {
        const val SPECIES_DB_URL =
            "https://raw.githubusercontent.com/TamerCaio/vbhelper-species-db/main/species.json"
        /** Freshness window for the in-memory species database. */
        const val DATABASE_CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L

        // Process-wide so short-lived owners (per-dialog repositories) share one
        // fetch instead of each paying a network round trip on first use.
        private val databaseMutex = Mutex()
        private var cachedDatabase: SpeciesDatabaseDto? = null
        private var cachedDatabaseAt: Long = 0L

        internal fun clearDatabaseCacheForTests() {
            cachedDatabase = null
            cachedDatabaseAt = 0L
        }
    }

    private val gson = Gson()
    private val customization by lazy { database?.let(::SpeciesCustomizationRepository) }
    val catalogChanges get() = customization?.catalogChanges ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun saveCatalogEntry(originalName: String?, entry: SpeciesEntryDto, spriteId: Int?): SpeciesEntryDto {
        val entries = getAllSpeciesEntries()
        val originalKey = originalName?.let(SpeciesCustomizationRepository::key)
        val newKey = SpeciesCustomizationRepository.key(entry.name)
        require(entries.none { SpeciesCustomizationRepository.key(it.name) == newKey && newKey != originalKey })
        return requireNotNull(customization).save(originalName, entry, spriteId)
    }

    private suspend fun customizedProfile(profile: SpeciesProfile): SpeciesProfile = customization?.applyProfile(profile) ?: profile
    private var conversationExamplesCache: List<SpeciesConversationEntry>? = null
    internal var clock: () -> Long = System::currentTimeMillis

    /** Only explicitly official cards may identify species by their card number/slot. */
    suspend fun matchSpeciesForCard(cardId: Long): Int {
        val card = database?.cardDao()?.getCardById(cardId) ?: return 0
        return if (card.officialStatus == OfficialStatus.OFFICIAL) matchOfficialSpeciesForCard(cardId)
        else matchCustomSpeciesForCard(cardId)
    }

    suspend fun matchCustomSpeciesForCard(cardId: Long): Int = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext 0
        if (spriteMatcher == null) return@withContext 0
        val card = db.cardDao().getCardById(cardId) ?: return@withContext 0
        // Recognition works offline, including a first import with no downloaded species cache.
        val databaseSpecies = cachedDatabaseSnapshot() ?: assetDatabase() ?: return@withContext 0
        var matchedCount = 0
        for (character in db.characterDao().getCharactersForCard(cardId)) {
            currentCoroutineContext().ensureActive()
            if (db.speciesProfileDao().getByCardCharacterId(character.id)?.source == SpeciesSource.MANUAL) continue
            val sprite = db.spriteDao().getOriginalForCharacter(character.id) ?: continue
            val profile = profileForSpriteMatch(character.id, sprite, databaseSpecies, null,
                compactDim = !card.isBEm && character.charaIndex < 2) ?: continue
            // A manual edit made while the catalog was decoded still takes priority.
            db.withTransaction {
                if (db.characterDao().getById(character.id) != null &&
                    db.speciesProfileDao().getByCardCharacterId(character.id)?.source != SpeciesSource.MANUAL) {
                    db.speciesProfileDao().upsert(customizedProfile(profile))
                    matchedCount++
                }
            }
        }
        matchedCount
    }

    internal fun profileForSpriteMatch(
        cardCharacterId: Long,
        sprite: Sprite,
        databaseSpecies: SpeciesDatabaseDto,
        current: SpeciesProfile?,
        compactDim: Boolean = false
    ): SpeciesProfile? {
        if (current?.source == SpeciesSource.MANUAL) return null
        val matches = spriteMatcher?.findMatches(sprite, compactDim).orEmpty().mapNotNull { identity ->
            val entries = findCatalogNumber(databaseSpecies.species, identity.cardNumber)
                ?: return@mapNotNull null
            findCatalogNumber(entries, identity.charaIndex + 1)?.takeIf { it.name.isNotBlank() }
        }
        // Reused art is fine when every resolved candidate names the same species.
        if (matches.map { normalizeSpeciesName(it.name) }.distinct().size != 1) return null
        val matched = matches.first()
        return SpeciesProfile(
            cardCharacterId = cardCharacterId,
            speciesName = matched.name,
            matchedName = matched.name,
            level = matched.level,
            type = matched.type,
            profileDescription = matched.profile,
            specialMoves = matched.specialMoves,
            source = SpeciesSource.OFFICIAL_MATCHED
        )
    }

    // Bundled folder numbers are decimal and mon slots are one-indexed. A missing
    // decimal 24 must not alias hex "18", nor may a missing slot fall back to its neighbor.
    private fun <T> findCatalogNumber(entries: Map<String, T>, number: Int): T? =
        entries[number.toString()] ?: entries.entries.firstOrNull { (key, _) ->
            key.toIntOrNull() == number ||
                (key.startsWith("0x", ignoreCase = true) && key.substring(2).toIntOrNull(16) == number)
        }?.value

    suspend fun matchOfficialSpeciesForCard(cardId: Long): Int {
        val db = database ?: return 0
        val card = db.cardDao().getCardById(cardId) ?: return 0
        if (card.officialStatus == OfficialStatus.CUSTOM) return matchCustomSpeciesForCard(cardId)
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
            if (current?.source == SpeciesSource.MANUAL) return@forEach
            db.speciesProfileDao().upsert(
                customizedProfile(SpeciesProfile(
                    cardCharacterId = character.id,
                    speciesName = matched.name,
                    matchedName = matched.name,
                    level = matched.level ?: current?.level,
                    type = matched.type ?: current?.type,
                    profileDescription = matched.profile ?: current?.profileDescription,
                    specialMoves = matched.specialMoves.ifEmpty { current?.specialMoves ?: emptyList() },
                    source = SpeciesSource.OFFICIAL_MATCHED
                ))
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
        database?.speciesProfileDao()?.getByCardCharacterId(cardCharacterId)?.let { customizedProfile(it) }

    suspend fun getAllSpeciesNames(): List<String> = getAllSpeciesEntries().map { it.name }

    suspend fun getAllSpeciesEntries(): List<SpeciesEntryDto> {
        val canonical = getCanonicalCatalog()
        val base = fetchDatabase()
            ?.species
            ?.values
            ?.asSequence()
            ?.flatMap { it.values.asSequence() }
            ?.filter { it.name.isNotBlank() }
            ?.distinctBy { it.name.lowercase(Locale.ROOT) }
            ?.toList()
            ?: emptyList()
        val references = (base + canonical?.data?.species.orEmpty().map { it.asEntry() })
            .distinctBy { it.name.lowercase(Locale.ROOT) }.map { entry ->
                canonical?.resolve(entry.name)?.let { identity ->
                    entry.copy(speciesId = identity.id, dotSprites = identity.dotSprites)
                } ?: entry
            }
        return customization?.mergeCatalog(references) ?: references
    }

/** Local source-independent reference catalog; no network fetch or partner creation. */
    suspend fun getCanonicalCatalog(): CanonicalSpeciesCatalog? = canonicalCatalogLoader?.invoke()

    /**
     * Builds the shared eligibility index from the bundled catalog and every species
     * currently present on an imported card. This is the one availability read used by
     * both modes; partners are never recreated or migrated by building it.
     */
    suspend fun availabilityIndex(): SpeciesAvailabilityIndex {
        val loaded = database?.speciesProfileDao()?.getLoadedSpeciesNames().orEmpty()
            .mapNotNull { it?.takeIf(String::isNotBlank) }.toSet()
        return SpeciesAvailabilityIndex(getCanonicalCatalog(), loaded)
    }

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
        val requestedStem = conversationFormStem(normalizedRequestedName)
        val namedEntries = entries.map { entry ->
            canonicalConversationName(entry.speciesName) to entry
        }
        val exactMatches = namedEntries.filter { it.first == normalizedRequestedName }
        if (exactMatches.isNotEmpty()) {
            val baseMatches = namedEntries.filter {
                it.first == requestedStem && it.first == conversationFormStem(it.first)
            }
            return (exactMatches + baseMatches)
                .distinctBy { it.second }
                .map { it.second }
        }
        val relatedForms = namedEntries.filter {
            conversationFormStem(it.first) == requestedStem
        }
        return if (relatedForms.size == 1) relatedForms.map { it.second } else emptyList()
    }

    suspend fun saveManualProfile(
        cardCharacterId: Long,
        name: String,
        level: String?,
        type: String?,
        profile: String?,
        specialMoves: List<String>
    ) {
        if (!debugEnabled()) return
        writeManualProfile(cardCharacterId, name, level, type, profile, specialMoves, requiresDebug = true)
    }

    /** Ordinary custom-card identification selects an existing catalog record, never arbitrary reference overrides. */
    suspend fun identifySpecies(cardCharacterId: Long, name: String): Boolean {
        val base = ((cachedDatabaseSnapshot() ?: assetDatabase())?.species?.values?.flatMap { it.values }.orEmpty() +
            getCanonicalCatalog()?.data?.species.orEmpty().map { it.asEntry() }).distinctBy { it.name.lowercase(Locale.ROOT) }
        val entries = customization?.mergeCatalog(base) ?: base
        val matched = entries.firstOrNull {
            SpeciesCustomizationRepository.key(it.name) == SpeciesCustomizationRepository.key(name)
        } ?: return false
        writeManualProfile(cardCharacterId, matched.name, matched.level, matched.type, matched.profile, matched.specialMoves,
            requiresDebug = false)
        return true
    }

    private suspend fun writeManualProfile(cardCharacterId: Long, name: String, level: String?, type: String?,
                                          profile: String?, specialMoves: List<String>, requiresDebug: Boolean) {
        val db = database ?: return
        if (requiresDebug && !debugEnabled()) return
        val existing = getProfileForCharacter(cardCharacterId)
        // Enrichment uses only locally available data: the name the user picked
        // is authoritative and must not wait on a network fetch to be saved.
        val matched = runCatching {
            cachedDatabaseSnapshot()
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

        db.withTransaction {
            check(!requiresDebug || debugEnabled()) { "Debug access was revoked" }
            val matchedMoves = (matched?.specialMoves as List<String>?).orEmpty()
            db.speciesProfileDao().upsert(
                SpeciesProfile(
                    cardCharacterId = cardCharacterId,
                    speciesName = name,
                    matchedName = matched?.name,
                    level = level ?: matched?.level,
                    type = type ?: matched?.type,
                    profileDescription = profile ?: matched?.profile,
                    specialMoves = specialMoves.ifEmpty {
                        matchedMoves.ifEmpty { existing?.specialMoves ?: emptyList() }
                    },
                    source = SpeciesSource.MANUAL
                )
            )
        }
    }

    private fun normalizeSpeciesName(name: String): String =
        name.trim().lowercase(Locale.ROOT)

    private fun normalizeConversationName(name: String): String =
        Normalizer.normalize(name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]"), "")

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
        "dukemoncm" -> "dukemoncrimsonmode"
        "duftmonlm" -> "duftmonleopardmode"
        "beelzemonbm" -> "beelzebumonblastmode"
        "belphemonrm" -> "belphemonragemode"
        "belphemonsm" -> "belphemonsleepmode"
        "lucemonsm" -> "lucemonsatanmode"
        else -> normalizeConversationName(name)
    }

    private fun conversationFormStem(name: String): String {
        var stem = name
        if (stem.endsWith("mode") && stem.length > "mode".length + 3) {
            stem = stem.dropLast("mode".length)
        }
        listOf(
            "hysteric",
            "separation",
            "wonderland",
            "prominence",
            "falldown",
            "burst",
            "blast",
            "destroy",
            "wrath",
            "infernal",
            "bastion",
            "goddess",
            "leopard",
            "crimson",
            "inferno",
            "dragon",
            "satan",
            "rapid",
            "flame",
            "fiery",
            "hero",
            "dark",
            "light",
            "rage",
            "sleep",
            "fist"
        ).forEach { qualifier ->
            if (stem.endsWith(qualifier) && stem.length > qualifier.length + 3) {
                stem = stem.dropLast(qualifier.length)
            }
        }
        listOf("bm", "fm", "lm", "rm", "sm", "cm", "vs").forEach { suffix ->
            if (stem.endsWith(suffix) && stem.length > suffix.length + 3) {
                stem = stem.dropLast(suffix.length)
            }
        }
        return stem
    }

    private fun loadConversationExamples(): List<SpeciesConversationEntry> {
        val json = conversationExamplesLoader?.invoke() ?: return emptyList()
        return runCatching {
            val parsed = gson.fromJson(json, SpeciesConversationDatabase::class.java)
            parsed?.entries
                .orEmpty()
                .filter { it.speciesName.isNotBlank() && it.exchanges.isNotEmpty() }
                .map { it.copy(source = it.source?.takeIf(String::isNotBlank) ?: parsed?.source?.takeIf(String::isNotBlank)) }
        }.onFailure {
            Timber.w(it, "Failed to parse bundled Digimon conversation examples")
        }.getOrDefault(emptyList())
    }

    internal suspend fun fetchDatabase(): SpeciesDatabaseDto? = databaseMutex.withLock {
        val fresh = cachedDatabase
        if (fresh != null && clock() - cachedDatabaseAt <= DATABASE_CACHE_TTL_MILLIS) return fresh
        try {
            val remoteUrl = "$SPECIES_DB_URL?cacheBust=${System.currentTimeMillis()}"
            val remote = parseDatabase(service.getSpeciesDatabase(remoteUrl).string())
            remote.also {
                settingsRepository.cacheDatabase(gson.toJson(it), it.version)
            }
            cachedDatabase = remote
            cachedDatabaseAt = clock()
            remote
        } catch (exception: Exception) {
            Timber.w(exception, "Failed to fetch species database; trying cached/bundled copy")
            // Remember the fallback as fresh so one offline stretch does not
            // pay a network timeout on every dialog open and import.
            val fallback = cachedDatabase ?: cachedDatabase() ?: assetDatabase()
            if (fallback != null) {
                cachedDatabase = fallback
                cachedDatabaseAt = clock()
            }
            fallback
        }
    }

    private suspend fun cachedDatabase(): SpeciesDatabaseDto? {
        val json = settingsRepository.cachedDatabaseJson.first() ?: return null
        return runCatching { parseDatabase(json) }
            .onFailure { Timber.w(it, "Failed to parse cached species database") }
            .getOrNull()
    }

    /** Local-only snapshot for enrichment: in-memory first, then the DataStore cache. Never hits the network. */
    private suspend fun cachedDatabaseSnapshot(): SpeciesDatabaseDto? {
        databaseMutex.withLock { cachedDatabase }?.let { return it }
        return runCatching { settingsRepository.cachedDatabaseJson.first()?.let { parseDatabase(it) } }
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
        val parsed = try {
            gson.fromJson(normalizedJson, SpeciesDatabaseDto::class.java)
        } catch (e: Exception) {
            val extracted = extractBalancedRootObject(normalizedJson)
            gson.fromJson(extracted, SpeciesDatabaseDto::class.java)
        }
        return parsed.copy(species = parsed.species.mapValues { (_, slots) -> slots.mapValues { (_, entry) ->
            val rawMoves = (entry.specialMoves as List<String>?).orEmpty()
            val rawProfile = entry.profile as String?
            val rawNotes = (entry.sourceNotes as List<String>?).orEmpty()
            entry.copy(profile = rawProfile?.let(ResearchText::description), specialMoves = ResearchText.moves(rawMoves),
                sourceNotes = (rawNotes + rawProfile?.let(ResearchText::productionNotes).orEmpty()).distinct())
        } })
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

