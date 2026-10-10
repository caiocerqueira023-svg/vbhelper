package com.github.nacabaro.vbhelper.battle.offline.tamers

import android.content.Context
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import com.github.nacabaro.vbhelper.battle.BattleCharacterCatalog
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity
import com.github.nacabaro.vbhelper.battle.offline.data.VitalBattleProfile
import com.github.nacabaro.vbhelper.battle.offline.data.VitalStatScale
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.species.SpeciesDatabaseDto
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
        val bundled = metadata.species.flatMap { (card, entries) ->
            val number = card.toIntOrNull()
            // Bucket zero is a general species encyclopedia, not a physical DIM slot map.
            if (number == null || number == 0) emptyList() else entries.mapNotNull { (slot, entry) ->
                val index = slot.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val id = String.format(Locale.ROOT, "dim%03d_mon%02d", number, index)
                if (id !in folders) return@mapNotNull null
                val frames = assets.list("${BattleAssetPaths.CHARACTER_SPRITES}/$id").orEmpty().toSet()
                if ("${id}_01.png" !in frames || "${id}_11.png" !in frames) return@mapNotNull null
                val data = stats[id]
                // Cartridge battle phases are stat/art provenance, not the species' official level.
                val stage = levelStage(entry.level) ?: data?.phase?.takeIf { it in 1..6 }?.minus(1) ?: return@mapNotNull null
                ArenaSpecies(entry.name, stage, id, null, data?.attribute ?: BattleAttribute.NONE,
                    entry.specialMoves.filter { it.isNotBlank() && !it.startsWith("[N ") },
                    data?.takeIf { it.hasAnyUsableStats && it.phase in 3..6 }?.let {
                        VitalBattleProfile(VitalStatScale.ARENA_EXTRACTED, it.hp, it.bp, it.ap, sourcePhase = it.phase)
                    }, entry.profile)
            }
        }
        val imported = db.speciesProfileDao().getAll().mapNotNull { profile ->
            val name = profile.speciesName?.takeIf(String::isNotBlank)
                ?: profile.matchedName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val character = db.characterDao().getById(profile.cardCharacterId) ?: return@mapNotNull null
            val art = db.spriteDao().getForCharacter(profile.cardCharacterId) ?: return@mapNotNull null
            if (art.width <= 0 || art.height <= 0 || art.spriteIdle1.isEmpty() || art.spriteAttack.isEmpty()) return@mapNotNull null
            ArenaSpecies(name, levelStage(profile.level) ?: character.stage.coerceIn(0, 5), null, profile.cardCharacterId,
                BattleAttribute.entries.firstOrNull { it.name.equals(character.attribute.toString(), true) } ?: BattleAttribute.NONE,
                profile.specialMoves.filter { it.isNotBlank() && !it.startsWith("[N ") }, null, profile.profileDescription)
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
