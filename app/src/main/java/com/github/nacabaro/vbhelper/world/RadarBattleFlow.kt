package com.github.nacabaro.vbhelper.world

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.battle.offline.data.BattleStatSourceScale
import com.github.nacabaro.vbhelper.battle.offline.data.VitalBattleProfile
import com.github.nacabaro.vbhelper.battle.offline.data.VitalStatScale
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.screens.OfflineBattleSpriteSet
import kotlin.random.Random

/** Database side effects allowed when a World Radar battle reaches a terminal outcome. */
data class RadarBattleEffect(
    val recordsBattle: Boolean,
    val won: Boolean?,
    val removesSpawn: Boolean
)

fun BattleOutcome.toRadarBattleEffect(): RadarBattleEffect = when (this) {
    BattleOutcome.ALLIED_VICTORY -> RadarBattleEffect(
        recordsBattle = true,
        won = true,
        removesSpawn = true
    )

    BattleOutcome.OPPOSING_VICTORY -> RadarBattleEffect(
        recordsBattle = true,
        won = false,
        removesSpawn = false
    )

    BattleOutcome.DRAW,
    BattleOutcome.ABANDONED -> RadarBattleEffect(
        recordsBattle = false,
        won = null,
        removesSpawn = false
    )
}

/** Builds the wild side directly from its DiM/BEM card record. */
fun worldRadarBattleParticipant(spawn: WorldDtos.SpawnWithDetails): OfflineBattleParticipant {
    val scale = if (spawn.isBemCard) VitalStatScale.BEM else VitalStatScale.DIM
    val personality = DigimonPersonalityGenerator.generate(
        individualId = spawn.individualId,
        attribute = spawn.attribute,
        stage = spawn.stage,
        now = spawn.spawnedAt,
        random = Random(spawn.individualId.hashCode())
    ).personalityType
    val externalCharacterId = spawn.externalCharacterId

    return OfflineBattleParticipant(
        character = null,
        assetCharacterId = externalCharacterId,
        externalCharacterId = externalCharacterId,
        cardCharacterId = spawn.cardCharacterId,
        displayName = spawn.speciesName?.takeIf(String::isNotBlank) ?: "Wild Digimon #${spawn.id}",
        maxHp = spawn.baseHp.coerceAtLeast(1),
        attackPower = spawn.baseAp.coerceAtLeast(1),
        stage = spawn.stage,
        vitalStats = VitalBattleProfile(
            scale = scale,
            baseHp = spawn.baseHp,
            baseBp = spawn.baseBp,
            baseAp = spawn.baseAp,
            mood = spawn.mood
        ),
        attribute = spawn.attribute.toBattleAttribute(),
        individualId = spawn.individualId,
        stableRngKey = spawn.individualId,
        personalityType = personality,
        spriteSet = OfflineBattleSpriteSet(
            idle = spawn.spriteIdle,
            idle2 = spawn.spriteIdle2,
            walk = spawn.spriteWalk,
            walk2 = spawn.spriteWalk2,
            attack = spawn.spriteRun,
            defeated = spawn.spriteRun2,
            width = spawn.spriteWidth,
            height = spawn.spriteHeight
        ),
        statSourceScale = if (spawn.isBemCard) {
            BattleStatSourceScale.CARD_BEM
        } else {
            BattleStatSourceScale.CARD_DIM
        }
    )
}

private fun NfcCharacter.Attribute.toBattleAttribute(): BattleAttribute = when (this) {
    NfcCharacter.Attribute.Virus -> BattleAttribute.VIRUS
    NfcCharacter.Attribute.Data -> BattleAttribute.DATA
    NfcCharacter.Attribute.Vaccine -> BattleAttribute.VACCINE
    NfcCharacter.Attribute.Free -> BattleAttribute.FREE
    NfcCharacter.Attribute.None -> BattleAttribute.NONE
}
