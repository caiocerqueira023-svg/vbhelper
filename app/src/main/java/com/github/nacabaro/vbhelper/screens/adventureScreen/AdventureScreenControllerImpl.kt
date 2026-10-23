package com.github.nacabaro.vbhelper.screens.adventureScreen

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.components.showAppFeedback
import com.github.nacabaro.vbhelper.species.PartnerUnavailableException
import com.github.nacabaro.vbhelper.R

class AdventureScreenControllerImpl(
    private val componentActivity: ComponentActivity,
) : AdventureScreenController {
    private val application = componentActivity.applicationContext as VBHelper
    private val database = application.container.db

    override fun sendCharacterToAdventure(characterId: Long, timeInMinutes: Long) {
        val finishesAdventureAt = timeInMinutes * 60
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            try {
            database.withTransaction {
            database.participation?.snapshot()?.requireOwned(characterId)
            val characterData = database
                .userCharacterDao()
                .getCharacter(characterId)

            if (characterData.isActive) {
                database
                    .userCharacterDao()
                    .clearActiveCharacter()
            }

            database
                .adventureDao()
                .insertNewAdventure(characterId, timeInMinutes, finishesAdventureAt)
            }

            runCatching {
                application.container.chatRepository.triggerReaction(
                    characterId,
                    PromptLocalization.adventureStarted(
                        PromptLocalization.currentLanguageTag(),
                        timeInMinutes
                    )
                )
            }.onFailure { /* A aventura não deve falhar por indisponibilidade do LLM. */ }
            } catch (_: PartnerUnavailableException) {
                componentActivity.runOnUiThread { componentActivity.showAppFeedback(R.string.participation_inactive_hint) }
            }
        }
    }

    override fun getItemFromAdventure(
        characterId: Long,
        onResult: (ItemDtos.PurchasedItem, Int) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            application.container.currencyRepository.initializeWallet()
            try {
            val reward = database.withTransaction {
            database.participation?.reconcileLocked()
            database.participation?.snapshot()?.requireOwned(characterId)
            if (database.adventureDao().getAdventureCharacters().first().none {
                it.id == characterId && it.finishesAdventure <= java.time.Instant.now().epochSecond
            }) return@withTransaction null
            database
                .adventureDao()
                .deleteAdventure(characterId)

            val generatedCurrency = generateRandomCurrency()

            val generatedItem = generateItem(characterId)
            generatedItem to generatedCurrency
            }
            val (generatedItem, generatedCurrency) = reward ?: return@launch

            runCatching {
                application.container.chatRepository.triggerReaction(
                    characterId,
                    PromptLocalization.adventureReturned(
                        PromptLocalization.currentLanguageTag(),
                        generatedItem.itemName,
                        generatedCurrency
                    )
                )
            }.onFailure { /* A recompensa continua válida mesmo sem resposta do LLM. */ }

            onResult(generatedItem, generatedCurrency)
            } catch (_: PartnerUnavailableException) {
                componentActivity.runOnUiThread { componentActivity.showAppFeedback(R.string.participation_inactive_hint) }
            }
        }
    }

    private suspend fun generateRandomCurrency(): Int {
        val random = (2..6).random() * 1000
        application.container.currencyRepository.credit(random)

        return random
    }

    override fun cancelAdventure(characterId: Long, onResult: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            database
                .adventureDao()
                .deleteAdventure(characterId)

            componentActivity
                .runOnUiThread {
                    Toast.makeText(
                        componentActivity,
                        "Adventure canceled",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult()
                }

        }
    }

    private suspend fun generateItem(characterId: Long): ItemDtos.PurchasedItem {
        val character = database
            .userCharacterDao()
            .getCharacterInfo(characterId)

        val randomItem = database
            .itemDao()
            .getAllItems()
            .first()
            .random()

        val random = ((Random.nextFloat() * character.stage) + 3).roundToInt()

        database
            .itemDao()
            .purchaseItem(
                itemId = randomItem.id,
                itemAmount = random
            )

        return ItemDtos.PurchasedItem(
            itemId = randomItem.id,
            itemAmount = random,
            itemName = randomItem.name,
            itemIcon = randomItem.itemIcon,
            itemLength = randomItem.itemLength,
            itemDescription = randomItem.description,
            itemType = randomItem.itemType
        )
    }
}
