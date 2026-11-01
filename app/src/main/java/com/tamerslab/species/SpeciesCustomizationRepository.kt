package com.tamerslab.species

import androidx.room.withTransaction
import com.tamerslab.battle.offline.core.BattleSpeciesIdentity
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.species.SpeciesCatalogEntry
import com.tamerslab.domain.species.SpeciesProfile
import com.tamerslab.domain.species.SpeciesSource
import com.tamerslab.domain.species.SpeciesSpriteChoice
import com.tamerslab.ui.SpritePreviewPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpeciesCustomizationRepository(private val db: AppDatabase) {
    val catalogChanges = db.speciesCustomizationDao().observeCatalog()

    suspend fun mergeCatalog(base: List<SpeciesEntryDto>): List<SpeciesEntryDto> {
        val local = db.speciesCustomizationDao().getCatalog()
        val replaced = local.flatMap { listOfNotNull(it.speciesKey, it.originalKey) }.toSet()
        val identities = base.associateBy { key(it.name) }
        val customized = local.map { entry ->
            val identity = identities[entry.originalKey] ?: identities[entry.speciesKey]
            asEntry(entry).copy(speciesId = identity?.speciesId, dotSprites = identity?.dotSprites)
        }
        return (base.filterNot { key(it.name) in replaced } + customized)
            .distinctBy { key(it.name) }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }

    suspend fun applyProfile(profile: SpeciesProfile): SpeciesProfile {
        val entry = db.speciesCustomizationDao().getEntry(key(profile.speciesName ?: profile.matchedName.orEmpty()))
            ?: return profile
        return profile.copy(speciesName = entry.name, level = entry.level, type = entry.type,
            profileDescription = entry.profile, specialMoves = entry.specialMoves, source = SpeciesSource.MANUAL,
            appearanceKey = entry.speciesKey)
    }

    suspend fun save(originalName: String?, input: SpeciesEntryDto, spriteId: Int?): SpeciesEntryDto = withContext(Dispatchers.IO) {
        val name = input.name.trim()
        require(name.isNotEmpty() && name.length <= 160 && key(name).isNotEmpty())
        require(input.level.orEmpty().length <= 100 && input.type.orEmpty().length <= 100)
        require(input.profile.orEmpty().length <= 20_000 && input.specialMoves.size <= 64)
        require(input.specialMoves.all { it.length <= 160 })
        val cleaned = input.copy(name = name, level = input.level?.trim()?.takeIf(String::isNotEmpty),
            type = input.type?.trim()?.takeIf(String::isNotEmpty), profile = input.profile?.trim()?.takeIf(String::isNotEmpty),
            specialMoves = input.specialMoves.map(String::trim).filter(String::isNotEmpty).distinct())
        db.withTransaction {
            val dao = db.speciesCustomizationDao()
            val oldKey = originalName?.let(::key)
            val previous = oldKey?.let { dao.getEntry(it) }
            val newKey = key(name)
            val collision = dao.getEntry(newKey)
            require(collision == null || collision.speciesKey == previous?.speciesKey)
            // Editing a bundled/downloaded species never mutates the original record:
            // it creates a separate user-owned copy and leaves existing profiles assigned.
            val isCopy = oldKey != null && previous == null
            if (isCopy) require(newKey != oldKey) { "A copy needs a different name so the original stays in the catalog" }
            if (spriteId != null) {
                val sprite = requireNotNull(db.spriteDao().getById(spriteId))
                require(SpritePreviewPolicy.validFrame(sprite.width, sprite.height, sprite.spriteIdle1.size))
            }
            val affected = if (oldKey != null && previous != null) db.speciesProfileDao().getByAppearanceKey(oldKey) else emptyList()
            if (previous != null && previous.speciesKey != newKey) dao.deleteEntry(previous.speciesKey)
            dao.upsertEntry(SpeciesCatalogEntry(newKey, cleaned.name,
                originalKey = if (previous != null) previous.originalKey ?: oldKey else null,
                level = cleaned.level, type = cleaned.type, profile = cleaned.profile, specialMoves = cleaned.specialMoves))
            val renamedKey = oldKey?.takeIf { it != newKey }
            if (previous != null && renamedKey != null) dao.clearSpriteChoice(renamedKey)
            if (spriteId == null) dao.clearSpriteChoice(newKey) else dao.upsertSpriteChoice(SpeciesSpriteChoice(newKey, spriteId))
            affected.forEach { profile ->
                db.speciesProfileDao().upsert(profile.copy(speciesName = cleaned.name, level = cleaned.level,
                    type = cleaned.type, profileDescription = cleaned.profile, specialMoves = cleaned.specialMoves,
                    source = SpeciesSource.MANUAL))
            }
        }
        cleaned
    }

    companion object {
        fun key(name: String): String = BattleSpeciesIdentity.normalize(name)
        fun asEntry(entry: SpeciesCatalogEntry) = SpeciesEntryDto(entry.name, entry.level, entry.type, entry.profile, entry.specialMoves)
    }
}
