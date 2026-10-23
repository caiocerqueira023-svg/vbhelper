package com.github.nacabaro.vbhelper.screens.chatScreen

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ChatComposer
import com.github.nacabaro.vbhelper.components.ChatHistoryPanel
import com.github.nacabaro.vbhelper.components.ChatMessageBubble
import com.github.nacabaro.vbhelper.components.ChatStatusPanel
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.screens.chatScreen.dialogs.SpeciesManualEditDialog
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ChatScreen(
    navController: NavController,
    chatScreenController: ChatScreenController,
    characterId: Long
) {
    val debugSettings = com.github.nacabaro.vbhelper.components.rememberDebugSettings()
    val participation = com.github.nacabaro.vbhelper.components.rememberPartnerParticipation()
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

    val messages by chatScreenController.getHistory(characterId).collectAsState(emptyList())
    val mood by chatScreenController.getMood(characterId).collectAsState(50)
    var input by rememberSaveable(characterId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMessage by remember { mutableStateOf<com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val listState = rememberLazyListState()

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
            if (!WindowInsets.isImeVisible) ChatStatusPanel(
                label = stringResource(R.string.ui_chat_mood_title, mood),
                value = mood,
                summary = com.github.nacabaro.vbhelper.components.moodSummary(mood)
            )

            ChatHistoryPanel(
                isEmpty = messages.isEmpty(),
                emptyMessage = stringResource(R.string.ui_chat_empty),
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
                            if (message.role == "user") {
                                VitalButton(
                                    enabled = !sending && usable,
                                    onClick = {
                                        sending = true
                                        selectedMessage = null
                                        chatScreenController.resendMessage(characterId, message.id, message.content) { result ->
                                            sending = false
                                             result.onFailure { error = resources.getString(R.string.app_chat_failed) }
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

            ChatComposer(
                value = input,
                onValueChange = { input = it },
                placeholder = stringResource(R.string.ui_chat_placeholder),
                sendLabel = stringResource(R.string.ui_send),
                sending = sending,
                enabled = usable,
                errorMessage = error,
                errorActionLabel = stringResource(R.string.ui_settings),
                onErrorAction = { navController.navigate(com.github.nacabaro.vbhelper.navigation.NavigationItems.Settings.route) },
                onSend = {
                    val text = input
                    sending = true
                    error = null
                    chatScreenController.sendMessage(characterId, text) { result ->
                        sending = false
                        result.onSuccess {
                            if (input == text) input = ""
                        }
                        result.onFailure { error = resources.getString(R.string.app_chat_failed) }
                    }
                }
            )
        }
    }
}
