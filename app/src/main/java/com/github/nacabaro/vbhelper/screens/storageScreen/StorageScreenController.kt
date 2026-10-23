package com.github.nacabaro.vbhelper.screens.storageScreen

interface StorageScreenController {
    fun setActive(
        characterId: Long,
        announce: Boolean = true,
        onCompletion: () -> Unit = {},
        onRejected: () -> Unit = {}
    )
    fun setFavorite(characterId: Long, isFavorite: Boolean, onCompletion: () -> Unit = {})
    fun deleteCharacter(characterId: Long, onCompletion: () -> Unit)
}
