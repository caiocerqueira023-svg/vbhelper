package com.github.nacabaro.vbhelper.screens.homeScreens.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ItemDisplay
import com.github.nacabaro.vbhelper.components.TransformationHistoryCard
import com.github.nacabaro.vbhelper.components.WeeklyVitalsChart
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerGlanceRow
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerIdentityBlock
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerTrainingGainsRow
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerVitalsHeader
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerWinRateRow
import com.github.nacabaro.vbhelper.screens.itemsScreen.ItemsScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.itemsScreen.getIconResource
import com.github.nacabaro.vbhelper.utils.BitmapData

/**
 * Home layout for a BE device, whether the character came from a DiM or a
 * BE Memory card. It mirrors [VBDiMHomeScreen] so every DiM/BEM character
 * reads the same way, while keeping the BE-only status (rank, training
 * limit, held item and the training gains) that a VB bracelet does not have.
 *
 * Shared blocks come from `PartnerModules.kt`; only the BE status rows stay here.
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

        Row(Modifier.fillMaxWidth()) {
            TransformationHistoryCard(
                transformationHistory = transformationHistory,
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                onClickTransformation = onClickTransformation,
            )
        }

        // BE-only device status: rank, remaining training time and the held item, none of which
        // exist on a VB bracelet.
        Row(Modifier.fillMaxWidth()) {
            val tile = Modifier
                .weight(1f)
                .heightIn(min = 104.dp)
                .padding(8.dp)
            ItemDisplay(
                icon = R.drawable.baseline_rank_24,
                textValue = beData.rank.toString(),
                definition = stringResource(R.string.home_be_rank),
                modifier = tile,
            )
            val timeInHours = beData.remainingTrainingTimeInMinutes / 60
            ItemDisplay(
                icon = R.drawable.baseline_timer_24,
                textValue = "$timeInHours h",
                definition = stringResource(R.string.home_be_training_limit),
                modifier = tile,
            )
            if (beData.itemRemainingTime != 0) {
                ItemDisplay(
                    icon = getIconResource(beData.itemType),
                    textValue = "${beData.itemRemainingTime} m",
                    definition = itemDefinition(beData.itemType),
                    modifier = tile,
                )
            }
        }

        PartnerTrainingGainsRow(
            trainingHp = beData.trainingHp,
            trainingBp = beData.trainingBp,
            trainingAp = beData.trainingAp,
        )
    }
}

@Composable
private fun itemDefinition(itemId: Int): String = when (itemId) {
    ItemsScreenControllerImpl.ItemTypes.PPTraining.id -> stringResource(R.string.immersion_pp_training)
    ItemsScreenControllerImpl.ItemTypes.HPTraining.id -> stringResource(R.string.home_be_training_hp)
    ItemsScreenControllerImpl.ItemTypes.APTraining.id -> stringResource(R.string.home_be_training_ap)
    ItemsScreenControllerImpl.ItemTypes.BPTraining.id -> stringResource(R.string.home_be_training_bp)
    ItemsScreenControllerImpl.ItemTypes.AllTraining.id -> stringResource(R.string.immersion_all_training)
    else -> ""
}
