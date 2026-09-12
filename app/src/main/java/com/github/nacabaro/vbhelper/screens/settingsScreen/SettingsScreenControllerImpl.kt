package com.github.nacabaro.vbhelper.screens.settingsScreen

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.screens.settingsScreen.controllers.CardImportController
import com.github.nacabaro.vbhelper.screens.settingsScreen.controllers.DatabaseManagementController
import com.github.nacabaro.vbhelper.source.ApkSecretsImporter
import com.github.nacabaro.vbhelper.source.LlmSettingsRepository
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import com.github.nacabaro.vbhelper.source.SecretsImporter
import com.github.nacabaro.vbhelper.source.SecretsRepository
import com.github.nacabaro.vbhelper.source.proto.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.AppFont


class SettingsScreenControllerImpl(
    private val context: ComponentActivity,
): SettingsScreenController {
    private val filePickerLauncher: ActivityResultLauncher<String>
    private val filePickerOpenerLauncher: ActivityResultLauncher<Array<String>>
    private val filePickerApk: ActivityResultLauncher<Array<String>>
    private val filePickerCard: ActivityResultLauncher<Array<String>>
    private val secretsImporter: SecretsImporter = ApkSecretsImporter()
    private val application = context.applicationContext as VBHelper
    private val languagePreferences = context.getSharedPreferences("app_preferences", 0)
    private val secretsRepository: SecretsRepository = application.container.dataStoreSecretsRepository
    private val database: AppDatabase = application.container.db
    private val databaseManagementController = DatabaseManagementController(
        componentActivity = context,
        application = application
    )

    val llmSettingsRepository: LlmSettingsRepository = application.container.llmSettingsRepository
    val currentLlmApiKey: Flow<String?> = llmSettingsRepository.apiKey
    val currentLlmModel: Flow<String> = llmSettingsRepository.model
    val currentLlmBaseUrl: Flow<String> = llmSettingsRepository.chatCompletionsBaseUrl
    val currentSystemPromptTemplate: Flow<String?> = llmSettingsRepository.systemPromptTemplate
    val currentWildSystemPromptTemplate: Flow<String?> = llmSettingsRepository.wildSystemPromptTemplate
    val currentTamerName: Flow<String> = llmSettingsRepository.tamerName
    private val speciesSettingsRepository: SpeciesSettingsRepository = application.container.speciesSettingsRepository
    val promptOriginAtImportTime: Flow<Boolean> = speciesSettingsRepository.promptOriginAtImportTime

    private val _showLlmDialog = MutableStateFlow(false)
    val showLlmDialog: StateFlow<Boolean> = _showLlmDialog
    private val _showPromptTemplateDialog = MutableStateFlow(false)
    val showPromptTemplateDialog: StateFlow<Boolean> = _showPromptTemplateDialog
    private val _showWildPromptTemplateDialog = MutableStateFlow(false)
    val showWildPromptTemplateDialog: StateFlow<Boolean> = _showWildPromptTemplateDialog
    private val _currentLanguage = MutableStateFlow(
        languagePreferences.getString("language_tag", null)
            ?: AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "system" }
    )
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    private val _currentFont = MutableStateFlow(
        AppFont.fromPreference(languagePreferences.getString("app_font", null))
    )
    val currentFont: StateFlow<AppFont> = _currentFont.asStateFlow()

    init {
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
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                importCard(uri)
            } else {
                context.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.ui_card_cancelled), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onClickOpenDirectory() {
        filePickerLauncher.launch("My application data.vbhelper")
    }

    override fun onClickImportDatabase() {
        filePickerOpenerLauncher.launch(arrayOf("application/octet-stream"))
    }

    override fun onClickImportApk() {
        filePickerApk.launch(arrayOf("*/*"))
    }

    override fun onClickImportCard() {
        filePickerCard.launch(arrayOf("*/*"))
    }

    override fun onClickConfigureLlm() {
        _showLlmDialog.value = true
    }

    fun dismissLlmDialog() {
        _showLlmDialog.value = false
    }

    fun saveLlmSettings(apiKey: String, model: String, baseUrl: String) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setApiKey(apiKey.trim())
            llmSettingsRepository.setModel(model.trim().ifBlank { "openrouter/auto" })
            llmSettingsRepository.setChatCompletionsBaseUrl(baseUrl)

            context.runOnUiThread {
                Toast.makeText(context, context.getString(R.string.ui_chat_saved), Toast.LENGTH_SHORT).show()
                dismissLlmDialog()
            }
        }
    }

    fun onClickConfigurePromptTemplate() {
        _showPromptTemplateDialog.value = true
    }

    fun dismissPromptTemplateDialog() {
        _showPromptTemplateDialog.value = false
    }

    fun savePromptTemplate(template: String?) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setSystemPromptTemplate(template)
            context.runOnUiThread { dismissPromptTemplateDialog() }
        }
    }

    fun onClickConfigureWildPromptTemplate() {
        _showWildPromptTemplateDialog.value = true
    }

    fun dismissWildPromptTemplateDialog() {
        _showWildPromptTemplateDialog.value = false
    }

    fun saveWildPromptTemplate(template: String?) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setWildSystemPromptTemplate(template)
            context.runOnUiThread { dismissWildPromptTemplateDialog() }
        }
    }

    fun saveTamerName(name: String) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setTamerName(name)
            context.runOnUiThread {
                Toast.makeText(context, context.getString(R.string.ui_tamer_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setPromptOriginAtImportTime(value: Boolean) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            speciesSettingsRepository.setPromptOriginAtImportTime(value)
        }
    }

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

    private fun importCard(uri: Uri) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)

            inputStream.use { fileReader ->
                val cardImportController = CardImportController(database)
                cardImportController.importCard(fileReader)
            }

            inputStream?.close()
            context.runOnUiThread {
                Toast.makeText(context, context.getString(R.string.ui_import_success), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importApk(uri: Uri) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri).use {
                if(it == null) {
                    context.runOnUiThread {
                        Toast.makeText(
                            context,
                            context.getString(R.string.ui_empty_file),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                val secrets: Secrets?
                try {
                    secrets = secretsImporter.importSecrets(it)
                } catch (e: Exception) {
                    context.runOnUiThread {
                        Toast.makeText(context, context.getString(R.string.ui_secrets_import_failed), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                context.lifecycleScope.launch(Dispatchers.IO) {
                    secretsRepository.updateSecrets(secrets)
                }.invokeOnCompletion {
                    context.runOnUiThread {
                        Toast.makeText(context, context.getString(R.string.ui_secrets_imported), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
