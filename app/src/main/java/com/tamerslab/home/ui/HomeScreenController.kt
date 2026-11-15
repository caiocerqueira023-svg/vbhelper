package com.tamerslab.home.ui

import com.tamerslab.dtos.ItemDtos
import com.tamerslab.dtos.CharacterDtos

interface HomeScreenController {
    fun didAdventureMissionsFinish(onCompletion: (Boolean) -> Unit)
    fun clearSpecialMission(missionId: Long, onCleared: (ItemDtos.PurchasedItem?, Int?) -> Unit)

    /**
     * Equips one owned special-mission item into the partner's matching mission slot, consuming the
     * item exactly like the item flow does. Reports whether it was applied.
     */
    fun assignSpecialMission(itemId: Long, characterId: Long, onResult: (Boolean) -> Unit)
    fun checkDailyDiary(characterId: Long)
    fun degenerate(
        characterId: Long,
        transformation: CharacterDtos.TransformationHistory,
        onResult: (Result<Unit>) -> Unit
    )
}