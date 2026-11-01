package com.tamerslab.species

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.card.CardCharacter
import com.tamerslab.domain.species.SpeciesProfile
import com.tamerslab.domain.species.SpeciesSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Catalog species are genuine card-independent definitions: no synthetic DIM or Card row is created. */
class ExpandedCatalogRepository(private val db: AppDatabase, private val library: () -> BundledDotSpriteLibrary,
    private val preference: () -> SpritePreference) {
    private val mutex = Mutex()
    private var prepared = false

    suspend fun ensureReadyDefinitions() = withContext(Dispatchers.IO) { mutex.withLock {
        val catalog = library().catalog()
        val ready = catalog.data.species.filter { it.kind == "DIGIMON" }
        ensurePlaceholder()
        val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM CardCharacter WHERE catalogSpeciesId IS NOT NULL").use { it.moveToFirst(); it.getInt(0) }
        if (!prepared || count < ready.size) {
            val installed = db.spriteDao().getBundledSpriteIds().toSet()
            library().installAll(ready.mapNotNull { it.dotSprites.defaultSetId }.distinct()
                .filter { catalog.setsById.getValue(it).spriteId !in installed })
            db.withTransaction {
                val existing = db.characterDao().getCatalogDefinitions().associateBy { it.id }
                val profiles = db.speciesProfileDao().getAll().map { it.cardCharacterId }.toSet()
                val definitions = ready.map { definition(it, catalog) }
                definitions.forEach { model ->
                    require(existing[model.id]?.catalogSpeciesId.let { it == null || it == model.catalogSpeciesId }) {
                        "Catalog definition identity collision"
                    }
                }
                db.characterDao().insertCharacter(*definitions.filter { it.id !in existing }.toTypedArray())
                db.speciesProfileDao().upsertNormalized(ready.filter { definitionId(it.id) !in profiles }.map { species ->
                    val entry = species.asEntry()
                    SpeciesProfile(definitionId(species.id), species.name, species.name, species.level, species.type,
                        entry.profile, entry.specialMoves, SpeciesSource.OFFICIAL_MATCHED,
                        appearanceKey = SpeciesCustomizationRepository.key(species.name))
                })
                val defaults = defaultSprites()
                definitions.filter { it.spriteId != PlaceholderSprite.ID.toLong() }
                    .forEach { setDefault(it.id, it.spriteId.toInt(), defaults) }
            }
            prepared = true
        }
        syncImportedBindings(catalog)
        synchronizeArtwork()
    } }

    /** Deterministic starter: Agumon when ready, else the first ready Child-stage species. */
    suspend fun starterDefinition(): CardCharacter = withContext(Dispatchers.IO) {
        val catalog = library().catalog()
        val ready = catalog.data.species.filter { catalog.isExpandedReady(it.id) }
        val starter = ready.firstOrNull { it.id == "digimon.agumon" }
            ?: ready.filter { CatalogGameplayProfile.tier(it) == 2 }.minByOrNull { it.name.lowercase(java.util.Locale.ROOT) }
            ?: ready.minByOrNull { it.name.lowercase(java.util.Locale.ROOT) }
            ?: error("No expanded-ready species available.")
        db.characterDao().getById(definitionId(starter.id))
            ?: library().install(requireNotNull(starter.dotSprites.defaultSetId)).let { sprite ->
                db.withTransaction { ensureDefinitionLocked(starter, catalog, sprite) }
            }
    }

    suspend fun definitionFor(nameOrId: String): CardCharacter = withContext(Dispatchers.IO) {
        val catalog = library().catalog()
        val species = requireNotNull(catalog.resolve(nameOrId)) { "Unknown catalog species" }
        require(species.kind == "DIGIMON") { "A reference object is not a Digimon" }
        db.characterDao().getById(definitionId(species.id))
            ?: (species.dotSprites.defaultSetId?.let { library().install(it) } ?: ensurePlaceholder()).let { sprite ->
                db.withTransaction { ensureDefinitionLocked(species, catalog, sprite) }
            }
    }

    private suspend fun ensureDefinitionLocked(species: CanonicalSpeciesDto, catalog: CanonicalSpeciesCatalog, sprite: Int): CardCharacter {
        require((species.dotSprites.defaultSetId?.let { catalog.setsById.getValue(it).spriteId } ?: PlaceholderSprite.ID) == sprite)
        val id = definitionId(species.id)
        val existing = db.characterDao().getById(id)
        require(existing == null || existing.catalogSpeciesId == species.id) { "Catalog definition identity collision" }
        val model = definition(species, catalog)
        if (existing == null) db.characterDao().insertCharacter(model)
        val entry = species.asEntry()
        if (db.speciesProfileDao().getByCardCharacterId(id) == null) db.speciesProfileDao().upsert(SpeciesProfile(id,
            species.name, species.name, species.level, species.type, entry.profile, entry.specialMoves, SpeciesSource.OFFICIAL_MATCHED))
        if (sprite != PlaceholderSprite.ID) setDefault(id, sprite, defaultSprites())
        return existing ?: model
    }

    private fun definition(species: CanonicalSpeciesDto, catalog: CanonicalSpeciesCatalog): CardCharacter {
        val set = species.dotSprites.defaultSetId?.let(catalog.setsById::getValue)
        val stats = CatalogGameplayProfile.stats(species)
        return CardCharacter(definitionId(species.id), null, (set?.spriteId ?: PlaceholderSprite.ID).toLong(), 0, CatalogGameplayProfile.tier(species),
            set?.let(CatalogGameplayProfile::attribute) ?: com.tamerslab.hardware.nfc.data.NfcCharacter.Attribute.None,
            stats.first, stats.second, stats.third,
            byteArrayOf(0xe0.toByte(), 7), 1, 1, species.id, species.id)
    }

    /**
     * Backfills the canonical binding on imported slots from their recognized profile.
     * Custom DIM rows that recognition never identified keep a null binding and are
     * remain usable even without a bundled catalog or artwork match.
     */
    private suspend fun syncImportedBindings(catalog: CanonicalSpeciesCatalog) = db.withTransaction {
        val imported = db.characterDao().getAllCharacters().filter { it.cardId != null }.associateBy { it.id }
        if (imported.isEmpty()) return@withTransaction
        val custom = db.speciesCustomizationDao().getCatalog().associateBy { it.speciesKey }
        val installed = db.spriteDao().getBundledSpriteIds().toSet()
        val defaults = defaultSprites()
        for (profile in db.speciesProfileDao().getAll()) {
            val character = imported[profile.cardCharacterId] ?: continue
            val species = catalog.resolve(profile.speciesName.orEmpty())
                ?: catalog.resolve(custom[profile.appearanceKey]?.originalKey.orEmpty())
                ?: catalog.resolve(profile.matchedName.orEmpty()) ?: continue
            db.openHelper.writableDatabase.execSQL("UPDATE CardCharacter SET speciesId = ? WHERE id = ? AND speciesId IS NOT ?",
                arrayOf<Any?>(species.id, character.id, species.id))
            val setId = species.dotSprites.defaultSetId ?: continue
            val set = catalog.setsById.getValue(setId)
            // The default set was installed while preparing its catalog definition.
            if (set.spriteId in installed) setDefault(character.id, set.spriteId, defaults)
        }
    }

    suspend fun refreshImportedBindings() = withContext(Dispatchers.IO) {
        syncImportedBindings(library().catalog())
    }

    private fun defaultSprites(): Map<Long, Int> = db.openHelper.readableDatabase
        .query("SELECT cardCharacterId, spriteId FROM SpeciesDefaultSprite").use { rows ->
            buildMap { while (rows.moveToNext()) put(rows.getLong(0), rows.getInt(1)) }
        }

    private fun setDefault(id: Long, sprite: Int, defaults: Map<Long, Int>) {
        if (defaults[id] != sprite) db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO SpeciesDefaultSprite(cardCharacterId, spriteId) VALUES (?, ?)", arrayOf<Any?>(id, sprite))
    }

    private suspend fun ensurePlaceholder(): Int {
        val placeholder = PlaceholderSprite.create()
        val existing = db.spriteDao().getById(PlaceholderSprite.ID)
        require(existing == null || existing == placeholder) { "Placeholder artwork identity collision" }
        if (existing == null) db.spriteDao().insertBundledSprite(placeholder)
        return PlaceholderSprite.ID
    }

    fun synchronizeArtwork(selected: SpritePreference = preference()) {
        val sql = db.openHelper.writableDatabase
        val current = sql.query("SELECT mode FROM AppSourceState WHERE id = 1").use { if (it.moveToFirst()) it.getString(0) else null }
        if (current != selected.name) sql.execSQL("INSERT OR REPLACE INTO AppSourceState(id, mode) VALUES (1, ?)", arrayOf<Any?>(selected.name))
    }

    companion object {
        fun definitionId(speciesId: String): Long {
            val hash = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(speciesId.toByteArray(Charsets.UTF_8))).long and Long.MAX_VALUE
            return -hash.coerceAtLeast(2)
        }
    }
}
