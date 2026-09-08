package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import com.github.nacabaro.vbhelper.R

class CardScreenControllerImpl(
    private val componentActivity: ComponentActivity,
) : CardScreenController {
    private val application = componentActivity.applicationContext as VBHelper
    private val database = application.container.db
    private val speciesRepository = SpeciesRepository(
        database = database,
        settingsRepository = application.container.speciesSettingsRepository
    )

    override fun renameCard(cardId: Long, newName: String, onRenamed: (String) -> Unit) {
        componentActivity.lifecycleScope.launch {
            database
                .cardDao()
                .renameCard(cardId.toInt(), newName)

            onRenamed(newName)
        }
    }

    override fun deleteCard(cardId: Long, onDeleted: () -> Unit) {
        componentActivity.lifecycleScope.launch {
            database
                .cardDao()
                .deleteCard(cardId)

            onDeleted()
        }
    }

    override fun getCardAdventureMissions(cardId: Long): Flow<List<CardDtos.CardAdventureWithSprites>> {
        return database
            .cardAdventureDao()
            .getAdventureForCard(cardId)
    }

    override fun getCardProgress(cardId: Long): Flow<Int> {
        return database
            .cardProgressDao()
            .getCardProgress(cardId)
    }

    override fun getFusionsForCharacters(characterId: Long): Flow<List<CharacterDtos.FusionsWithSpritesAndObtained>> {
        return database
            .cardFusionsDao()
            .getFusionsForCharacter(characterId)
    }

    override fun setCardOfficialStatus(cardId: Long, status: OfficialStatus, onComplete: (Int) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            database.cardDao().updateOfficialStatus(cardId, status)
            val matchedCount = if (status == OfficialStatus.OFFICIAL) {
                speciesRepository.matchOfficialSpeciesForCard(cardId)
            } else {
                0
            }
            withContext(Dispatchers.Main) { onComplete(matchedCount) }
        }
    }

    override fun retrySpeciesMatch(cardId: Long, onComplete: (Int, String?) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val card = database.cardDao().getCardById(cardId)
            if (card?.officialStatus == OfficialStatus.CUSTOM) {
                withContext(Dispatchers.Main) {
                    onComplete(
                        0,
                        componentActivity.getString(R.string.ui_custom_card_species_match_unavailable)
                    )
                }
                return@launch
            }
            val result = runCatching {
                speciesRepository.matchOfficialSpeciesForCard(cardId)
            }
            withContext(Dispatchers.Main) {
                onComplete(result.getOrDefault(0), result.exceptionOrNull()?.message)
            }
        }
    }

}
