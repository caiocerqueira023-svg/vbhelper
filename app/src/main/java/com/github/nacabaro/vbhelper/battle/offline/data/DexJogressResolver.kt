package com.github.nacabaro.vbhelper.battle.offline.data

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.database.AppDatabase

/**
 * Resolves Dex card Jogress routes (device data) into species-level options.
 * Specific routes name an exact partner; attribute routes accept any partner
 * with the required attribute. Results are species names from SpeciesProfile,
 * so they compose with the same matching used everywhere else.
 */
object DexJogressResolver {
    fun nfcToBattleAttribute(attribute: NfcCharacter.Attribute): BattleAttribute = when (attribute) {
        NfcCharacter.Attribute.Virus -> BattleAttribute.VIRUS
        NfcCharacter.Attribute.Data -> BattleAttribute.DATA
        NfcCharacter.Attribute.Vaccine -> BattleAttribute.VACCINE
        NfcCharacter.Attribute.Free -> BattleAttribute.FREE
        NfcCharacter.Attribute.None -> BattleAttribute.NONE
    }

    suspend fun speciesNameOf(db: AppDatabase, cardCharacterId: Long): String? =
        db.speciesProfileDao().getByCardCharacterId(cardCharacterId)?.let {
            it.speciesName?.takeIf { name -> name.isNotBlank() }
                ?: it.matchedName?.takeIf { name -> name.isNotBlank() }
        }

    /** (result, partner) for specific routes whose partner card is imported. */
    suspend fun specificTargets(
        db: AppDatabase,
        cardCharacterId: Long
    ): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (route in db.cardFusionsDao().getSpecificRoutesFrom(cardCharacterId)) {
            val result = speciesNameOf(db, route.toCharaId) ?: continue
            val partnerCard = db.cardDao().getCardByCardId(route.partnerCardNumber).firstOrNull() ?: continue
            val partnerChara = db.characterDao().getCharacterByMonIndex(route.partnerCharaIndex, partnerCard.id)
            val partner = speciesNameOf(db, partnerChara.id) ?: continue
            out += result to partner
        }
        return out.distinct()
    }

    /** (result, required partner attribute) for attribute routes. */
    suspend fun attributeTargets(
        db: AppDatabase,
        cardCharacterId: Long
    ): List<Pair<String, BattleAttribute>> =
        db.cardFusionsDao().getAttributeRoutesFrom(cardCharacterId).mapNotNull { route ->
            speciesNameOf(db, route.toCharaId)?.let { it to nfcToBattleAttribute(route.attribute) }
        }.distinct()

    /** Partner species accepted for an equipped result via specific routes. */
    suspend fun specificPartners(
        db: AppDatabase,
        cardCharacterId: Long,
        choiceResult: String
    ): List<String> =
        specificTargets(db, cardCharacterId)
            .filter { it.first.equals(choiceResult, ignoreCase = true) }
            .map { it.second }
            .distinct()

    /** Partner attribute accepted for an equipped result via attribute routes. */
    suspend fun attributeFor(
        db: AppDatabase,
        cardCharacterId: Long,
        choiceResult: String
    ): BattleAttribute? =
        attributeTargets(db, cardCharacterId)
            .firstOrNull { it.first.equals(choiceResult, ignoreCase = true) }
            ?.second
}
