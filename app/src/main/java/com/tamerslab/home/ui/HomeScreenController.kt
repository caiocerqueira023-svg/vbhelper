package com.tamerslab.home.ui

import com.tamerslab.dtos.ItemDtos
import com.tamerslab.dtos.CharacterDtos

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