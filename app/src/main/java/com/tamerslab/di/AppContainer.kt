package com.tamerslab.di

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.items.data.CurrencyRepository
import com.tamerslab.security.DataStoreSecretsRepository
import com.tamerslab.settings.data.LlmSettingsRepository
import com.tamerslab.settings.data.SpeciesSettingsRepository
import com.tamerslab.companion.validation.ValidatedCardManager
import com.tamerslab.companion.logs.CompanionLogService
import com.tamerslab.chat.ChatRepository
import com.tamerslab.chat.DigimonDiaryService
import com.tamerslab.chat.ReactionRepository
import com.tamerslab.chat.lorebook.LorebookRepository
import com.tamerslab.world.WorldRepository
import com.tamerslab.farm.DigifarmRepository
import com.tamerslab.farm.FarmSessionCoordinator
import com.tamerslab.world.ecosystem.WorldEcosystemCoordinator

interface AppContainer {
    val db: AppDatabase
    val dataStoreSecretsRepository: DataStoreSecretsRepository
    val currencyRepository: CurrencyRepository
    val debugSettingsRepository: com.tamerslab.settings.data.DebugSettingsRepository
    val spritePreferenceRepository: com.tamerslab.settings.data.SpritePreferenceRepository
    val questRepository: com.tamerslab.quests.QuestRepository
        get() = com.tamerslab.quests.QuestRepository(db, currencyRepository,
            allowAnyDistance = { debugSettingsRepository.allowAnyDistance })
    val validatedCardManager: ValidatedCardManager
    val companionLogService: CompanionLogService
    val llmSettingsRepository: LlmSettingsRepository
    val speciesSettingsRepository: SpeciesSettingsRepository
    val speciesRepository: com.tamerslab.species.SpeciesRepository
    val dotSpriteLibrary: com.tamerslab.species.BundledDotSpriteLibrary?
        get() = null
    val chatRepository: ChatRepository
    val reactionRepository: ReactionRepository
    val diaryService: DigimonDiaryService
    val lorebookRepository: LorebookRepository
    val worldRepository: WorldRepository
    val digifarmRepository: DigifarmRepository
    val farmSessionCoordinator: FarmSessionCoordinator
    val farmConversationOrchestrator: com.tamerslab.farm.social.FarmConversationOrchestrator
        get() = digifarmRepository.conversationOwner(chatRepository)
    val worldEcosystemCoordinator: WorldEcosystemCoordinator
    val worldInteractionOrchestrator: com.tamerslab.world.ecosystem.WorldInteractionOrchestrator
    val digilineContentProvider: com.tamerslab.digiline.content.DigilineContentProvider
    val ownedDigilineRepository: com.tamerslab.digiline.data.OwnedDigilineRepository
    val digilineGateway: com.tamerslab.digiline.gateway.DigilineGateway
}
