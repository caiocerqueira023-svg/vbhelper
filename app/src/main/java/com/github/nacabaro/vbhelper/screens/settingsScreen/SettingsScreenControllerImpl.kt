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


class SettingsScreenControllerImpl(
    private val context: ComponentActivity,
): SettingsScreenController {
    private val filePickerLauncher: ActivityResultLauncher<String>
    private val filePickerOpenerLauncher: ActivityResultLauncher<Array<String>>
    private val filePickerApk: ActivityResultLauncher<Array<String>>
    private val filePickerCard: ActivityResultLauncher<Array<String>>
    private val secretsImporter: SecretsImporter = ApkSecretsImporter()
    private val application = context.applicationContext as VBHelper
    private val secretsRepository: SecretsRepository = application.container.dataStoreSecretsRepository
    private val database: AppDatabase = application.container.db
    private val databaseManagementController = DatabaseManagementController(
        componentActivity = context,
        application = application
    )

    val llmSettingsRepository: LlmSettingsRepository = application.container.llmSettingsRepository
    val currentLlmApiKey: Flow<String?> = llmSettingsRepository.apiKey
    val currentLlmModel: Flow<String> = llmSettingsRepository.model
    private val speciesSettingsRepository: SpeciesSettingsRepository = application.container.speciesSettingsRepository
    val promptOriginAtImportTime: Flow<Boolean> = speciesSettingsRepository.promptOriginAtImportTime

    private val _showLlmDialog = MutableStateFlow(false)
    val showLlmDialog: StateFlow<Boolean> = _showLlmDialog

    init {
        filePickerLauncher = context.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                databaseManagementController.exportDatabase(uri)
            } else {
                context.runOnUiThread {
                    Toast.makeText(context, "No destination selected", Toast.LENGTH_SHORT)
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
                    Toast.makeText(context, "No source selected", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "APK import cancelled", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Card import cancelled", Toast.LENGTH_SHORT).show()
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

    fun saveLlmSettings(apiKey: String, model: String) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            llmSettingsRepository.setApiKey(apiKey.trim())
            llmSettingsRepository.setModel(model.trim().ifBlank { "openrouter/auto" })

            context.runOnUiThread {
                Toast.makeText(context, "Configurações de chat salvas!", Toast.LENGTH_SHORT).show()
                dismissLlmDialog()
            }
        }
    }

    fun setPromptOriginAtImportTime(value: Boolean) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            speciesSettingsRepository.setPromptOriginAtImportTime(value)
        }
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
                Toast.makeText(context, "Import successful!", Toast.LENGTH_SHORT).show()
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
                            "Selected file is empty!",
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
                        Toast.makeText(context, "Secrets import failed. Please only select the official app APK.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                context.lifecycleScope.launch(Dispatchers.IO) {
                    secretsRepository.updateSecrets(secrets)
                }.invokeOnCompletion {
                    context.runOnUiThread {
                        Toast.makeText(context, "Secrets successfully imported. Connections with devices are now possible.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
