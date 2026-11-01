package com.tamerslab.battle.offline.tamers

import android.content.Context
import com.tamerslab.battle.BattleAssetPaths
import com.tamerslab.battle.BattleCharacterCatalog
import com.tamerslab.battle.domain.BattleAttribute
import com.tamerslab.battle.offline.core.BattleSpeciesIdentity
import com.tamerslab.battle.offline.data.VitalBattleProfile
import com.tamerslab.battle.offline.data.VitalStatScale
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.species.SpeciesDatabaseDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object ArenaSpeciesIndex {
    suspend fun load(context: Context, db: AppDatabase): List<ArenaSpecies> = withContext(Dispatchers.IO) {
        val assets = context.applicationContext.assets
        val metadata = assets.open("species.json").bufferedReader().use {
            Gson().fromJson(it, SpeciesDatabaseDto::class.java)
        }
        val stats = BattleCharacterCatalog.all(context)
        val folders = assets.list(BattleAssetPaths.CHARACTER_SPRITES).orEmpty().toSet()
        val choices = db.speciesCustomizationDao().getSpriteChoices().filter { it.spriteId != null }.map { it.speciesKey }.toSet()
        val preferredSpecies = choices + db.speciesCustomizationDao().getCatalog()
            .filter { it.speciesKey in choices }.mapNotNull { it.originalKey }
        val bundled = metadata.species.flatMap { (card, entries) ->
            val number = card.toIntOrNull()
            // Bucket zero is a general species encyclopedia, not a physical DIM slot map.
            if (number == null || number == 0) emptyList() else entries.mapNotNull { (slot, entry) ->
                val index = slot.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val id = String.format(Locale.ROOT, "dim%03d_mon%02d", number, index)
                val hasPreferred = BattleSpeciesIdentity.normalize(entry.name) in preferredSpecies
                if (id !in folders && !hasPreferred) return@mapNotNull null
                val frames = assets.list("${BattleAssetPaths.CHARACTER_SPRITES}/$id").orEmpty().toSet()
                if (("${id}_01.png" !in frames || "${id}_11.png" !in frames) && !hasPreferred) return@mapNotNull null
                val data = stats[id]
                // Cartridge battle phases are stat/art provenance, not the species' official level.
                val stage = levelStage(entry.level) ?: data?.phase?.takeIf { it in 1..6 }?.minus(1) ?: return@mapNotNull null
                ArenaSpecies(entry.name, stage, id.takeIf { "${id}_01.png" in frames }, null, data?.attribute ?: BattleAttribute.NONE,
                    entry.specialMoves.filter { it.isNotBlank() && !it.startsWith("[N ") },
                    data?.takeIf { it.hasAnyUsableStats && it.phase in 3..6 }?.let {
                        VitalBattleProfile(VitalStatScale.ARENA_EXTRACTED, it.hp, it.bp, it.ap, sourcePhase = it.phase)
                    }, entry.profile, nativeArtwork = "${id}_01.png" in frames || hasPreferred)
            }
        }
        val importedFallbackCards = mutableMapOf<Long, Boolean>()
        val characters = db.characterDao().getAllCharacters().associateBy { it.id }
        val renderable = db.spriteDao().getRenderableCharacterIds().toSet()
        val imported = db.speciesProfileDao().getAll().mapNotNull { profile ->
            val name = profile.speciesName?.takeIf(String::isNotBlank)
                ?: profile.matchedName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val character = characters[profile.cardCharacterId] ?: return@mapNotNull null
            if (profile.cardCharacterId !in renderable) return@mapNotNull null
            val fallbackArtwork = character.cardId?.let { cardId ->
                importedFallbackCards.getOrPut(cardId) {
                    db.cardDao().getCardById(cardId)?.cardId?.and(0xff) == 124
                }
            } ?: false
            ArenaSpecies(name, levelStage(profile.level) ?: character.stage.coerceIn(0, 5), null, profile.cardCharacterId,
                BattleAttribute.entries.firstOrNull { it.name.equals(character.attribute.toString(), true) } ?: BattleAttribute.NONE,
                profile.specialMoves.filter { it.isNotBlank() && !it.startsWith("[N ") }, null, profile.profileDescription,
                fallbackArtwork = fallbackArtwork, nativeArtwork = character.cardId != null,
                isCatalogDefinition = character.catalogSpeciesId != null)
        }
        (bundled + imported).distinctBy { Triple(BattleSpeciesIdentity.normalize(it.name), it.assetId, it.cardCharacterId) }
    }

    private fun levelStage(level: String?): Int? = when (level?.lowercase(Locale.ROOT)) {
        "baby i", "fresh" -> 0
        "baby ii", "in-training" -> 1
        "child", "rookie" -> 2
        "adult", "champion", "armor" -> 3
        "perfect" -> 4
        "ultimate", "mega", "super ultimate" -> 5
        else -> null
    }
}
