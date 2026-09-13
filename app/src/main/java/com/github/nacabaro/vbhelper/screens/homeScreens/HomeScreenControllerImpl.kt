package com.github.nacabaro.vbhelper.screens.homeScreens

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.github.cfogrady.vbnfc.vb.SpecialMission
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.random.Random

class HomeScreenControllerImpl(
    private val componentActivity: ComponentActivity,
): HomeScreenController {
    private val application = componentActivity.applicationContext as VBHelper
    private val database = application.container.db

    override fun didAdventureMissionsFinish(onCompletion: (Boolean) -> Unit) {
        componentActivity.lifecycleScope.launch {
            val currentTime = Instant.now().epochSecond
            val adventureCharacters = database
                .adventureDao()
                .getAdventureCharacters()
                .first()

            val finishedAdventureCharacters = adventureCharacters.filter { character ->
                character.finishesAdventure <= currentTime
            }

            onCompletion(finishedAdventureCharacters.isNotEmpty())
        }
    }

    override fun clearSpecialMission(missionId: Long, onCleared: (ItemDtos.PurchasedItem?, Int?) -> Unit) {
        componentActivity.lifecycleScope.launch {
            val missionStatus = database
                .specialMissionDao()
                .getSpecialMission(missionId)
                .first()

            database
                .specialMissionDao()
                .clearSpecialMission(missionId)

            if (missionStatus.status == SpecialMission.Status.COMPLETED) {
                val allItems = database.itemDao().getAllItems().first()
                if (allItems.isEmpty()) {
                    onCleared(null, null)
                    return@launch
                }
                val randomItem = allItems.random()

                val randomItemAmount = (Random.nextFloat() * 5).roundToInt()

                database
                    .itemDao()
                    .purchaseItem(
                        itemId = randomItem.id,
                        itemAmount = randomItemAmount
                    )

                val purchasedItem = ItemDtos.PurchasedItem(
                    itemId = randomItem.id,
                    itemName = randomItem.name,
                    itemDescription = randomItem.description,
                    itemIcon = randomItem.itemIcon,
                    itemLength = randomItem.itemLength,
                    itemAmount = randomItemAmount,
                    itemType = randomItem.itemType
                )

                val randomAmount = (2..6).random() * 1000
                val currentCurrency = application.container.currencyRepository.currencyValue.first()
                application.container.currencyRepository.setCurrencyValue(currentCurrency + randomAmount)

                onCleared(purchasedItem, randomAmount)
            } else {
                onCleared(null, null)
            }
        }
    }

    override fun checkDailyDiary(characterId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            application.container.diaryService.checkAndGenerateEntry(characterId)
        }
    }

    override fun degenerate(
        characterId: Long,
        transformation: CharacterDtos.TransformationHistory,
        onResult: (Result<Unit>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val character = database.userCharacterDao().getCharacter(characterId)
                val characterWithSprites = database.userCharacterDao().getCharacterWithSprites(characterId)
                check(character.charId != transformation.stageId) {
                    "This Digimon is already at that stage."
                }
                check(transformation.stage < characterWithSprites.stage) {
                    "Degeneration requires a strictly lower stage."
                }

                val currentCurrency = application.container.currencyRepository.currencyValue.first()
                check(currentCurrency >= DEGENERATION_COST) {
                    "Not enough bits. $DEGENERATION_COST bits are required."
                }

                application.container.reactionRepository
                    .snapshotBeforeSendingToWatch(characterId)
                database.withTransaction {
                    com.github.nacabaro.vbhelper.source.EvolutionHistoryRepository(database)
                        .repairCharacter(characterId)
                    val selected = database.userCharacterDao()
                        .getTransformationHistoryEntry(characterId, transformation.id)
                    check(selected != null && selected.stageId == transformation.stageId) {
                        "This evolution is no longer in this Digimon's history."
                    }
                    database.userCharacterDao().degenerateCharacter(
                        characterId = characterId,
                        stageId = selected.stageId
                    )
                    // Watch dates can precede recruitment dates or move backwards
                    // when its clock changes. Follow lineage order, not dates.
                    database.userCharacterDao().deleteTransformationsAfter(
                        characterId = characterId,
                        historyId = selected.id
                    )
                    com.github.nacabaro.vbhelper.source.EvolutionHistoryRepository(database)
                        .repairCharacter(characterId)
                }
                application.container.currencyRepository.setCurrencyValue(
                    currentCurrency - DEGENERATION_COST
                )
                application.container.reactionRepository.evaluateAndReact(characterId)
            }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

    private companion object {
        const val DEGENERATION_COST = 5000
    }
}
