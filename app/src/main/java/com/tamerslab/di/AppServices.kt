package com.tamerslab.di

import org.koin.core.context.GlobalContext

/** Koin-backed feature access for state/controller layers that still need a service. */
object AppServices {
    private val container: DefaultAppContainer
        get() = GlobalContext.get().get()

    private val db get() = container.db
    val cardRepository get() = container.cardRepository
    val chatRepository get() = container.chatRepository
    val currencyRepository get() = container.currencyRepository
    val gameplayRepository get() = container.gameplayRepository
    val dataStoreSecretsRepository get() = container.dataStoreSecretsRepository
    val debugSettingsRepository get() = container.debugSettingsRepository
    val diaryService get() = container.diaryService
    val gameImportAcquisition get() = container.gameImportAcquisition
    val llmSettingsRepository get() = container.llmSettingsRepository
    val lorebookRepository get() = container.lorebookRepository
    val questRepository get() = container.questRepository
    val reactionRepository get() = container.reactionRepository
    val speciesRepository get() = container.speciesRepository
    val speciesSettingsRepository get() = container.speciesSettingsRepository
    val spritePreferenceRepository get() = container.spritePreferenceRepository
    val spriteRepository get() = container.spriteRepository
    val storageRepository get() = container.storageRepository
    val worldInteractionOrchestrator get() = container.worldInteractionOrchestrator
    val ownedDigilineRepository get() = container.ownedDigilineRepository
    val digilineGateway get() = container.digilineGateway
    val worldRepository get() = container.worldRepository
    val arenaRosterGateway get() = GlobalContext.get().get<com.tamerslab.domain.repository.ArenaRosterGateway>()
    val battleUseCase get() = GlobalContext.get().get<com.tamerslab.domain.usecase.BattleUseCase>()

    fun vitalWearExporter(context: android.content.Context) =
        com.tamerslab.transfer.data.VitalWearCharacterExporter(context, db)

    fun cardImportController() =
        com.tamerslab.settings.ui.controllers.CardImportController(db)
}
