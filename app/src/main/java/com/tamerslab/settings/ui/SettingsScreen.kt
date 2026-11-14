package com.tamerslab.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.audio.AppMusicController
import com.tamerslab.chat.ChatApiProvider
import com.tamerslab.chat.DigimonPersonaBuilder
import com.tamerslab.components.BackgroundMusicControlPanel
import com.tamerslab.components.LabeledSwitchRow
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalSectionBar
import com.tamerslab.components.cyberFrame
import com.tamerslab.navigation.AppRoute
import com.tamerslab.settings.data.DEFAULT_ROLEPLAY_TEMPERATURE
import com.tamerslab.settings.ui.dialogs.LanguageDialog
import com.tamerslab.settings.ui.dialogs.LlmSettingsDialog
import com.tamerslab.settings.ui.dialogs.PromptTemplateDialog
import com.tamerslab.species.SpritePreference
import com.tamerslab.species.ui.card.CardBatchImportPanel
import com.tamerslab.ui.theme.AppFont
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.appFontFamily

/**
 * Settings is ordered the way a setup-first app is scanned: resources the app needs, then how it
 * looks and sounds, then who is using it, then its collections and companion surfaces, then data,
 * then the developer switch, and finally app information. Each section header is the grouping
 * boundary; rows and section chrome are unchanged.
 */
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
    var showTamerNameDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showSpeciesLibrary by rememberSaveable { mutableStateOf(false) }

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
            // 1. What the app needs to talk to the watch and read cards.
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
            LabeledSwitchRow(stringResource(R.string.ui_ask_card_origin), promptOriginAtImport,
                settingsScreenController::setPromptOriginAtImportTime, Modifier.padding(horizontal = 16.dp),
                description = stringResource(R.string.ui_card_origin_toggle_description))

            // 2. How the app looks and sounds.
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

            // 3. Who is using the app.
            SettingsSection(title = stringResource(R.string.immersion_tamer_preferences))
            SettingsEntry(
                title = stringResource(R.string.ui_tamer_name),
                description = stringResource(
                    R.string.ui_current_name,
                    currentTamerName.ifBlank { stringResource(R.string.ui_not_defined) }
                )
            ) {
                showTamerNameDialog = true
            }
            SettingsEntry(
                title = stringResource(R.string.ui_language),
                description = when (currentLanguage) {
                    "en" -> "English"
                    "pt-BR" -> "Português (Brasil)"
                    "ja" -> "日本語"
                    else -> stringResource(R.string.immersion_system_language)
                }
            ) {
                showLanguageDialog = true
            }

            // 4. What the collection is made of.
            SettingsSection(title = stringResource(R.string.ui_species_section))
            SettingsEntry(title = stringResource(R.string.species_library), description = stringResource(R.string.species_library_hint)) {
                showSpeciesLibrary = true
            }
            Text(stringResource(R.string.sprite_preference_note), style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            SpritePreferenceOption(
                title = stringResource(R.string.sprite_preference_vb_title),
                description = stringResource(R.string.sprite_preference_vb_description),
                selected = spritePreference == SpritePreference.VB_FIRST,
                onSelect = { settingsScreenController.setSpritePreference(SpritePreference.VB_FIRST) })
            SpritePreferenceOption(
                title = stringResource(R.string.sprite_preference_dot_title),
                description = stringResource(R.string.sprite_preference_dot_description),
                selected = spritePreference == SpritePreference.DOT_FIRST,
                onSelect = { settingsScreenController.setSpritePreference(SpritePreference.DOT_FIRST) })

            // 5. Companion knowledge and its connection.
            SettingsSection(title = stringResource(R.string.settings_section_llm_chat))
            SettingsEntry(
                title = stringResource(R.string.ui_lorebook_title),
                description = stringResource(R.string.ui_lorebook_description)
            ) {
                navController.navigate(AppRoute.Lorebook)
            }
            if (debugSettings.enabled && debugSettings.legacyLlmChat) {
                SettingsEntry(
                    title = stringResource(R.string.settings_configure_llm_title),
                    description = stringResource(R.string.settings_configure_llm_desc)
                ) {
                    settingsScreenController.onClickConfigureLlm()
                }
            }

            // 6. Records maintenance before developer tooling.
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

            // 7. Developer machinery: one master switch, subordinate controls only while enabled.
            SettingsSection(title = stringResource(R.string.settings_section_debug))
            DebugSwitchRow(
                title = stringResource(R.string.immersion_debug_title),
                description = stringResource(R.string.immersion_debug_description),
                checked = debugSettings.enabled,
                onCheckedChange = settingsScreenController::setDebugEnabled
            )
            if (debugSettings.enabled) {
                LabeledSwitchRow(
                    stringResource(R.string.settings_debug_legacy_llm_title),
                    debugSettings.legacyLlmChat,
                    settingsScreenController::setDebugLegacyLlm,
                    Modifier.padding(horizontal = 16.dp),
                    description = stringResource(R.string.settings_debug_legacy_llm_description)
                )
                DebugSwitchRow(
                    title = stringResource(R.string.settings_debug_instant_scan_title),
                    description = stringResource(R.string.settings_debug_instant_scan_description),
                    checked = debugSettings.instantScan,
                    onCheckedChange = settingsScreenController::setDebugInstantScan
                )
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

            // 8. App information.
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
        }
    }

    if (showSpeciesLibrary) com.tamerslab.components.SpeciesPickerDialog(
        speciesNames = emptyList(), onDismiss = { showSpeciesLibrary = false }, onSpeciesSelected = {},
        editOnSelect = true, title = stringResource(R.string.species_library))

    if (showTamerNameDialog) {
        TamerNameDialog(
            currentName = currentTamerName,
            onSave = {
                showTamerNameDialog = false
                settingsScreenController.saveTamerName(it)
            },
            onDismiss = { showTamerNameDialog = false }
        )
    }

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
                            verticalAlignment = Alignment.CenterVertically,
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

/**
 * The Tamer name is edited in a dialog, like the language and font choices, instead of as an inline
 * field plus a separate save row in the middle of the list.
 */
@Composable
private fun TamerNameDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_tamer_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.ui_tamer_name_help)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
private fun DebugSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondaryOnDark)
        }
        Switch(checked = checked, onCheckedChange = null)
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
