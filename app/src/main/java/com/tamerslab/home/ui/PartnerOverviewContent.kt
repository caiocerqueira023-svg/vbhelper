package com.tamerslab.home.ui

import androidx.compose.runtime.Composable
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.home.ui.screens.BEBEmHomeScreen
import com.tamerslab.home.ui.screens.BEDiMHomeScreen
import com.tamerslab.home.ui.screens.VBDiMHomeScreen
import com.tamerslab.utils.DeviceType
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Chooses the device-specific expanded overview.
 *
 * This is the branch that used to live inline in `HomeScreen.kt`: an app-only partner reads the
 * DiM layout with the app payload substituted, a BE Memory partner reads the BEM layout, a DiM
 * partner on a BE device reads the BE layout, and everything else falls back to the VB layout.
 * Both hosts share it so the two surfaces can never disagree about which renderer a partner gets.
 */
@Composable
fun PartnerOverviewContent(
    state: PartnerOverviewState,
    homeScreenController: HomeScreenController,
    onClickCollect: (ItemDtos.PurchasedItem?, Int?) -> Unit,
    onClickTransformation: (CharacterDtos.TransformationHistory) -> Unit,
    onClickCharacter: () -> Unit,
    onLongClickCharacter: () -> Unit,
    onFavoriteSwipe: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val activeMon = state.activeMon ?: return
    val cardIcon = state.cardIcon ?: return
    val specialMissions: List<SpecialMissions> = state.specialMissions

    when {
        activeMon.characterType == DeviceType.APP -> {
            VBDiMHomeScreen(
                activeMon = state.appData?.let {
                    activeMon.copy(stage = it.stage, attribute = it.attribute, isBemCard = it.assignedBem)
                } ?: activeMon,
                vbData = null,
                appData = state.appData,
                specialMissions = specialMissions,
                homeScreenController = homeScreenController,
                onClickCollect = onClickCollect,
                transformationHistory = state.transformationHistory,
                nickname = state.nickname,
                speciesName = state.speciesName,
                contentPadding = contentPadding,
                cardIcon = cardIcon,
                speechBubbleText = state.speechBubbleText,
                onClickCharacter = onClickCharacter,
                onLongClickCharacter = onLongClickCharacter,
                onFavoriteSwipe = onFavoriteSwipe,
                favoriteTransitionDirection = state.favoriteTransitionDirection,
                favoriteIndex = state.favoriteIndex,
                favoriteCount = state.favoriteCharacters.size,
                vitalsHistory = state.vitalsHistory,
                onClickTransformation = onClickTransformation,
            )
        }

        activeMon.isBemCard && state.beData != null -> {
            BEBEmHomeScreen(
                activeMon = activeMon,
                beData = state.beData,
                transformationHistory = state.transformationHistory,
                nickname = state.nickname,
                speciesName = state.speciesName,
                contentPadding = contentPadding,
                cardIcon = cardIcon,
                speechBubbleText = state.speechBubbleText,
                onClickCharacter = onClickCharacter,
                onLongClickCharacter = onLongClickCharacter,
                onFavoriteSwipe = onFavoriteSwipe,
                favoriteTransitionDirection = state.favoriteTransitionDirection,
                favoriteIndex = state.favoriteIndex,
                favoriteCount = state.favoriteCharacters.size,
                onClickTransformation = onClickTransformation,
                vitalsHistory = state.vitalsHistory,
            )
        }

        !activeMon.isBemCard && activeMon.characterType == DeviceType.BEDevice && state.beData != null -> {
            BEDiMHomeScreen(
                activeMon = activeMon,
                beData = state.beData,
                transformationHistory = state.transformationHistory,
                nickname = state.nickname,
                speciesName = state.speciesName,
                contentPadding = contentPadding,
                cardIcon = cardIcon,
                speechBubbleText = state.speechBubbleText,
                onClickCharacter = onClickCharacter,
                onLongClickCharacter = onLongClickCharacter,
                onFavoriteSwipe = onFavoriteSwipe,
                favoriteTransitionDirection = state.favoriteTransitionDirection,
                favoriteIndex = state.favoriteIndex,
                favoriteCount = state.favoriteCharacters.size,
                onClickTransformation = onClickTransformation,
                vitalsHistory = state.vitalsHistory,
            )
        }

        state.vbData != null -> {
            VBDiMHomeScreen(
                activeMon = activeMon,
                vbData = state.vbData,
                transformationHistory = state.transformationHistory,
                nickname = state.nickname,
                speciesName = state.speciesName,
                contentPadding = contentPadding,
                specialMissions = specialMissions,
                homeScreenController = homeScreenController,
                onClickCollect = onClickCollect,
                cardIcon = cardIcon,
                speechBubbleText = state.speechBubbleText,
                onClickCharacter = onClickCharacter,
                onLongClickCharacter = onLongClickCharacter,
                onFavoriteSwipe = onFavoriteSwipe,
                favoriteTransitionDirection = state.favoriteTransitionDirection,
                favoriteIndex = state.favoriteIndex,
                favoriteCount = state.favoriteCharacters.size,
                onClickTransformation = onClickTransformation,
                vitalsHistory = state.vitalsHistory,
            )
        }
    }
}
