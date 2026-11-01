package com.tamerslab.items.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.chat.PromptLocalization
import com.tamerslab.components.showAppFeedback
import com.tamerslab.species.PartnerUnavailableException
import com.tamerslab.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ItemsScreenControllerImpl(
    private val context: ComponentActivity,
) : ItemsScreenController {
    enum class ItemTypes(val id: Int) {
        PPTraining(1),
        HPTraining(2),
        APTraining(3),
        BPTraining(4),
        AllTraining(5),
        EvoTimer(6),
        LimitTimer(7),
        Vitals(8),
        Step8k(9),
        Step4k(10),
        Vitals1000(11),
        Vitals250(12),
        Battle20(13),
        Battle5(14),
        Win10(15),
        Win4(16)
    }

    private val gameplay = com.tamerslab.di.AppServices.gameplayRepository

    override fun applyItem(itemId: Long, characterId: Long, onCompletion: () -> Unit) {
        context.lifecycleScope.launch {
            try {
                val itemName = withContext(Dispatchers.IO) { gameplay.applyItem(itemId, characterId) }
                onCompletion()
                launch(Dispatchers.IO) {
                    runCatching {
                        com.tamerslab.di.AppServices.chatRepository.triggerReaction(
                            characterId,
                            PromptLocalization.itemEvent(
                                PromptLocalization.currentLanguageTag(),
                                itemName
                            )
                        )
                    }.onFailure { Timber.e(it, "Failed to generate item reaction") }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: PartnerUnavailableException) {
                context.showAppFeedback(R.string.participation_inactive_hint)
            } catch (_: Exception) {
                context.showAppFeedback(R.string.app_item_not_applied)
            }
        }
    }
}
