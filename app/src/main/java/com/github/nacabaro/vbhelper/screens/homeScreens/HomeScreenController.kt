package com.github.nacabaro.vbhelper.screens.homeScreens

import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.dtos.CharacterDtos

interface HomeScreenController {
    fun didAdventureMissionsFinish(onCompletion: (Boolean) -> Unit)
    fun clearSpecialMission(missionId: Long, onCleared: (ItemDtos.PurchasedItem?, Int?) -> Unit)
    fun checkDailyDiary(characterId: Long)
    fun degenerate(
        characterId: Long,
        transformation: CharacterDtos.TransformationHistory,
        onResult: (Result<Unit>) -> Unit
    )
}