package com.github.nacabaro.vbhelper.screens.homeScreens.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ActiveDigimonCard
import com.github.nacabaro.vbhelper.components.InfoStatRow
import com.github.nacabaro.vbhelper.components.PartnerStatColumn
import com.github.nacabaro.vbhelper.components.ItemDisplay
import com.github.nacabaro.vbhelper.components.SpecialMissionsEntry
import com.github.nacabaro.vbhelper.components.TransformationHistoryCard
import com.github.nacabaro.vbhelper.components.VitalsHeaderStat
import com.github.nacabaro.vbhelper.components.WeeklyVitalsChart
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.screens.homeScreens.HomeScreenControllerImpl
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.utils.BitmapData
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.screens.homeScreens.dialogs.DeleteSpecialMissionDialog

@Composable
fun VBDiMHomeScreen(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    vbData: VBCharacterData?,
    specialMissions: List<SpecialMissions>,
    homeScreenController: HomeScreenControllerImpl,
    transformationHistory: List<CharacterDtos.TransformationHistory>,
    nickname: String?,
    speciesName: String?,
    contentPadding: PaddingValues,
    onClickCollect: (ItemDtos.PurchasedItem?, Int?) -> Unit,
    speechBubbleText: String? = null,
    onClickCharacter: () -> Unit = {},
    onLongClickCharacter: () -> Unit = {},
    onFavoriteSwipe: (Int) -> Unit = {},
    favoriteTransitionDirection: Int = 1,
    favoriteIndex: Int = -1,
    favoriteCount: Int = 0,
    onClickTransformation: (CharacterDtos.TransformationHistory) -> Unit = {},
    vitalsHistory: List<VitalsHistory> = emptyList(),
    appData: com.github.nacabaro.vbhelper.daos.AppHomeStats? = null,
) {
    var selectedSpecialMissionId by remember { mutableStateOf<Long>(-1) }

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
                cardIcon = cardIcon,
                multiplier = 8,
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
            // Level / Attribute / Days readout beside the character
            // portrait, matching the reference screen's stat list.
            PartnerStatColumn(shortStageName(activeMon.stage), activeMon.attribute.name, activeMon.ageInDays.toString(), Modifier.weight(1f))
        }
        if (vitalsHistory.isNotEmpty()) {
            WeeklyVitalsChart(
                history = vitalsHistory,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        Row (
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ItemDisplay(
                icon = R.drawable.baseline_mood_24,
                textValue = com.github.nacabaro.vbhelper.components.moodSummary(activeMon.mood),
                definition = stringResource(R.string.home_vbdim_mood),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 104.dp)
                    .padding(8.dp)
            )
            ItemDisplay(
                icon = R.drawable.baseline_trophy_24,
                textValue = activeMon.trophies.toString(),
                definition = stringResource(R.string.home_vbdim_trophies),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 104.dp)
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
                    .heightIn(min = 104.dp)
                    .padding(8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            ItemDisplay(
                icon = R.drawable.baseline_swords_24,
                textValue = when {
                    activeMon.totalBattlesWon + activeMon.totalBattlesLost == 0 -> "0.00 %"
                    else -> {
                        val battleWinPercentage =
                            activeMon.totalBattlesWon.toFloat() / (activeMon.totalBattlesWon + activeMon.totalBattlesLost).toFloat()
                        String.format(
                            LocalConfiguration.current.locales[0],
                            "%.2f",
                            battleWinPercentage * 100
                        ) + " %" // Specify locale
                    }
                },
                definition = stringResource(R.string.home_vbdim_total_battle_win),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 104.dp)
                    .padding(8.dp)
            )
            ItemDisplay(
                icon = R.drawable.baseline_swords_24,
                textValue = when {
                    activeMon.currentPhaseBattlesWon + activeMon.currentPhaseBattlesLost == 0 -> "0.00 %"
                    else -> {
                        val battleWinPercentage =
                            activeMon.currentPhaseBattlesWon.toFloat() / (activeMon.currentPhaseBattlesWon + activeMon.currentPhaseBattlesLost).toFloat()
                        String.format(
                            LocalConfiguration.current.locales[0],
                            "%.2f",
                            battleWinPercentage * 100
                        ) + " %"
                    }
                },
                definition = stringResource(R.string.home_vbdim_current_phase_win),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 104.dp)
                    .padding(8.dp)
            )
        }
        appData?.let { state ->
            Row(Modifier.fillMaxWidth()) {
                ItemDisplay(R.drawable.baseline_health_24, "+${state.trainingHp}",
                    definition = stringResource(R.string.home_be_training_hp), modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp))
                ItemDisplay(R.drawable.baseline_agility_24, "+${state.trainingBp}",
                    definition = stringResource(R.string.home_be_training_bp), modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp))
                ItemDisplay(R.drawable.baseline_attack_24, "+${state.trainingAp}",
                    definition = stringResource(R.string.home_be_training_ap), modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                ItemDisplay(R.drawable.baseline_rank_24, state.progress.rank.toString(), definition = stringResource(R.string.home_be_rank),
                    modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp))
                ItemDisplay(R.drawable.baseline_timer_24, "${state.progress.remainingTrainingTimeInMinutes / 60} h", definition = stringResource(R.string.home_be_training_limit),
                    modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp))
                if (state.progress.itemRemainingTime > 0) ItemDisplay(com.github.nacabaro.vbhelper.screens.itemsScreen.getIconResource(state.progress.itemType),
                    "${state.progress.itemRemainingTime} m", definition = stringResource(R.string.home_app_held_item),
                    modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp))
            }
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
        Row (
            modifier = Modifier
                .padding(8.dp)
        ) {
            Text(
                text = stringResource(R.string.home_vbdim_special_missions),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryOnDark
                )
        }
        for (mission in specialMissions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                SpecialMissionsEntry(
                    specialMission = mission,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    onClickMission = { missionId ->
                        selectedSpecialMissionId = missionId
                    },
                    onClickCollect = { missionId ->
                        homeScreenController
                            .clearSpecialMission(missionId, onClickCollect)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (selectedSpecialMissionId.toInt() != -1) {
        DeleteSpecialMissionDialog(
            onClickDismiss = {
                selectedSpecialMissionId = -1
            },
            onClickDelete = {
                homeScreenController
                    .clearSpecialMission(selectedSpecialMissionId, onClickCollect)
                selectedSpecialMissionId = -1
            }
        )
    }
}
