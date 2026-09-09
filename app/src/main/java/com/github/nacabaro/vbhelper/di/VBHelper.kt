package com.github.nacabaro.vbhelper.di

import DefaultAppContainer
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.github.nacabaro.vbhelper.companion.validation.ValidatedCardManager
import com.github.nacabaro.vbhelper.companion.logs.CompanionLogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.github.nacabaro.vbhelper.world.WorldAfkScheduler

class VBHelper : Application() {
    lateinit var container: DefaultAppContainer
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        WorldAfkScheduler.schedule(applicationContext)
    }
}