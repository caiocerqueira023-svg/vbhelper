package com.tamerslab.settings.ui

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.card.OfficialStatus
import com.tamerslab.settings.ui.controllers.CardImportViewModel
import com.tamerslab.settings.ui.controllers.DatabaseManagementController
import com.tamerslab.security.ApkSecretsImporter
import com.tamerslab.settings.data.LlmSettingsRepository
import com.tamerslab.settings.data.SpeciesSettingsRepository
import com.tamerslab.settings.data.PendingCardOriginPrompt
import com.tamerslab.security.SecretsImporter
import com.tamerslab.security.SecretsRepository
import com.tamerslab.security.proto.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tamerslab.R
import com.tamerslab.chat.ChatApiProvider
import com.tamerslab.settings.data.LlmProviderSettings
import com.tamerslab.ui.theme.AppFont
import com.tamerslab.ui.theme.AppTheme
import com.tamerslab.settings.data.AppThemeSettings
import com.tamerslab.security.isMissingSecrets
import com.tamerslab.components.showAppFeedback

class SettingsScreenControllerImpl(
    private val context: ComponentActivity,
): SettingsScreenController {
    private val restoredDialogs = context.savedStateRegistry.consumeRestoredStateForKey("vbhelper.settings_dialogs")
    private val filePickerLauncher: ActivityResultLauncher<String>
    private val filePickerOpenerLauncher: ActivityResultLauncher<Array<String>>
    private val filePickerApk: ActivityResultLauncher<Array<String>>
    private val filePickerCard: ActivityResultLauncher<Array<String>>
    private val secretsImporter: SecretsImporter = ApkSecretsImporter()
    private val application = context.applicationContext as TamersLabApplication
    private val languagePreferences = context.getSharedPreferences("app_preferences", 0)
    private val debugSettingsRepository = com.tamerslab.di.AppServices.debugSettingsRepository
    val debugSettings = debugSettingsRepository.state
    fun setDebugEnabled(enabled: Boolean) = debugSettingsRepository.setEnabled(enabled)
    fun setDebugInstantScan(enabled: Boolean) = debugSettingsRepository.setInstantScan(enabled)
    fun setDebugLegacyLlm(enabled: Boolean) = debugSettingsRepository.setLegacyLlmChat(enabled)
    private val spritePreferences = com.tamerslab.di.AppServices.spritePreferenceRepository
    val spritePreference = spritePreferences.preference
    fun setSpritePreference(preference: com.tamerslab.species.SpritePreference) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.tamerslab.di.AppServices.gameplayRepository.synchronizeSpritePreference(preference) {
                    spritePreferences.setPreference(preference)
                }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { context.showAppFeedback(R.string.sprite_preference_save_failed, important = true) }
        }
    }
    private val secretsRepository: SecretsRepository = com.tamerslab.di.AppServices.dataStoreSecretsRepository
    private val cardImports = ViewModelProvider(context, viewModelFactory {
        initializer { CardImportViewModel(application, createSavedStateHandle()) }
    })[CardImportViewModel::class.java]
    val cardImportState get() = cardImports.state
    private val databaseManagementController = DatabaseManagementController(
        componentActivity = context,
        application = application
    )

    val llmSettingsRepository: LlmSettingsRepository = com.tamerslab.di.AppServices.llmSettingsRepository
    val currentLlmApiKey: Flow<String?> = llmSettingsRepository.apiKey
    val currentLlmModel: Flow<String> = llmSettingsRepository.model
    val currentLlmTemperature: Flow<Double> = llmSettingsRepository.temperature
    val currentLlmBaseUrl: Flow<String> = llmSettingsRepository.chatCompletionsBaseUrl
    val currentLlmProvider: Flow<ChatApiProvider> = llmSettingsRepository.activeProvider
    val savedLlmProviderSettings: Flow<Map<ChatApiProvider, LlmProviderSettings>> =
        llmSettingsRepository.providerSettings
    val currentSystemPromptTemplate: Flow<String?> = llmSettingsRepository.systemPromptTemplate
    val currentWildSystemPromptTemplate: Flow<String?> = llmSettingsRepository.wildSystemPromptTemplate
    val currentTamerName: Flow<String> = llmSettingsRepository.tamerName
    private val speciesSettingsRepository: SpeciesSettingsRepository = com.tamerslab.di.AppServices.speciesSettingsRepository
    val promptOriginAtImportTime: Flow<Boolean> = speciesSettingsRepository.promptOriginAtImportTime
    val pendingCardOriginPrompts: StateFlow<List<PendingCardOriginPrompt>> = cardImports.pendingOrigins

    private val _showLlmDialog = MutableStateFlow(restoredDialogs?.getBoolean("llm") ?: false)
    val showLlmDialog: StateFlow<Boolean> = _showLlmDialog
    private val _showPromptTemplateDialog = MutableStateFlow(debugSettingsRepository.enabled && restoredDialogs?.getBoolean("prompt") == true)
    val showPromptTemplateDialog: StateFlow<Boolean> = _showPromptTemplateDialog
    private val _showWildPromptTemplateDialog = MutableStateFlow(debugSettingsRepository.enabled && restoredDialogs?.getBoolean("wild_prompt") == true)
    val showWildPromptTemplateDialog: StateFlow<Boolean> = _showWildPromptTemplateDialog
    private val _currentLanguage = MutableStateFlow(
        languagePreferences.getString("language_tag", null)
            ?: AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "en" }
    )
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    private val _currentFont = MutableStateFlow(
        AppFont.fromPreference(languagePreferences.getString("app_font", null))
    )
    val currentFont: StateFlow<AppFont> = _currentFont.asStateFlow()
    private val themeSettings = AppThemeSettings(languagePreferences)
    val currentTheme: StateFlow<AppTheme> = themeSettings.currentTheme
    private val mutableImportingConnection = MutableStateFlow(false)
    val importingConnection = mutableImportingConnection.asStateFlow()

    fun setAppTheme(appTheme: AppTheme) {
        context.setTheme(appTheme.nativeThemeResource)
        themeSettings.setTheme(appTheme)
    }

    init {
        context.lifecycleScope.launch {
            debugSettings.collect { settings ->
                if (!settings.enabled) {
                    _showPromptTemplateDialog.value = false
                    _showWildPromptTemplateDialog.value = false
                }
            }
        }
        context.savedStateRegistry.registerSavedStateProvider("vbhelper.settings_dialogs") {
            android.os.Bundle().apply {
                putBoolean("llm", _showLlmDialog.value)
                putBoolean("prompt", _showPromptTemplateDialog.value)
                putBoolean("wild_prompt", _showWildPromptTemplateDialog.value)
            }
        }
        filePickerLauncher = context.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                databaseManagementController.exportDatabase(uri)
            } else {
                context.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.ui_no_destination), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        filePickerOpenerLauncher = context.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                databaseManagementController.importDatabase(uri)
            } else {
                context.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.ui_no_source), Toast.LENGTH_SHORT).show()
                }
            }
        }

        filePickerApk = context.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                importApk(uri)
            } else {
                context.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.ui_apk_cancelled), Toast.LENGTH_SHORT).show()
                }
            }
        }

        filePickerCard = context.registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            cardImports.start(uris)
        }
    }

    override fun onClickOpenDirectory() {
        filePickerLauncher.launch("Partner records.vbhelper")
    }

    override fun onClickImportDatabase() {
        filePickerOpenerLauncher.launch(arrayOf("application/octet-stream"))
    }

    override fun onClickImportApk() {
        if (mutableImportingConnection.value) return
        filePickerApk.launch(arrayOf("*/*"))
    }

    override fun onClickImportCard() {
        if (cardImportState.value.isRunning) return
        cardImports.preparePicker(alwaysAskForNewCards = false)
        filePickerCard.launch(arrayOf("*/*"))
    }

    fun onClickImportCardsFromDex() {
        if (cardImportState.value.isRunning) return
        cardImports.preparePicker(alwaysAskForNewCards = true)
        filePickerCard.launch(arrayOf("*/*"))
    }

    fun stopCardImport() = cardImports.stop()
    fun dismissCardImportResult() = cardImports.dismissResult()

    override fun onClickConfigureLlm() {
        _showLlmDialog.value = true
    }

    fun dismissLlmDialog() {
        _showLlmDialog.value = false
    }

    fun saveLlmSettings(
        provider: ChatApiProvider,
        apiKey: String,
        model: String,
        baseUrl: String,
        temperature: Double
    ) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.saveProviderSettings(
                provider = provider,
                apiKey = apiKey,
                model = model,
                baseUrl = baseUrl,
                temperature = if (debugSettingsRepository.enabled) temperature
                    else llmSettingsRepository.providerSettings.first()[provider]?.temperature
                        ?: com.tamerslab.settings.data.DEFAULT_ROLEPLAY_TEMPERATURE
            )

            context.runOnUiThread {
                context.showAppFeedback(R.string.ui_chat_saved)
                dismissLlmDialog()
            }
        }
    }

    fun onClickConfigurePromptTemplate() {
        if (debugSettingsRepository.enabled) _showPromptTemplateDialog.value = true
    }

    fun dismissPromptTemplateDialog() {
        _showPromptTemplateDialog.value = false
    }

    fun savePromptTemplate(template: String?) {
        if (!debugSettingsRepository.enabled) return
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setSystemPromptTemplate(template)
            context.runOnUiThread { dismissPromptTemplateDialog() }
        }
    }

    fun onClickConfigureWildPromptTemplate() {
        if (debugSettingsRepository.enabled) _showWildPromptTemplateDialog.value = true
    }

    fun dismissWildPromptTemplateDialog() {
        _showWildPromptTemplateDialog.value = false
    }

    fun saveWildPromptTemplate(template: String?) {
        if (!debugSettingsRepository.enabled) return
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setWildSystemPromptTemplate(template)
            context.runOnUiThread { dismissWildPromptTemplateDialog() }
        }
    }

    fun saveTamerName(name: String) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setTamerName(name)
            context.runOnUiThread {
                context.showAppFeedback(R.string.ui_tamer_saved)
            }
        }
    }

    fun setPromptOriginAtImportTime(value: Boolean) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            speciesSettingsRepository.setPromptOriginAtImportTime(value)
        }
    }

    fun dismissPendingCardOriginPrompt(cardId: Long) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            speciesSettingsRepository.clearPendingCardOriginPrompt(cardId)
        }
    }

    fun setPendingCardOrigin(cardId: Long, status: OfficialStatus) = cardImports.selectOrigin(cardId, status)

    fun setLanguage(languageTag: String) {
        languagePreferences.edit()
            .putString("language_tag", languageTag)
            .commit()
        _currentLanguage.value = languageTag
        AppCompatDelegate.setApplicationLocales(
            if (languageTag == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
        )
        context.recreate()
    }

    fun setAppFont(appFont: AppFont) {
        languagePreferences.edit()
            .putString("app_font", appFont.preferenceValue)
            .apply()
        _currentFont.value = appFont
        context.recreate()
    }

    private fun importApk(uri: Uri) {
        if (mutableImportingConnection.value) return
        mutableImportingConnection.value = true
        context.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val imported = context.contentResolver.openInputStream(uri)?.use(secretsImporter::importSecrets)
                    ?: error("No connection data")
                check(!imported.isMissingSecrets()) { "Incomplete connection data" }
                secretsRepository.updateSecrets(imported)
                context.showAppFeedback(R.string.ui_secrets_imported, important = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { context.showAppFeedback(R.string.ui_secrets_import_failed, important = true) }
            finally { mutableImportingConnection.value = false }
        }
    }
}

