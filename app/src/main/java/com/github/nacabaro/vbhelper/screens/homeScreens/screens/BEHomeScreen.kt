package com.github.nacabaro.vbhelper.screens.homeScreens.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ActiveDigimonCard
import com.github.nacabaro.vbhelper.components.InfoStatRow
import com.github.nacabaro.vbhelper.components.ItemDisplay
import com.github.nacabaro.vbhelper.components.TransformationHistoryCard
import com.github.nacabaro.vbhelper.components.VitalsHeaderStat
import com.github.nacabaro.vbhelper.components.WeeklyVitalsChart
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.itemsScreen.ItemsScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.itemsScreen.getIconResource
import com.github.nacabaro.vbhelper.utils.BitmapData

/**
 * Home layout for a BE device, whether the character came from a DiM or a
 * BE Memory card. It mirrors [VBDiMHomeScreen] so every DiM/BEM character
 * reads the same way, while keeping the BE-only status (rank, training
 * limit, held item and the training gains) that a VB bracelet does not have.
 */
@Composable
fun BEHomeScreen(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    beData: BECharacterData,
    transformationHistory: List<CharacterDtos.TransformationHistory>,
    nickname: String?,
    speciesName: String?,
    contentPadding: PaddingValues,
    speechBubbleText: String? = null,
    onClickCharacter: () -> Unit = {},
    onLongClickCharacter: () -> Unit = {},
    onFavoriteSwipe: (Int) -> Unit = {},
    favoriteTransitionDirection: Int = 1,
    favoriteIndex: Int = -1,
    favoriteCount: Int = 0,
    onClickTransformation: (CharacterDtos.TransformationHistory) -> Unit = {},
    vitalsHistory: List<VitalsHistory> = emptyList()
) {
    Column(
        modifier = Modifier
            .padding(top = contentPadding.calculateTopPadding())
            .verticalScroll(state = rememberScrollState())
    ) {
        // Big "Vitals X / max" readout, echoing the top gauge of the
        // reference home screen.
        VitalsHeaderStat(
            label = nickname?.takeIf { it.isNotBlank() }
                ?: speciesName?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.widget_digimon_label),
            current = activeMon.vitalPoints,
            max = MAX_VITAL_POINTS,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ActiveDigimonCard(
                activeMon = activeMon,
                multiplier = 8,
                cardIcon = cardIcon,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                speechBubbleText = speechBubbleText,
                transitionDirection = favoriteTransitionDirection,
                favoriteIndex = favoriteIndex,
                favoriteCount = favoriteCount,
                onClick = onClickCharacter,
                onLongClick = onLongClickCharacter,
                onFavoriteSwipe = onFavoriteSwipe
            )
            // Level / Attribute / Days readout beside the character portrait,
            // matching the reference screen's stat list.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoStatRow(
                    icon = R.drawable.baseline_trophy_24,
                    label = stringResource(R.string.home_vbdim_level),
                    value = shortStageName(activeMon.stage),
                    modifier = Modifier.weight(1f)
                )
                InfoStatRow(
                    icon = R.drawable.baseline_mood_24,
                    label = stringResource(R.string.home_vbdim_attribute),
                    value = activeMon.attribute.name,
                    modifier = Modifier.weight(1f)
                )
                InfoStatRow(
                    icon = R.drawable.baseline_next_24,
                    label = stringResource(R.string.home_vbdim_days),
                    value = activeMon.ageInDays.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (vitalsHistory.isNotEmpty()) {
            WeeklyVitalsChart(
                history = vitalsHistory,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ItemDisplay(
                icon = R.drawable.baseline_mood_24,
                textValue = activeMon.mood.toString(),
                definition = stringResource(R.string.home_vbdim_mood),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
            ItemDisplay(
                icon = R.drawable.baseline_trophy_24,
                textValue = activeMon.trophies.toString(),
                definition = stringResource(R.string.home_vbdim_trophies),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
            val transformationCountdownInHours = activeMon.transformationCountdown / 60
            ItemDisplay(
                icon = R.drawable.baseline_next_24,
                textValue = when (transformationCountdownInHours) {
                    0 -> "${activeMon.transformationCountdown} m"
                    else -> "$transformationCountdownInHours h"
                },
                definition = stringResource(R.string.home_vbdim_next_timer),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            ItemDisplay(
                icon = R.drawable.baseline_swords_24,
                textValue = calculateWinPercent(
                    activeMon.totalBattlesWon,
                    activeMon.totalBattlesLost
                ),
                definition = stringResource(R.string.home_vbdim_total_battle_win),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
            ItemDisplay(
                icon = R.drawable.baseline_swords_24,
                textValue = calculateWinPercent(
                    activeMon.currentPhaseBattlesWon,
                    activeMon.currentPhaseBattlesLost
                ),
                definition = stringResource(R.string.home_vbdim_current_phase_win),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            TransformationHistoryCard(
                transformationHistory = transformationHistory,
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                onClickTransformation = onClickTransformation
            )
        }

        // BE-only device status: rank, remaining training time and the held
        // item, none of which exist on a VB bracelet.
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ItemDisplay(
                icon = R.drawable.baseline_rank_24,
                textValue = beData.rank.toString(),
                definition = stringResource(R.string.home_be_rank),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
            val timeInHours = beData.remainingTrainingTimeInMinutes / 60
            ItemDisplay(
                icon = R.drawable.baseline_timer_24,
                textValue = "$timeInHours h",
                definition = stringResource(R.string.home_be_training_limit),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
            if (beData.itemRemainingTime != 0) {
                ItemDisplay(
                    icon = getIconResource(beData.itemType),
                    textValue = "${beData.itemRemainingTime} m",
                    definition = itemDefinition(beData.itemType),
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(8.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ItemDisplay(
                icon = R.drawable.baseline_health_24,
                textValue = "+${beData.trainingHp}",
                definition = stringResource(R.string.home_be_training_hp),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
            ItemDisplay(
                icon = R.drawable.baseline_agility_24,
                textValue = "+${beData.trainingBp}",
                definition = stringResource(R.string.home_be_training_bp),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
            ItemDisplay(
                icon = R.drawable.baseline_attack_24,
                textValue = "+${beData.trainingAp}",
                definition = stringResource(R.string.home_be_training_ap),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun calculateWinPercent(won: Int, lost: Int): String {
    val total = won + lost
    if (total == 0) return "0.00 %"
    val percentage = won.toFloat() / total.toFloat()
    return String.format(LocalConfiguration.current.locales[0], "%.2f", percentage * 100) + " %"
}

private fun itemDefinition(itemId: Int): String = when (itemId) {
    ItemsScreenControllerImpl.ItemTypes.PPTraining.id -> "PP Training"
    ItemsScreenControllerImpl.ItemTypes.HPTraining.id -> "HP Training"
    ItemsScreenControllerImpl.ItemTypes.APTraining.id -> "AP Training"
    ItemsScreenControllerImpl.ItemTypes.BPTraining.id -> "BP Training"
    ItemsScreenControllerImpl.ItemTypes.AllTraining.id -> "All Training"
    else -> ""
}
