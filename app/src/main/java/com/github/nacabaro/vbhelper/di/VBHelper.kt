package com.github.nacabaro.vbhelper.di

import DefaultAppContainer
import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.github.nacabaro.vbhelper.companion.validation.ValidatedCardManager
import com.github.nacabaro.vbhelper.companion.logs.CompanionLogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.github.nacabaro.vbhelper.source.EvolutionHistoryRepository
import com.github.nacabaro.vbhelper.world.WorldAfkScheduler
import com.github.nacabaro.vbhelper.source.StorageRepository

class VBHelper : Application() {
    lateinit var container: DefaultAppContainer
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val feedback = com.github.nacabaro.vbhelper.components.AppFeedback()

    val validatedCardManager: ValidatedCardManager
        get() = container.validatedCardManager

    val companionLogService: CompanionLogService
        get() = container.companionLogService

    override fun onCreate() {
        super.onCreate()
        val languageTag = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getString("language_tag", null)
        if (!languageTag.isNullOrBlank()) {
            AppCompatDelegate.setApplicationLocales(
                if (languageTag == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageTag)
                }
            )
        }
        container = DefaultAppContainer(applicationContext)
        applicationScope.launch {
            try {
                // Reassign legacy or missing personality rows before any chat
                // screen can build a prompt from the old system.
                StorageRepository(container.db).ensureAllPersonalities()
            } catch (failure: Exception) {
                Log.e("DigimonPersonality", "Could not migrate stored personalities", failure)
            }
            val histories = EvolutionHistoryRepository(container.db)
            // Initial emission repairs existing storage; later emissions also cover
            // imports, degeneration, restored backups and edited evolution routes.
            container.db.invalidationTracker.createFlow(
                "UserCharacter", "TransformationHistory", "CardCharacter",
                "PossibleTransformations", "CardFusions",
            ).collect {
                try {
                    histories.repairAll()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e("EvolutionHistory", "Could not repair stored evolution histories", failure)
                }
            }
        }
        WorldAfkScheduler.schedule(applicationContext)
    }
}
