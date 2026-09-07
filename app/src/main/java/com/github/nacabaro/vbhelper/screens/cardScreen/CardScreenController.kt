package com.github.nacabaro.vbhelper.screens.cardScreen

import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow

interface CardScreenController {
    fun renameCard(cardId: Long, newName: String, onRenamed: (String) -> Unit)
    fun deleteCard(cardId: Long, onDeleted: () -> Unit)
    fun getCardAdventureMissions(cardId: Long): Flow<List<CardDtos.CardAdventureWithSprites>>
    fun getCardProgress(cardId: Long): Flow<Int>
    fun getFusionsForCharacters(characterId: Long): Flow<List<CharacterDtos.FusionsWithSpritesAndObtained>>
    fun setCardOfficialStatus(cardId: Long, status: OfficialStatus, onComplete: (Int) -> Unit)
    fun retrySpeciesMatch(cardId: Long, onComplete: (Int, String?) -> Unit)
}
