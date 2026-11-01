package com.tamerslab.home.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.ItemDtos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeScreenControllerImpl(
    private val componentActivity: ComponentActivity,
): HomeScreenController {
    private val gameplay = com.tamerslab.di.AppServices.gameplayRepository
    internal var cachedHomeCollection: List<CharacterDtos.CharacterWithSprites>? = null
    internal var cachedHomeCards: List<com.tamerslab.dtos.CardDtos.CardProgress>? = null
    internal var cachedPartnerDetails: HomePartnerDetails? = null
    val shareExport by lazy {
        ViewModelProvider(componentActivity, viewModelFactory {
            initializer {
                VitalWearShareViewModel { id ->
                    com.tamerslab.di.AppServices.vitalWearExporter(componentActivity).buildShareIntent(id)
                }
            }
        })[VitalWearShareViewModel::class.java].export
    }

    override fun didAdventureMissionsFinish(onCompletion: (Boolean) -> Unit) {
        componentActivity.lifecycleScope.launch {
            onCompletion(gameplay.hasFinishedAdventures())
        }
    }

    override fun clearSpecialMission(missionId: Long, onCleared: (ItemDtos.PurchasedItem?, Int?) -> Unit) {
        componentActivity.lifecycleScope.launch {
            val reward = gameplay.clearSpecialMission(missionId)
            onCleared(reward.item, reward.credits)
        }
    }

    override fun checkDailyDiary(characterId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            com.tamerslab.di.AppServices.diaryService.checkAndGenerateEntry(characterId)
        }
    }

    override fun degenerate(
        characterId: Long,
        transformation: CharacterDtos.TransformationHistory,
        onResult: (Result<Unit>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                com.tamerslab.di.AppServices.reactionRepository
                    .snapshotBeforeSendingToWatch(characterId)
                gameplay.degenerate(characterId, transformation)
                com.tamerslab.di.AppServices.reactionRepository.evaluateAndReact(characterId)
            }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }
}
