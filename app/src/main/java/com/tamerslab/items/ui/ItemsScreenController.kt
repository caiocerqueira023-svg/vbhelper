package com.tamerslab.items.ui


interface ItemsScreenController {
    fun applyItem(itemId: Long, characterId: Long, onCompletion: () -> Unit)
}