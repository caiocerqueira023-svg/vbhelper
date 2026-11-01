package com.tamerslab.adventure.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.chat.PromptLocalization
import com.tamerslab.components.showAppFeedback
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.species.PartnerUnavailableException
import com.tamerslab.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdventureScreenControllerImpl(
    private val componentActivity: ComponentActivity,
) : AdventureScreenController {
    private val gameplay = com.tamerslab.di.AppServices.gameplayRepository

    override fun sendCharacterToAdventure(characterId: Long, timeInMinutes: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                gameplay.sendToAdventure(characterId, timeInMinutes)
                runCatching {
                    com.tamerslab.di.AppServices.chatRepository.triggerReaction(
                        characterId,
                        PromptLocalization.adventureStarted(
                            PromptLocalization.currentLanguageTag(),
                            timeInMinutes
                        )
                    )
                }.onFailure { /* The adventure must not fail because the LLM is unavailable. */ }
            } catch (_: PartnerUnavailableException) {
                componentActivity.runOnUiThread {
                    componentActivity.showAppFeedback(R.string.participation_inactive_hint)
                }
            }
        }
    }

    override fun getItemFromAdventure(
        characterId: Long,
        onResult: (ItemDtos.PurchasedItem, Int) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            com.tamerslab.di.AppServices.currencyRepository.initializeWallet()
            try {
                val reward = gameplay.claimAdventure(characterId) ?: return@launch
                val (generatedItem, generatedCurrency) = reward
                runCatching {
                    com.tamerslab.di.AppServices.chatRepository.triggerReaction(
                        characterId,
                        PromptLocalization.adventureReturned(
                            PromptLocalization.currentLanguageTag(),
                            generatedItem.itemName,
                            generatedCurrency
                        )
                    )
                }.onFailure { /* The reward remains valid without an LLM response. */ }
                onResult(generatedItem, generatedCurrency)
            } catch (_: PartnerUnavailableException) {
                componentActivity.runOnUiThread {
                    componentActivity.showAppFeedback(R.string.participation_inactive_hint)
                }
            }
        }
    }

    override fun cancelAdventure(characterId: Long, onResult: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            gameplay.cancelAdventure(characterId)
            componentActivity.runOnUiThread {
                Toast.makeText(componentActivity, "Adventure canceled", Toast.LENGTH_SHORT).show()
                onResult()
            }
        }
    }
}
