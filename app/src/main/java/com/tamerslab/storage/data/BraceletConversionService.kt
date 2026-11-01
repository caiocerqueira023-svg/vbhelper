package com.tamerslab.storage.data

import androidx.room.withTransaction
import com.tamerslab.battle.offline.core.BattleSpeciesIdentity
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.card.CardCharacter
import com.tamerslab.domain.device_data.hasPhysicalSlot
import com.tamerslab.domain.device_data.isCatalogPartner
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

    /** Imported slots whose species matches the partner, by canonical id or by recognized name. */
    suspend fun compatibleTargets(userCharacterId: Long): List<CompatibleTarget> {
        val userCharacter = db.userCharacterDao().getCharacter(userCharacterId)
        val definition = db.characterDao().getById(userCharacter.charId) ?: return emptyList()
        if (!definition.isCatalogPartner) return emptyList()
        val wanted = definition.speciesId
        val wantedName = db.speciesProfileDao().getByCardCharacterId(definition.id)
            ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
        return db.characterDao().getAllCharacters()
            .filter { it.cardId != null && it.catalogSpeciesId == null }
            .mapNotNull { slot ->
                val name = db.speciesProfileDao().getByCardCharacterId(slot.id)
                    ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
                    ?: return@mapNotNull null
                val matches = (wanted != null && slot.speciesId == wanted) ||
                    (wantedName != null && BattleSpeciesIdentity.normalize(name) ==
                        BattleSpeciesIdentity.normalize(wantedName))
                if (!matches) return@mapNotNull null
                val card = slot.cardId?.let { db.cardDao().getCardById(it) } ?: return@mapNotNull null
                CompatibleTarget(slot, card.name, name, card.isBEm)
            }
            .sortedWith(compareBy({ !it.isBEm }, { it.cardName.lowercase(Locale.ROOT) }, { it.cardCharacter.charaIndex }))
    }

    /** Assigns exact DIM/BEM stat/export rules; native baselines and earned progress are independent. */
    suspend fun convert(userCharacterId: Long, targetCardCharacterId: Long) = db.withTransaction {
        db.participation?.snapshot()?.requireOwned(userCharacterId)
        val target = db.characterDao().getById(targetCardCharacterId)
            ?: error("Converted slot is no longer available.")
        require(target.hasPhysicalSlot && target.catalogSpeciesId == null) { "Conversion requires an exact loaded DIM/BEM slot." }
        val userCharacter = db.userCharacterDao().getCharacter(userCharacterId)
        val definition = db.characterDao().getById(userCharacter.charId)
            ?: error("Partner not found.")
        require(definition.isCatalogPartner && definition.speciesId != null) { "Only catalog-defined partners need conversion." }
        require(matchesSpecies(definition, target)) { "The converted slot must be the exact same species and form." }
        db.userCharacterDao().setExportTarget(userCharacterId, targetCardCharacterId)
    }

    /** A slot matches by canonical species id or by recognized species name. */
    private suspend fun matchesSpecies(definition: CardCharacter, target: CardCharacter): Boolean {
        val wanted = definition.speciesId
        if (wanted != null && target.speciesId == wanted) return true
        return sameCanonicalName(definition.id, target.id)
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
