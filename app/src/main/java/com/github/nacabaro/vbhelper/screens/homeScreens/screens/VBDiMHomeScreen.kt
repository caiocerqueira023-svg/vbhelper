package com.github.nacabaro.vbhelper.screens.homeScreens.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ItemDisplay
import com.github.nacabaro.vbhelper.components.SpecialMissionsEntry
import com.github.nacabaro.vbhelper.components.TransformationHistoryCard
import com.github.nacabaro.vbhelper.components.WeeklyVitalsChart
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.screens.homeScreens.HomeScreenController
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerGlanceRow
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerIdentityBlock
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerTrainingGainsRow
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerVitalsHeader
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerWinRateRow
import com.github.nacabaro.vbhelper.screens.homeScreens.dialogs.DeleteSpecialMissionDialog
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.utils.BitmapData

/**
 * Full partner overview for a VB device or an app-only partner.
 *
 * The device-independent blocks — vitals header, portrait with the Stage/Attribute/Days column,
 * mood/trophies/next-evolution row, win rates and training gains — come from `PartnerModules.kt`
 * so the farm partner deck draws exactly the same widgets. Only the APP status rows and the special
 * missions list live here.
 */
@Composable
fun VBDiMHomeScreen(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    vbData: VBCharacterData?,
    specialMissions: List<SpecialMissions>,
    homeScreenController: HomeScreenController,
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
        // Big "Vitals X / max" readout, echoing the top gauge of the reference home screen.
        PartnerVitalsHeader(
            label = nickname?.takeIf { it.isNotBlank() }
                ?: speciesName?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.widget_digimon_label),
            vitalPoints = activeMon.vitalPoints,
        )

        // Animated portrait beside the Stage / Attribute / Days readout.
        PartnerIdentityBlock(
            activeMon = activeMon,
            cardIcon = cardIcon,
            speechBubbleText = speechBubbleText,
            favoriteTransitionDirection = favoriteTransitionDirection,
            favoriteIndex = favoriteIndex,
            favoriteCount = favoriteCount,
            onClickCharacter = onClickCharacter,
            onLongClickCharacter = onLongClickCharacter,
            onFavoriteSwipe = onFavoriteSwipe,
        )

        if (vitalsHistory.isNotEmpty()) {
            WeeklyVitalsChart(
                history = vitalsHistory,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        PartnerGlanceRow(
            mood = activeMon.mood,
            trophies = activeMon.trophies,
            transformationCountdownMinutes = activeMon.transformationCountdown,
        )

        PartnerWinRateRow(
            totalWon = activeMon.totalBattlesWon,
            totalLost = activeMon.totalBattlesLost,
            phaseWon = activeMon.currentPhaseBattlesWon,
            phaseLost = activeMon.currentPhaseBattlesLost,
        )

        appData?.let { state ->
            PartnerTrainingGainsRow(
                trainingHp = state.trainingHp,
                trainingBp = state.trainingBp,
                trainingAp = state.trainingAp,
            )
            Row(Modifier.fillMaxWidth()) {
                ItemDisplay(
                    R.drawable.baseline_rank_24,
                    state.progress.rank.toString(),
                    definition = stringResource(R.string.home_be_rank),
                    modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp),
                )
                ItemDisplay(
                    R.drawable.baseline_timer_24,
                    "${state.progress.remainingTrainingTimeInMinutes / 60} h",
                    definition = stringResource(R.string.home_be_training_limit),
                    modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp),
                )
                if (state.progress.itemRemainingTime > 0) {
                    ItemDisplay(
                        com.github.nacabaro.vbhelper.screens.itemsScreen.getIconResource(
                            state.progress.itemType
                        ),
                        "${state.progress.itemRemainingTime} m",
                        definition = stringResource(R.string.home_app_held_item),
                        modifier = Modifier.weight(1f).heightIn(min = 104.dp).padding(8.dp),
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            TransformationHistoryCard(
                transformationHistory = transformationHistory,
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                onClickTransformation = onClickTransformation,
            )
        }

        Row(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(R.string.home_vbdim_special_missions),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryOnDark,
            )
        }
        for (mission in specialMissions) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SpecialMissionsEntry(
                    specialMission = mission,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    onClickMission = { missionId -> selectedSpecialMissionId = missionId },
                    onClickCollect = { missionId ->
                        homeScreenController.clearSpecialMission(missionId, onClickCollect)
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (selectedSpecialMissionId.toInt() != -1) {
        DeleteSpecialMissionDialog(
            onClickDismiss = { selectedSpecialMissionId = -1 },
            onClickDelete = {
                homeScreenController.clearSpecialMission(selectedSpecialMissionId, onClickCollect)
                selectedSpecialMissionId = -1
            },
        )
    }
}
