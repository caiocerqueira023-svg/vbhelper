package com.github.nacabaro.vbhelper.source

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import java.util.Locale

/**
 * Projects a catalog-defined partner onto an exact loaded DIM/BEM slot for watch
 * transfer. The partner keeps its app identity, history and training; only the
 * selected DIM/BEM exclusively supplies the normal card stat and outgoing device rules. Never invents a match:
 * a vaguely similar Digimon is not a compatible conversion.
 */
class BraceletConversionService(private val db: AppDatabase) {
    data class CompatibleTarget(
        val cardCharacter: CardCharacter,
        val cardName: String,
        val speciesName: String,
        val isBEm: Boolean,
    )

    suspend fun partnerSpeciesName(userCharacterId: Long): String? {
        val userCharacter = db.userCharacterDao().getCharacter(userCharacterId)
        val definition = db.characterDao().getById(userCharacter.charId) ?: return null
        if (definition.cardId != null) {
            return db.speciesProfileDao().getByCardCharacterId(definition.id)
                ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
        }
        return definition.speciesId?.let { canonicalName(it) }
            ?: db.speciesProfileDao().getByCardCharacterId(definition.id)
                ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
    }

    private suspend fun canonicalName(speciesId: String): String? =
        db.expandedCatalog?.let {
            runCatching { it.definitionFor(speciesId) }.getOrNull()
        }?.let { db.speciesProfileDao().getByCardCharacterId(it.id)?.speciesName }

    /** Imported slots whose canonical species exactly matches the partner. */
    suspend fun compatibleTargets(userCharacterId: Long): List<CompatibleTarget> {
        val userCharacter = db.userCharacterDao().getCharacter(userCharacterId)
        val definition = db.characterDao().getById(userCharacter.charId) ?: return emptyList()
        if (definition.cardId != null) return emptyList()
        val wanted = definition.speciesId ?: return emptyList()
        return db.characterDao().getAllCharacters()
            .filter { it.cardId != null && it.catalogSpeciesId == null && it.speciesId == wanted }
            .mapNotNull { slot ->
                val card = slot.cardId?.let { db.cardDao().getCardById(it) } ?: return@mapNotNull null
                val name = db.speciesProfileDao().getByCardCharacterId(slot.id)
                    ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
                    ?: return@mapNotNull null
                CompatibleTarget(slot, card.name, name, card.isBEm)
            }
            .sortedWith(compareBy({ !it.isBEm }, { it.cardName.lowercase(Locale.ROOT) }, { it.cardCharacter.charaIndex }))
    }

    /** Assigns exact DIM/BEM stat/export rules; native baselines and earned progress are independent. */
    suspend fun convert(userCharacterId: Long, targetCardCharacterId: Long) = db.withTransaction {
        db.participation?.snapshot()?.requireOwned(userCharacterId)
        val target = db.characterDao().getById(targetCardCharacterId)
            ?: error("Converted slot is no longer available.")
        require(target.cardId != null && target.catalogSpeciesId == null) { "Conversion requires an exact loaded DIM/BEM slot." }
        val userCharacter = db.userCharacterDao().getCharacter(userCharacterId)
        val definition = db.characterDao().getById(userCharacter.charId)
            ?: error("Partner not found.")
        require(definition.cardId == null && definition.speciesId != null) { "Only catalog-defined partners need conversion." }
        val targetSpecies = target.speciesId
            ?: db.speciesProfileDao().getByCardCharacterId(target.id)
                ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
                ?.let { "name:$it" }
        val partnerSpecies = definition.speciesId
            ?: db.speciesProfileDao().getByCardCharacterId(definition.id)
                ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
                ?.let { "name:$it" }
        require(targetSpecies != null && targetSpecies == partnerSpecies ||
            sameCanonicalName(definition.id, target.id)) { "The converted slot must be the exact same species and form." }
        db.userCharacterDao().setExportTarget(userCharacterId, targetCardCharacterId)
    }

    suspend fun clearConversion(userCharacterId: Long) = db.withTransaction {
        db.userCharacterDao().setExportTarget(userCharacterId, null)
    }

    private suspend fun sameCanonicalName(definitionId: Long, targetId: Long): Boolean {
        val left = db.speciesProfileDao().getByCardCharacterId(definitionId)
            ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName } ?: return false
        val right = db.speciesProfileDao().getByCardCharacterId(targetId)
            ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName } ?: return false
        return BattleSpeciesIdentity.normalize(left) == BattleSpeciesIdentity.normalize(right)
    }
}
