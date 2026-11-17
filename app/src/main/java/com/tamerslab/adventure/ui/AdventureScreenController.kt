package com.tamerslab.adventure.ui

import com.tamerslab.dtos.ItemDtos

interface AdventureScreenController {
    fun sendCharacterToAdventure(characterId: Long, timeInMinutes: Long)
    fun getItemFromAdventure(characterId: Long, onResult: (ItemDtos.PurchasedItem, Int) -> Unit)
    fun cancelAdventure(characterId: Long, onResult: () -> Unit)
    fun withdrawAdventures(characterIds: List<Long>, onResult: () -> Unit)
}