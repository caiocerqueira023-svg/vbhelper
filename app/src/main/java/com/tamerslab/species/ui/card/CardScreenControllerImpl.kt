package com.tamerslab.species.ui.card

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.domain.card.OfficialStatus
import com.tamerslab.dtos.CardDtos
import com.tamerslab.dtos.CharacterDtos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardScreenControllerImpl(
    private val componentActivity: ComponentActivity,
) : CardScreenController {
    private val cards = com.tamerslab.di.AppServices.cardRepository
    private val speciesRepository = com.tamerslab.di.AppServices.speciesRepository

    override fun renameCard(cardId: Long, newName: String, onRenamed: (String) -> Unit) {
        componentActivity.lifecycleScope.launch {
            cards.rename(cardId, newName)
            onRenamed(newName)
        }
    }

    override fun deleteCard(cardId: Long, onDeleted: () -> Unit) {
        componentActivity.lifecycleScope.launch {
            cards.delete(cardId)
            onDeleted()
        }
    }

    override fun getCardAdventureMissions(cardId: Long): Flow<List<CardDtos.CardAdventureWithSprites>> =
        cards.adventures(cardId)

    override fun getCardProgress(cardId: Long): Flow<Int> = cards.progress(cardId)

    override fun getFusionsForCharacters(characterId: Long): Flow<List<CharacterDtos.FusionsWithSpritesAndObtained>> =
        cards.fusions(characterId)

    override fun setCardOfficialStatus(cardId: Long, status: OfficialStatus, onComplete: (Int) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            cards.setOfficialStatus(cardId, status)
            val matchedCount = if (status != OfficialStatus.UNKNOWN) {
                speciesRepository.matchSpeciesForCard(cardId)
            } else {
                0
            }
            withContext(Dispatchers.Main) { onComplete(matchedCount) }
        }
    }

    override fun retrySpeciesMatch(cardId: Long, onComplete: (Int, String?) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { speciesRepository.matchSpeciesForCard(cardId) }
            withContext(Dispatchers.Main) {
                onComplete(result.getOrDefault(0), result.exceptionOrNull()?.message)
            }
        }
    }
}
