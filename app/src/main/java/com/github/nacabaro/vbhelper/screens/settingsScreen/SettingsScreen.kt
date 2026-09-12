package com.github.nacabaro.vbhelper.screens.settingsScreen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.settingsScreen.dialogs.LlmSettingsDialog
import com.github.nacabaro.vbhelper.screens.settingsScreen.dialogs.PromptTemplateDialog
import com.github.nacabaro.vbhelper.screens.settingsScreen.dialogs.LanguageDialog
import com.github.nacabaro.vbhelper.chat.DigimonPersonaBuilder
import com.github.nacabaro.vbhelper.R
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import com.github.nacabaro.vbhelper.ui.theme.AppFont
import com.github.nacabaro.vbhelper.ui.theme.appFontFamily


@Composable
fun SettingsScreen(
    navController: NavController,
    settingsScreenController: SettingsScreenControllerImpl,
) {
    val context = LocalContext.current

    val showLlmDialog by settingsScreenController.showLlmDialog.collectAsState()
    val currentApiKey by settingsScreenController.currentLlmApiKey.collectAsState(initial = null)
    val currentModel by settingsScreenController.currentLlmModel.collectAsState(initial = "openrouter/auto")
    val currentBaseUrl by settingsScreenController.currentLlmBaseUrl.collectAsState(initial = "https://openrouter.ai/api/v1/")
    val currentSystemPromptTemplate by settingsScreenController.currentSystemPromptTemplate.collectAsState(initial = null)
    val currentWildSystemPromptTemplate by settingsScreenController.currentWildSystemPromptTemplate.collectAsState(initial = null)
    val currentTamerName by settingsScreenController.currentTamerName.collectAsState(initial = "")
    val showPromptTemplateDialog by settingsScreenController.showPromptTemplateDialog.collectAsState()
    val showWildPromptTemplateDialog by settingsScreenController.showWildPromptTemplateDialog.collectAsState()
    val promptOriginAtImport by settingsScreenController.promptOriginAtImportTime.collectAsState(initial = false)
    val currentLanguage by settingsScreenController.currentLanguage.collectAsState()
    val currentFont by settingsScreenController.currentFont.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.settings_title),
                onBackClick = {
                    navController.popBackStack()
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_nfc))
            SettingsEntry(
                title = stringResource(R.string.settings_import_apk_title),
                description = stringResource(R.string.settings_import_apk_desc)
            ) {
                settingsScreenController.onClickImportApk()
            }

            SettingsSection(title = stringResource(R.string.settings_section_dim_bem))
            SettingsEntry(
                title = stringResource(R.string.settings_import_card_title),
                description = stringResource(R.string.settings_import_card_desc)
            ) {
                settingsScreenController.onClickImportCard()
            }

            SettingsSection(title = stringResource(R.string.settings_section_appearance))
            SettingsEntry(
                title = stringResource(R.string.settings_font_title),
                description = currentFont.displayName
            ) {
                showFontDialog = true
            }

            SettingsSection(title = stringResource(R.string.settings_section_llm_chat))
            SettingsEntry(
                title = stringResource(R.string.ui_language),
                description = when (currentLanguage) {
                    "en" -> "English"
                    "pt-BR" -> "Português (Brasil)"
                    "ja" -> "日本語"
                    else -> "Idioma do sistema"
                }
            ) {
                showLanguageDialog = true
            }
            var tamerName by remember(currentTamerName) { mutableStateOf(currentTamerName) }
            OutlinedTextField(
                value = tamerName,
                onValueChange = { tamerName = it },
                label = { Text(stringResource(R.string.ui_tamer_name)) },
                supportingText = { Text(stringResource(R.string.ui_tamer_name_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            SettingsEntry(
                title = stringResource(R.string.ui_save_tamer_name),
                description = stringResource(
                    R.string.ui_current_name,
                    currentTamerName.ifBlank { stringResource(R.string.ui_not_defined) }
                )
            ) {
                settingsScreenController.saveTamerName(tamerName)
            }
            SettingsEntry(
                title = stringResource(R.string.settings_configure_llm_title),
                description = stringResource(R.string.settings_configure_llm_desc)
            ) {
                settingsScreenController.onClickConfigureLlm()
            }
            SettingsEntry(
                title = stringResource(R.string.ui_personality_prompt),
                description = stringResource(R.string.ui_edit_prompt_description)
            ) {
                settingsScreenController.onClickConfigurePromptTemplate()
            }
            SettingsEntry(
                title = stringResource(R.string.ui_wild_digimon_prompt),
                description = stringResource(R.string.ui_wild_digimon_prompt_desc)
            ) {
                settingsScreenController.onClickConfigureWildPromptTemplate()
            }
            SettingsEntry(
                title = stringResource(R.string.ui_lorebook_title),
                description = stringResource(R.string.ui_lorebook_description)
            ) {
                navController.navigate(NavigationItems.Lorebook.route)
            }

            SettingsSection(title = stringResource(R.string.ui_species_section))
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_ask_card_origin))
                    Text(
                        stringResource(R.string.ui_card_origin_toggle_description),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = promptOriginAtImport,
                    onCheckedChange = settingsScreenController::setPromptOriginAtImportTime
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_about))
            SettingsEntry(
                title = stringResource(R.string.settings_credits_title),
                description = stringResource(R.string.settings_credits_desc)
            ) {
                navController.navigate(NavigationItems.Credits.route)
            }
            SettingsEntry(
                title = stringResource(R.string.settings_about_title),
                description = stringResource(R.string.settings_about_desc)
            ) {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/nacabaro/vbhelper/")
                )
                context.startActivity(browserIntent)
            }

            SettingsSection(title = stringResource(R.string.settings_section_data))
            SettingsEntry(
                title = stringResource(R.string.settings_export_data_title),
                description = stringResource(R.string.settings_export_data_desc)
            ) {
                settingsScreenController.onClickOpenDirectory()
            }
            SettingsEntry(
                title = stringResource(R.string.settings_import_data_title),
                description = stringResource(R.string.settings_import_data_desc)
            ) {
                settingsScreenController.onClickImportDatabase()
            }
        }
    }

    if (showLlmDialog) {
        LlmSettingsDialog(
            currentApiKey = currentApiKey,
            currentModel = currentModel,
            currentBaseUrl = currentBaseUrl,
            onDismiss = { settingsScreenController.dismissLlmDialog() },
            onSave = { apiKey, model, baseUrl ->
                settingsScreenController.saveLlmSettings(apiKey, model, baseUrl)
            }
        )
    }

    if (showPromptTemplateDialog) {
        PromptTemplateDialog(
            currentTemplate = currentSystemPromptTemplate,
            onDismiss = { settingsScreenController.dismissPromptTemplateDialog() },
            onSave = settingsScreenController::savePromptTemplate
        )
    }

    if (showWildPromptTemplateDialog) {
        PromptTemplateDialog(
            currentTemplate = currentWildSystemPromptTemplate,
            onDismiss = { settingsScreenController.dismissWildPromptTemplateDialog() },
            onSave = settingsScreenController::saveWildPromptTemplate,
            title = stringResource(R.string.ui_wild_digimon_prompt),
            defaultTemplate = DigimonPersonaBuilder.DEFAULT_WILD_SYSTEM_PROMPT_TEMPLATE.trimIndent()
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selectedLanguage = currentLanguage,
            onLanguageSelected = {
                settingsScreenController.setLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text(stringResource(R.string.settings_font_title)) },
            text = {
                Column {
                    AppFont.entries.forEach { appFont ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsScreenController.setAppFont(appFont)
                                    showFontDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = appFont == currentFont,
                                onClick = {
                                    settingsScreenController.setAppFont(appFont)
                                    showFontDialog = false
                                }
                            )
                            Text(
                                text = appFont.displayName,
                                fontFamily = appFontFamily(appFont),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) {
                    Text(stringResource(R.string.ui_close))
                }
            }
        )
    }
}

@Composable
fun SettingsEntry(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(text = title)
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun SettingsSection(
    title: String
) {
    Box(
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
