package com.github.nacabaro.vbhelper.screens.homeScreens.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.utils.BitmapData

/**
 * Home for a BE Memory (BEM) character. It shares [BEHomeScreen] with the
 * DiM-on-BE path so a BEM BEM reads like a DiM on the home screen; the BE
 * status block supplied by [beData] is what makes it a BEM.
 */
@Composable
fun BEBEmHomeScreen(
    activeMon: CharacterDtos.CharacterWithSprites,
    beData: BECharacterData,
    cardIcon: BitmapData,
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
    BEHomeScreen(
        activeMon = activeMon,
        cardIcon = cardIcon,
        beData = beData,
        transformationHistory = transformationHistory,
        nickname = nickname,
        speciesName = speciesName,
        contentPadding = contentPadding,
        speechBubbleText = speechBubbleText,
        onClickCharacter = onClickCharacter,
        onLongClickCharacter = onLongClickCharacter,
        onFavoriteSwipe = onFavoriteSwipe,
        favoriteTransitionDirection = favoriteTransitionDirection,
        favoriteIndex = favoriteIndex,
        favoriteCount = favoriteCount,
        onClickTransformation = onClickTransformation,
        vitalsHistory = vitalsHistory
    )
}
