package com.tamerslab.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tamerslab.components.TopBanner
import com.tamerslab.components.LabeledSwitchRow
import com.tamerslab.components.VitalSectionBar
import com.tamerslab.components.cyberFrame
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import com.tamerslab.components.BackgroundMusicControlPanel
import com.tamerslab.audio.AppMusicController
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.settings.ui.dialogs.LlmSettingsDialog
import com.tamerslab.settings.ui.dialogs.PromptTemplateDialog
import com.tamerslab.settings.ui.dialogs.LanguageDialog
import com.tamerslab.chat.DigimonPersonaBuilder
import com.tamerslab.R
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import com.tamerslab.ui.theme.AppFont
import com.tamerslab.ui.theme.appFontFamily
import com.tamerslab.chat.ChatApiProvider
import com.tamerslab.settings.data.DEFAULT_ROLEPLAY_TEMPERATURE
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.species.ui.card.CardBatchImportPanel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight


@Composable
fun SettingsScreen(
    navController: NavController,
    settingsScreenController: SettingsScreenControllerImpl,
    musicController: AppMusicController
) {
    val context = LocalContext.current

    val showLlmDialog by settingsScreenController.showLlmDialog.collectAsStateWithLifecycle()
    val currentApiKey by settingsScreenController.currentLlmApiKey.collectAsStateWithLifecycle(initialValue = null)
    val currentModel by settingsScreenController.currentLlmModel.collectAsStateWithLifecycle(
        initialValue = ChatApiProvider.OPENROUTER.suggestedModel.orEmpty()
    )
    val currentTemperature by settingsScreenController.currentLlmTemperature.collectAsStateWithLifecycle(
        initialValue = DEFAULT_ROLEPLAY_TEMPERATURE
    )
    val currentBaseUrl by settingsScreenController.currentLlmBaseUrl.collectAsStateWithLifecycle(initialValue = "https://openrouter.ai/api/v1/")
    val currentProvider by settingsScreenController.currentLlmProvider.collectAsStateWithLifecycle(initialValue = ChatApiProvider.OPENROUTER)
    val savedProviderSettings by settingsScreenController.savedLlmProviderSettings.collectAsStateWithLifecycle(initialValue = emptyMap())
    val currentSystemPromptTemplate by settingsScreenController.currentSystemPromptTemplate.collectAsStateWithLifecycle(initialValue = null)
    val currentWildSystemPromptTemplate by settingsScreenController.currentWildSystemPromptTemplate.collectAsStateWithLifecycle(initialValue = null)
    val currentTamerName by settingsScreenController.currentTamerName.collectAsStateWithLifecycle(initialValue = "")
    val showPromptTemplateDialog by settingsScreenController.showPromptTemplateDialog.collectAsStateWithLifecycle()
    val showWildPromptTemplateDialog by settingsScreenController.showWildPromptTemplateDialog.collectAsStateWithLifecycle()
    val promptOriginAtImport by settingsScreenController.promptOriginAtImportTime.collectAsStateWithLifecycle(initialValue = false)
    val currentLanguage by settingsScreenController.currentLanguage.collectAsStateWithLifecycle()
    val currentFont by settingsScreenController.currentFont.collectAsStateWithLifecycle()
    val currentTheme by settingsScreenController.currentTheme.collectAsStateWithLifecycle()
    val cardImports by settingsScreenController.cardImportState.collectAsStateWithLifecycle()
    val debugSettings by settingsScreenController.debugSettings.collectAsStateWithLifecycle()
    val spritePreference by settingsScreenController.spritePreference.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showSpeciesLibrary by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
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
            CardBatchImportPanel(cardImports, settingsScreenController::stopCardImport,
                settingsScreenController::dismissCardImportResult)

            SettingsSection(title = stringResource(R.string.settings_section_appearance))
            AppThemePicker(currentTheme, settingsScreenController::setAppTheme)
            SettingsEntry(
                title = stringResource(R.string.settings_font_title),
                description = currentFont.displayName
            ) {
                showFontDialog = true
            }
            SettingsSection(title = stringResource(R.string.immersion_audio))
            BackgroundMusicControlPanel(
                musicController = musicController,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SettingsSection(title = stringResource(R.string.immersion_tamer_preferences))
            SettingsEntry(
                title = stringResource(R.string.ui_language),
                description = when (currentLanguage) {
                    "en" -> "English"
                    "pt-BR" -> "Português (Brasil)"
                    "ja" -> "æ—¥æœ¬èªž"
                    else -> stringResource(R.string.immersion_system_language)
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
            SettingsSection(title = stringResource(R.string.settings_section_llm_chat))
            SettingsEntry(
                title = stringResource(R.string.settings_configure_llm_title),
                description = stringResource(R.string.settings_configure_llm_desc)
            ) {
                settingsScreenController.onClickConfigureLlm()
            }
            SettingsEntry(
                title = stringResource(R.string.ui_lorebook_title),
                description = stringResource(R.string.ui_lorebook_description)
            ) {
                navController.navigate(AppRoute.Lorebook)
            }

            SettingsSection(title = stringResource(R.string.ui_species_section))
            SettingsEntry(title = stringResource(R.string.species_library), description = stringResource(R.string.species_library_hint)) {
                showSpeciesLibrary = true
            }
            LabeledSwitchRow(stringResource(R.string.ui_ask_card_origin), promptOriginAtImport,
                settingsScreenController::setPromptOriginAtImportTime, Modifier.padding(horizontal = 16.dp),
                description = stringResource(R.string.ui_card_origin_toggle_description))

            SettingsSection(title = stringResource(R.string.sprite_preference_section))
            Text(stringResource(R.string.sprite_preference_note), style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            SpritePreferenceOption(
                title = stringResource(R.string.sprite_preference_vb_title),
                description = stringResource(R.string.sprite_preference_vb_description),
                selected = spritePreference == com.tamerslab.species.SpritePreference.VB_FIRST,
                onSelect = { settingsScreenController.setSpritePreference(com.tamerslab.species.SpritePreference.VB_FIRST) })
            SpritePreferenceOption(
                title = stringResource(R.string.sprite_preference_dot_title),
                description = stringResource(R.string.sprite_preference_dot_description),
                selected = spritePreference == com.tamerslab.species.SpritePreference.DOT_FIRST,
                onSelect = { settingsScreenController.setSpritePreference(com.tamerslab.species.SpritePreference.DOT_FIRST) })

            SettingsSection(title = stringResource(R.string.settings_section_debug))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {}
                .toggleable(value = debugSettings.enabled, role = Role.Switch,
                    onValueChange = settingsScreenController::setDebugEnabled)
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.immersion_debug_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.immersion_debug_description), style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryOnDark)
                }
                Switch(checked = debugSettings.enabled, onCheckedChange = null)
            }
            if (debugSettings.enabled) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {}
                    .toggleable(value = debugSettings.instantScan, role = Role.Switch,
                        onValueChange = settingsScreenController::setDebugInstantScan)
                    .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.settings_debug_instant_scan_title),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_debug_instant_scan_description),
                            style = MaterialTheme.typography.bodySmall, color = TextSecondaryOnDark)
                    }
                    Switch(checked = debugSettings.instantScan, onCheckedChange = null)
                }
                SettingsEntry(title = stringResource(R.string.ui_personality_prompt),
                    description = stringResource(R.string.ui_edit_prompt_description)) {
                    settingsScreenController.onClickConfigurePromptTemplate()
                }
                SettingsEntry(title = stringResource(R.string.ui_wild_digimon_prompt),
                    description = stringResource(R.string.ui_wild_digimon_prompt_desc)) {
                    settingsScreenController.onClickConfigureWildPromptTemplate()
                }
                SettingsEntry(title = stringResource(R.string.immersion_debug_viewer),
                    description = stringResource(R.string.immersion_debug_viewer_description)) {
                    navController.navigate(AppRoute.Viewer)
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_about))
            SettingsEntry(
                title = stringResource(R.string.settings_credits_title),
                description = stringResource(R.string.settings_credits_desc)
            ) {
                navController.navigate(AppRoute.Credits)
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
                showRestoreConfirmation = true
            }
        }
    }

    if (showSpeciesLibrary) com.tamerslab.components.SpeciesPickerDialog(
        speciesNames = emptyList(), onDismiss = { showSpeciesLibrary = false }, onSpeciesSelected = {},
        editOnSelect = true, title = stringResource(R.string.species_library))

    if (showRestoreConfirmation) AlertDialog(
        onDismissRequest = { showRestoreConfirmation = false },
        title = { Text(stringResource(R.string.settings_import_data_title)) },
        text = { Text(stringResource(R.string.immersion_restore_confirmation)) },
        confirmButton = { TextButton(onClick = {
            showRestoreConfirmation = false
            settingsScreenController.onClickImportDatabase()
        }) { Text(stringResource(R.string.immersion_choose_backup)) } },
        dismissButton = { TextButton(onClick = { showRestoreConfirmation = false }) { Text(stringResource(R.string.ui_cancel)) } }
    )
    if (showLlmDialog) {
        LlmSettingsDialog(
            currentApiKey = currentApiKey,
            currentModel = currentModel,
            currentTemperature = currentTemperature,
            currentBaseUrl = currentBaseUrl,
            currentProvider = currentProvider,
            debugEnabled = debugSettings.enabled,
            savedProviderSettings = savedProviderSettings,
            onDismiss = { settingsScreenController.dismissLlmDialog() },
            onSave = { provider, apiKey, model, baseUrl, temperature ->
                settingsScreenController.saveLlmSettings(
                    provider,
                    apiKey,
                    model,
                    baseUrl,
                    temperature
                )
            }
        )
    }

    if (showPromptTemplateDialog && debugSettings.enabled) {
        PromptTemplateDialog(
            currentTemplate = currentSystemPromptTemplate,
            onDismiss = { settingsScreenController.dismissPromptTemplateDialog() },
            onSave = settingsScreenController::savePromptTemplate
        )
    }

    if (showWildPromptTemplateDialog && debugSettings.enabled) {
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppFont.entries.forEach { appFont ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = currentFont == appFont, role = Role.RadioButton, onClick = {
                                    settingsScreenController.setAppFont(appFont)
                                    showFontDialog = false
                                })
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = appFont == currentFont,
                                onClick = null
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
    onClick: (() -> Unit)? = null
) {
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .cyberFrame()
            .heightIn(min = 64.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VitalCyan
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String
) {
    VitalSectionBar(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun SpritePreferenceOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .cyberFrame()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimaryOnDark)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondaryOnDark,
                modifier = Modifier.padding(top = 3.dp))
        }
    }
}



