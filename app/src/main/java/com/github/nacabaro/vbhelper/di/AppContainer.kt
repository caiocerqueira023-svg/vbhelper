package com.github.nacabaro.vbhelper.di

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.source.CurrencyRepository
import com.github.nacabaro.vbhelper.source.DataStoreSecretsRepository
import com.github.nacabaro.vbhelper.source.LlmSettingsRepository
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import com.github.nacabaro.vbhelper.companion.validation.ValidatedCardManager
import com.github.nacabaro.vbhelper.companion.logs.CompanionLogService
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.chat.DigimonDiaryService
import com.github.nacabaro.vbhelper.chat.ReactionRepository
import com.github.nacabaro.vbhelper.chat.lorebook.LorebookRepository
import com.github.nacabaro.vbhelper.world.WorldRepository

interface AppContainer {
    val db: AppDatabase
    val dataStoreSecretsRepository: DataStoreSecretsRepository
    val currencyRepository: CurrencyRepository
    val validatedCardManager: ValidatedCardManager
    val companionLogService: CompanionLogService
    val llmSettingsRepository: LlmSettingsRepository
    val speciesSettingsRepository: SpeciesSettingsRepository
    val chatRepository: ChatRepository
    val reactionRepository: ReactionRepository
    val diaryService: DigimonDiaryService
    val lorebookRepository: LorebookRepository
    val worldRepository: WorldRepository
}
