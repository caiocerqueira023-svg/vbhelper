package com.github.nacabaro.vbhelper.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.domain.device_data.BlastEvolutionSlot
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAiProfile
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleStats
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.EncounterBattleProfiles
import com.github.nacabaro.vbhelper.battle.offline.data.BattleParticipantProfile
import com.github.nacabaro.vbhelper.battle.offline.data.BattleStatSourceScale
import com.github.nacabaro.vbhelper.battle.offline.data.VitalBattleProfile
import com.github.nacabaro.vbhelper.battle.offline.data.VitalStatScale
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.battle.ExtractedBattleCharacter
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

data class OfflineBattleSpriteSet(
    val idle: ByteArray,
    val idle2: ByteArray,
    val walk: ByteArray,
    val walk2: ByteArray,
    val attack: ByteArray,
    val defeated: ByteArray,
    val width: Int,
    val height: Int
)

data class OfflineBattleParticipant(
    val character: CharacterDtos.CharacterWithSprites?,
    val assetCharacterId: String?,
    val displayName: String,
    val maxHp: Int,
    val attackPower: Int,
    val stage: Int = 1,
    val externalCharacterId: String? = null,
    val vitalStats: VitalBattleProfile? = null,
    val attribute: BattleAttribute = BattleAttribute.NONE,
    val individualId: String? = null,
    val stableRngKey: String = individualId ?: assetCharacterId ?: character?.id?.toString() ?: displayName,
    val personalityType: DigimonPersonalityType = DigimonPersonalityType.FRIENDLY,
    val techniqueIds: List<String> = GenericTechniqueCatalog.defaultTechniqueIds,
    val spriteSet: OfflineBattleSpriteSet? = null,
    /** Species special-move name for the innate special; null keeps the catalog name. */
    val specialDisplayNameOverride: String? = null,
    /** Solo Blast Evolution slot (NONE/POWER/FORM). */
    val blastMode: String = BlastEvolutionSlot.NONE,
    /** Target species for a FORM Blast. */
    val blastTargetSpecies: String? = null,
    /** Duo Jogress slot: result species chosen by this individual as fusion lead. */
    val jogressResultSpecies: String? = null,
    /** Raw species name for universal-table matching; null degrades Jogress to dual-strike. */
    val speciesName: String? = null,
    val initialHealth: Int? = null,
    val initialEnergy: Int? = null,
    /** Local species key for wild participants; stored partners use character.charId. */
    val cardCharacterId: Long? = null,
    val aiProfile: BattleAiProfile = BattleAiProfile(),
    val statSourceScale: BattleStatSourceScale = when (vitalStats?.scale) {
        VitalStatScale.DIM -> BattleStatSourceScale.CARD_DIM
        VitalStatScale.BEM -> BattleStatSourceScale.CARD_BEM
        VitalStatScale.ARENA_EXTRACTED -> BattleStatSourceScale.ARENA_EXTRACTED
        VitalStatScale.STAGE_FALLBACK -> BattleStatSourceScale.STAGE_FALLBACK
        null -> BattleStatSourceScale.STAGE_FALLBACK
    }
) {
    val stableId: String
        get() = individualId ?: character?.id?.toString() ?: assetCharacterId ?: displayName

    fun trainingStats(): TrainingBattleStats = TrainingBattleStats.forParticipant(stage, vitalStats)
}

fun offlineBattleParticipant(
    character: CharacterDtos.CharacterWithSprites,
    maxHp: Int,
    attackPower: Int,
    baseBp: Int = 0,
    trainingHp: Int = 0,
    trainingBp: Int = 0,
    trainingAp: Int = 0,
    externalCharacterId: String? = null,
    personalityType: DigimonPersonalityType = DigimonPersonalityType.FRIENDLY,
    individualId: String? = null,
    stableRngKey: String? = null,
    sourceScale: BattleStatSourceScale? = null,
    storedFallbackName: String? = null,
    /** Species special-move name for the innate special; null keeps the catalog name. */
    specialDisplayNameOverride: String? = null,
    /** Solo Blast Evolution slot (NONE/POWER/FORM). */
    blastMode: String = BlastEvolutionSlot.NONE,
    /** Target species for a FORM Blast. */
    blastTargetSpecies: String? = null,
    /** Duo Jogress slot: result species chosen by this individual as fusion lead. */
    jogressResultSpecies: String? = null,
    /** Raw species name for universal-table matching; null degrades Jogress to dual-strike. */
    speciesName: String? = null
): OfflineBattleParticipant {
    val profile = VitalBattleProfile(
        scale = if (character.isBemCard) VitalStatScale.BEM else VitalStatScale.DIM,
        baseHp = maxHp,
        baseBp = baseBp,
        baseAp = attackPower,
        trainingHp = trainingHp,
        trainingBp = trainingBp,
        trainingAp = trainingAp,
        vitalPoints = character.vitalPoints,
        mood = character.mood
    ).takeIf { it.hasAnyUsableBaseStats }
    return OfflineBattleParticipant(
        character = character,
        assetCharacterId = null,
        displayName = character.nickname?.takeIf { it.isNotBlank() }
            ?: character.speciesName?.takeIf { it.isNotBlank() }
            ?: storedFallbackName ?: "Stored Digimon #${character.id}",
        maxHp = maxHp.coerceAtLeast(1),
        attackPower = attackPower.coerceAtLeast(1),
        stage = character.stage,
        externalCharacterId = externalCharacterId,
        vitalStats = profile,
        individualId = individualId,
        stableRngKey = stableRngKey ?: individualId ?: character.id.toString(),
        personalityType = personalityType,
        specialDisplayNameOverride = specialDisplayNameOverride,
        blastMode = blastMode,
        blastTargetSpecies = blastTargetSpecies,
        jogressResultSpecies = jogressResultSpecies,
        speciesName = speciesName,
        statSourceScale = sourceScale ?: when (profile?.scale) {
            VitalStatScale.DIM -> BattleStatSourceScale.CARD_DIM
            VitalStatScale.BEM -> BattleStatSourceScale.CARD_BEM
            VitalStatScale.ARENA_EXTRACTED -> BattleStatSourceScale.ARENA_EXTRACTED
            VitalStatScale.STAGE_FALLBACK -> BattleStatSourceScale.STAGE_FALLBACK
            null -> BattleStatSourceScale.STAGE_FALLBACK
        },
        attribute = when (character.attribute) {
            com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.Virus -> BattleAttribute.VIRUS
            com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.Data -> BattleAttribute.DATA
            com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.Vaccine -> BattleAttribute.VACCINE
            com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.Free -> BattleAttribute.FREE
            com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.None -> BattleAttribute.NONE
        }
    )
}

fun offlineBattleParticipant(
    character: CharacterDtos.CharacterWithSprites,
    profile: BattleParticipantProfile
): OfflineBattleParticipant {
    val defaultHp = when {
        profile.stage <= 1 -> 1800
        profile.stage == 2 -> 2600
        profile.stage == 3 -> 3600
        else -> 4400
    }
    val defaultAttack = when {
        profile.stage <= 1 -> 700
        profile.stage == 2 -> 1050
        profile.stage == 3 -> 1450
        else -> 1850
    }
    return offlineBattleParticipant(
        character = character,
        maxHp = profile.baseHp.takeIf { VitalBattleProfile.isKnownBaseStat(it) } ?: defaultHp,
        attackPower = profile.baseAp.takeIf { VitalBattleProfile.isKnownBaseStat(it) } ?: defaultAttack,
        baseBp = profile.baseBp,
        trainingHp = profile.trainingHp,
        trainingBp = profile.trainingBp,
        trainingAp = profile.trainingAp,
        externalCharacterId = profile.externalCharacterId,
        personalityType = profile.personalityType ?: DigimonPersonalityType.FRIENDLY,
        individualId = profile.individualId,
        stableRngKey = profile.stableRngKey,
        sourceScale = profile.statSourceScale,
        specialDisplayNameOverride = profile.firstSpecialMove(),
        blastMode = profile.blastMode,
        blastTargetSpecies = profile.blastTargetSpecies,
        jogressResultSpecies = profile.jogressResultSpecies,
        speciesName = character.speciesName
    ).copy(displayName = profile.displayName)
}

fun assetOfflineBattleParticipant(
    characterId: String,
    displayName: String,
    maxHp: Int,
    attackPower: Int,
    stage: Int = 1,
    attribute: BattleAttribute = BattleAttribute.NONE,
    extractedData: ExtractedBattleCharacter? = null
): OfflineBattleParticipant {
    val encounter = EncounterBattleProfiles.wild(characterId)
    val extracted = extractedData?.takeIf {
        it.characterId.equals(characterId, ignoreCase = true) && it.phase in 3..6 && it.hasAnyUsableStats
    }
    val vitalProfile = extracted?.let {
        VitalBattleProfile(
            scale = VitalStatScale.ARENA_EXTRACTED,
            baseHp = it.hp,
            baseBp = it.bp,
            baseAp = it.ap,
            sourcePhase = it.phase
        )
    }
    return OfflineBattleParticipant(
        character = null,
        assetCharacterId = characterId,
        displayName = displayName,
        maxHp = extracted?.hp?.takeIf { VitalBattleProfile.isKnownBaseStat(it) } ?: maxHp.coerceAtLeast(1),
        attackPower = extracted?.ap?.takeIf { VitalBattleProfile.isKnownBaseStat(it) } ?: attackPower.coerceAtLeast(1),
        stage = stage,
        externalCharacterId = characterId,
        vitalStats = vitalProfile,
        attribute = attribute,
        stableRngKey = "asset:$characterId:profile-v1",
        personalityType = DigimonPersonalityType.FRIENDLY,
        techniqueIds = encounter.techniqueIds,
        speciesName = displayName,
        aiProfile = encounter.ai.copy(profileId = "arena:${encounter.ai.profileId}", autonomousSpecial = false),
        statSourceScale = if (vitalProfile != null) {
            BattleStatSourceScale.ARENA_EXTRACTED
        } else {
            BattleStatSourceScale.STAGE_FALLBACK
        }
    )
}

@Composable
fun OfflineBattleEntryPanel(
    player: OfflineBattleParticipant?,
    opponents: List<OfflineBattleParticipant>,
    isLoading: Boolean = false,
    onStartBattle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = CutCornerShape(9.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, SurfaceStroke)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = stringResource(R.string.ui_battle_entry_title),
                color = TextPrimaryOnDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.ui_battle_entry_subtitle),
                color = TextSecondaryOnDark,
                fontSize = 12.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = when {
                        player != null -> stringResource(R.string.ui_battle_entry_partner, player.displayName)
                        isLoading -> stringResource(R.string.ui_battle_entry_partner_loading)
                        else -> stringResource(R.string.ui_battle_entry_partner_missing)
                    },
                    modifier = Modifier.weight(1f),
                    color = VitalCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = if (isLoading) stringResource(R.string.ui_battle_entry_loading_short) else stringResource(R.string.ui_battle_entry_opponents, opponents.size),
                    color = TextSecondaryOnDark,
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = onStartBattle,
                enabled = !isLoading && player != null && opponents.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = CutCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(stringResource(R.string.ui_battle_entry_configure), fontWeight = FontWeight.Bold)
            }
            Text(
                text = when {
                    isLoading -> stringResource(R.string.ui_battle_entry_status_preparing)
                    player == null -> stringResource(R.string.ui_battle_entry_status_no_partner)
                    opponents.isEmpty() -> stringResource(R.string.ui_battle_entry_status_no_foes)
                    else -> stringResource(R.string.ui_battle_entry_status_ready)
                },
                modifier = Modifier.height(16.dp),
                color = TextSecondaryOnDark,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

