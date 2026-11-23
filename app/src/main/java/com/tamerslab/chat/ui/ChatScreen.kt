package com.tamerslab.chat.ui

import android.content.ClipData
import com.tamerslab.navigation.AppRoute
import com.tamerslab.ui.state.RequestState
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.tamerslab.R
import com.tamerslab.components.ChatComposer
import com.tamerslab.components.ChatHistoryPanel
import com.tamerslab.components.ChatMessageBubble
import com.tamerslab.components.ChatStatusPanel
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalButton
import com.tamerslab.chat.ui.dialogs.SpeciesManualEditDialog
import com.tamerslab.components.showAppFeedback
import com.tamerslab.digiline.freechat.model.FreeChatChip
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ChatScreen(
    navController: NavController,
    chatScreenController: ChatScreenController,
    characterId: Long
) {
    val debugSettings = com.tamerslab.components.rememberDebugSettings()
    val participation = com.tamerslab.components.rememberPartnerParticipation()
    val usable = participation?.owned(characterId)?.usable != false
    var speciesContext by remember { mutableStateOf<SpeciesContext?>(null) }
    var showManualDialog by rememberSaveable(characterId) { mutableStateOf(false) }
    var speciesGateResolved by rememberSaveable(characterId) { mutableStateOf(false) }
    val conversationTitle = speciesContext?.nickname?.takeIf(String::isNotBlank)
        ?: speciesContext?.existingProfile?.speciesName?.takeIf { it.isNotBlank() }
        ?: speciesContext?.cardName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.nav_chat)

    LaunchedEffect(characterId) {
        chatScreenController.getSpeciesContext(characterId) { context ->
            speciesContext = context
            if (debugSettings.enabled && context.existingProfile == null && !speciesGateResolved) showManualDialog = true
            else speciesGateResolved = true
        }
    }
    LaunchedEffect(debugSettings.enabled, speciesContext) {
        if (!debugSettings.enabled && speciesContext != null) {
            showManualDialog = false
            speciesGateResolved = true
        }
    }

    if (showManualDialog && debugSettings.enabled) {
        speciesContext?.let { context ->
            SpeciesManualEditDialog(
                cardName = context.cardName,
                onDismiss = { navController.popBackStack() },
                onSkip = {
                    showManualDialog = false
                    speciesGateResolved = true
                },
                onSave = { result ->
                    chatScreenController.saveManualSpeciesProfile(
                        context.cardCharacterId, result.name, result.level, result.type,
                        result.profile, result.specialMoves
                    ) {
                        showManualDialog = false
                        speciesGateResolved = true
                    }
                }
            )
        }
    }

    if (!speciesGateResolved) {
        Scaffold(
            topBar = { TopBanner(text = conversationTitle, onBackClick = { navController.popBackStack() }) },
            contentWindowInsets = WindowInsets.statusBars
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val messages by chatScreenController.getHistory(characterId).collectAsStateWithLifecycle(emptyList())
    val mood by chatScreenController.getMood(characterId).collectAsStateWithLifecycle(50)
    val scene by chatScreenController.getScene(characterId).collectAsStateWithLifecycle(null)
    val syncState by chatScreenController.getPersonalitySyncState(characterId)
        .collectAsStateWithLifecycle(PersonalitySyncUiState.NONE)
    val legacyLlmEnabled = chatScreenController.isLegacyLlmEnabled()
    val freeChatEnabled = !legacyLlmEnabled && chatScreenController.isFreeChatEnabled()
    var showShiftNotice by rememberSaveable(characterId) { mutableStateOf(false) }
    LaunchedEffect(characterId) { chatScreenController.ensureScene(characterId) { } }
    LaunchedEffect(characterId) {
        chatScreenController.isShiftNoticePending(characterId) { pending -> if (pending) showShiftNotice = true }
    }
    var input by rememberSaveable(characterId) { mutableStateOf("") }
    var requestState by remember { mutableStateOf<RequestState<Unit>>(RequestState.Idle) }
    val sending = requestState is RequestState.Loading
    val error = (requestState as? RequestState.Failure)?.message
    var selectedMessage by remember { mutableStateOf<com.tamerslab.domain.chat.ChatMessageEntity?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val listState = rememberLazyListState()

    // Authored Free Talk state stays separate from the legacy request state so the Debug LLM path is unchanged.
    var freeChatInput by rememberSaveable(characterId) { mutableStateOf("") }
    var freeChatChips by remember { mutableStateOf<List<FreeChatChip>>(emptyList()) }
    var freeChatSending by remember { mutableStateOf(false) }
    var freeChatError by remember { mutableStateOf<String?>(null) }
    var freeChatNotice by remember { mutableStateOf<FreeChatNotice?>(null) }
    var showFreeChatClearDialog by remember { mutableStateOf(false) }

    val reportFreeChatFailure: (Throwable) -> Unit = { failure ->
        freeChatError = failure.message?.takeIf { it.isNotBlank() }
            ?: resources.getString(R.string.freechat_unavailable)
    }
    // Exactly one open per screen entry: the repository persists the greeting once per cadence window.
    LaunchedEffect(characterId, freeChatEnabled) {
        if (!freeChatEnabled) return@LaunchedEffect
        chatScreenController.openFreeChat(characterId) { result ->
            result.fold(
                onSuccess = { view ->
                    freeChatChips = view?.chips.orEmpty().take(FREE_CHAT_MAX_CHIPS)
                    freeChatError = null
                },
                onFailure = reportFreeChatFailure
            )
        }
    }
    val submitFreeChat: (String) -> Unit = submit@{ raw ->
        val text = raw.trim()
        if (!freeChatEnabled || !usable || freeChatSending || text.isEmpty()) return@submit
        freeChatSending = true
        freeChatError = null
        chatScreenController.sendFreeText(characterId, text) { result ->
            freeChatSending = false
            result.fold(
                onSuccess = { view ->
                    freeChatChips = view?.chips.orEmpty().take(FREE_CHAT_MAX_CHIPS)
                    freeChatNotice = when (view?.closeReason) {
                        null -> null
                        "SAFETY" -> FreeChatNotice(resources.getString(R.string.freechat_closed_safety), isSafety = true)
                        else -> FreeChatNotice(resources.getString(R.string.freechat_boundary))
                    }
                    if (freeChatInput == raw) freeChatInput = ""
                },
                onFailure = reportFreeChatFailure
            )
        }
    }
    val clearFreeChatThread: () -> Unit = clear@{
        if (!freeChatEnabled || !usable || freeChatSending) return@clear
        freeChatSending = true
        chatScreenController.clearFreeChat(characterId) {
            freeChatSending = false
            freeChatChips = emptyList()
            freeChatNotice = null
            freeChatError = null
            context.showAppFeedback(R.string.freechat_clear_done)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        chatScreenController.markAssistantMessagesRead(characterId)
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = conversationTitle,
                onBackClick = { navController.popBackStack() }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .imePadding()
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!usable) Text(stringResource(R.string.participation_inactive_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The exact bond/mood reading is a Debug instrument; normal DigiLine chat stays authored.
            if (debugSettings.enabled && !WindowInsets.isImeVisible) ChatStatusPanel(
                label = stringResource(R.string.ui_chat_mood_title, mood),
                value = mood,
                summary = com.tamerslab.components.moodSummary(mood)
            )
            if (syncState == PersonalitySyncUiState.PENDING) {
                Text(
                    text = stringResource(R.string.digiline_sync_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (syncState == PersonalitySyncUiState.UNAVAILABLE) {
                Text(
                    text = stringResource(R.string.digiline_sync_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ChatHistoryPanel(
                isEmpty = messages.isEmpty(),
                emptyMessage = stringResource(
                    when {
                        legacyLlmEnabled -> R.string.ui_chat_empty
                        freeChatEnabled -> R.string.freechat_empty
                        else -> R.string.digiline_chat_empty
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                        contentType = { "chat-message" }
                    ) { message ->
                        ChatMessageBubble(
                            text = message.content,
                            isUser = message.role == "user",
                            authorLabel = if (message.role == "notice") stringResource(R.string.immersion_record_notice) else null,
                            onLongClick = { selectedMessage = message }
                        )
                    }
                }
            }

            selectedMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = { selectedMessage = null },
                    title = { Text(stringResource(R.string.ui_message_actions)) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            VitalButton(
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Digimon message", message.content))
                                    selectedMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.ui_copy)) }
                            if (message.role == "user" && legacyLlmEnabled) {
                                VitalButton(
                                    enabled = !sending && usable,
                                    onClick = {
                                        requestState = RequestState.Loading
                                        selectedMessage = null
                                        chatScreenController.resendMessage(characterId, message.id, message.content) { result ->
                                            requestState = result.fold({ RequestState.Success(Unit) },
                                                { RequestState.Failure(resources.getString(R.string.app_chat_failed), it) })
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.ui_resend)) }
                            }
                            VitalButton(
                                onClick = {
                                    chatScreenController.deleteFromMessage(characterId, message.id)
                                    selectedMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.ui_delete)) }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedMessage = null }) {
                            Text(stringResource(R.string.ui_cancel))
                        }
                    },
                    confirmButton = {}
                )
            }

            scene?.let { activeScene ->
                DigilineSceneCard(
                    scene = activeScene,
                    enabled = usable && !sending,
                    onOptionSelected = { optionId ->
                        requestState = RequestState.Loading
                        chatScreenController.chooseSceneOption(characterId, optionId) { result ->
                            requestState = result.fold(
                                { RequestState.Success(Unit) },
                                { RequestState.Failure(resources.getString(R.string.app_chat_failed), it) }
                            )
                        }
                    },
                    onDismiss = { chatScreenController.dismissScene(characterId) { } }
                )
            }

            if (legacyLlmEnabled) {
                ChatComposer(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = stringResource(R.string.ui_chat_placeholder),
                    sendLabel = stringResource(R.string.ui_send),
                    sending = sending,
                    enabled = usable,
                    errorMessage = error,
                    errorActionLabel = stringResource(R.string.ui_settings),
                    onErrorAction = { navController.navigate(com.tamerslab.navigation.AppRoute.Settings) },
                    onSend = {
                        val text = input
                        requestState = RequestState.Loading
                        chatScreenController.sendMessage(characterId, text) { result ->
                            requestState = result.fold({ RequestState.Success(Unit) },
                                { RequestState.Failure(resources.getString(R.string.app_chat_failed), it) })
                            result.onSuccess {
                                if (input == text) input = ""
                            }
                        }
                    }
                )
            } else if (freeChatEnabled) {
                // A pending authored card owns the interaction slot; the composer returns when it closes.
                if (scene == null) {
                    FreeChatComposer(
                        chips = freeChatChips,
                        value = freeChatInput,
                        onValueChange = { freeChatInput = it },
                        placeholder = stringResource(R.string.freechat_placeholder),
                        sendLabel = stringResource(R.string.freechat_send),
                        chipsLabel = stringResource(R.string.freechat_chips_label),
                        clearLabel = stringResource(R.string.freechat_clear),
                        sending = freeChatSending,
                        enabled = usable,
                        errorMessage = freeChatError ?: error,
                        notice = freeChatNotice,
                        onChipClick = { chip -> submitFreeChat(chip.text) },
                        onSend = { submitFreeChat(freeChatInput) },
                        onClear = { showFreeChatClearDialog = true }
                    )
                }
            } else if (scene == null && error == null) {
                Text(
                    text = stringResource(R.string.digiline_idle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showFreeChatClearDialog) {
        AlertDialog(
            onDismissRequest = { showFreeChatClearDialog = false },
            title = { Text(stringResource(R.string.freechat_clear_title)) },
            text = { Text(stringResource(R.string.freechat_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showFreeChatClearDialog = false
                    clearFreeChatThread()
                }) { Text(stringResource(R.string.freechat_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showFreeChatClearDialog = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }

    if (showShiftNotice) {
        AlertDialog(
            onDismissRequest = {
                chatScreenController.markShiftNoticeShown(characterId)
                showShiftNotice = false
            },
            title = { Text(stringResource(R.string.digiline_shift_notice_title)) },
            text = { Text(stringResource(R.string.digiline_shift_notice_body)) },
            confirmButton = {
                TextButton(onClick = {
                    chatScreenController.markShiftNoticeShown(characterId)
                    showShiftNotice = false
                }) { Text(stringResource(R.string.ui_ok)) }
            }
        )
    }
}




