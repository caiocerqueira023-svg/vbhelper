package com.tamerslab.storage.ui

interface StorageScreenController {
    /**
     * Makes [characterId] the active partner.
     *
     * The farm shows the active partner plus favourites, so this also keeps the active partner
     * favourited, and - unless [keepPreviousInFarm] is false - keeps the partner it replaces on the
     * island by favouriting it too. That is what stops a leader from vanishing when it steps down;
     * only an explicit swap passes false, because there the outgoing partner is meant to leave.
     */
    fun setActive(
        characterId: Long,
        announce: Boolean = true,
        onCompletion: () -> Unit = {},
        onRejected: () -> Unit = {}
    )
    fun setFavorite(characterId: Long, isFavorite: Boolean, onCompletion: () -> Unit = {})
    fun deleteCharacter(characterId: Long, onCompletion: () -> Unit)
}
