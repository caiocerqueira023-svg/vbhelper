package com.github.nacabaro.vbhelper.screens.homeScreens.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.components.InfoStatRow
import com.github.nacabaro.vbhelper.components.ItemDisplay
import com.github.nacabaro.vbhelper.components.SpecialMissionsEntry
import com.github.nacabaro.vbhelper.components.TransformationHistoryCard
import com.github.nacabaro.vbhelper.components.NicknameDisplay
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

// The firmware-side vitals counter is capped at this value across the app
// (see ItemsScreenControllerImpl), so we reuse it as the gauge's max.
private const val MAX_VITAL_POINTS = 9999

private fun shortStageName(stage: Int): String = when (stage) {
    0 -> "Baby I"
    1 -> "Baby II"
    2 -> "Child"
    3 -> "Adult"
    4 -> "Perfect"
    5 -> "Ultimate"
    else -> "Stage $stage"
}

@Composable
fun VBDiMHomeScreen(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    vbData: VBCharacterData,
    specialMissions: List<SpecialMissions>,
    homeScreenController: HomeScreenControllerImpl,
    transformationHistory: List<CharacterDtos.TransformationHistory>,
    nickname: String?,
    contentPadding: PaddingValues,
    onClickCollect: (ItemDtos.PurchasedItem?, Int?) -> Unit,
    speechBubbleText: String? = null,
    onClickCharacter: () -> Unit = {},
    onClickTransformation: (CharacterDtos.TransformationHistory) -> Unit = {},
    vitalsHistory: List<VitalsHistory> = emptyList()
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
            label = stringResource(R.string.home_vbdim_vitals),
            current = activeMon.vitalPoints,
            max = MAX_VITAL_POINTS,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            CharacterEntry(
                icon = BitmapData(
                    bitmap = activeMon.spriteIdle,
                    width = activeMon.spriteWidth,
                    height = activeMon.spriteHeight
                ),
                cardIcon = cardIcon,
                multiplier = 8,
                idleFrame2 = BitmapData(
                    bitmap = activeMon.spriteIdle2,
                    width = activeMon.spriteWidth,
                    height = activeMon.spriteHeight
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                speechBubbleText = speechBubbleText,
                vitalPoints = activeMon.vitalPoints,
                onClick = onClickCharacter
            )
            // Level / Attribute / Days readout beside the character
            // portrait, matching the reference screen's stat list.
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
        NicknameDisplay(nickname)

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
                textValue = when {
                    activeMon.totalBattlesLost == 0 -> "0.00 %"
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
                    .aspectRatio(1f)
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
