package com.github.nacabaro.vbhelper.battle.offline.data

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType

/**
 * Room projection of the persistent data needed to build an offline combatant.
 * It deliberately carries the individual's identity and raw source stats so
 * battle setup does not need per-character lookups or lose personality data.
 */
data class BattleParticipantProfile(
    val sourceCharacterId: Long,
    val individualId: String,
    val externalCharacterId: String,
    val displayName: String,
    val stage: Int,
    val attribute: NfcCharacter.Attribute,
    val personalityType: DigimonPersonalityType?,
    val baseHp: Int,
    val baseBp: Int,
    val baseAp: Int,
    val trainingHp: Int,
    val trainingBp: Int,
    val trainingAp: Int,
    val vitalPoints: Int,
    val mood: Int,
    val isBemCard: Boolean,
    /** Raw SpeciesProfile.specialMoves JSON; parsed lazily so the DAO stays a plain projection. */
    val specialMovesJson: String? = null,
    /** Solo Blast Evolution slot (NONE/POWER/FORM); see BlastEvolutionSlot. */
    val blastMode: String = "NONE",
    /** Target species for a FORM Blast. */
    val blastTargetSpecies: String? = null,
    /** Duo Jogress slot: result species chosen by this individual as fusion lead. */
    val jogressResultSpecies: String? = null
) {
    /** Stable across side changes and battle sessions; consumed by the RNG phase. */
    val stableRngKey: String
        get() = individualId

    /** First species skill-list entry, used as the innate special's display name. */
    fun firstSpecialMove(): String? = runCatching {
        val raw = specialMovesJson?.takeIf { it.isNotBlank() } ?: return null
        com.google.gson.JsonParser.parseString(raw).asJsonArray
            .firstOrNull()?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()

    val statSourceScale: BattleStatSourceScale
        get() = if (isBemCard) BattleStatSourceScale.CARD_BEM else BattleStatSourceScale.CARD_DIM
}

enum class BattleStatSourceScale {
    CARD_DIM,
    CARD_BEM,
    ARENA_EXTRACTED,
    STAGE_FALLBACK
}
