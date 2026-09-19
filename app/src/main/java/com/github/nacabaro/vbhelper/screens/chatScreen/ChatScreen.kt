package com.github.nacabaro.vbhelper.screens.chatScreen

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.screens.chatScreen.dialogs.SpeciesManualEditDialog

@Composable
fun ChatScreen(
    navController: NavController,
    chatScreenController: ChatScreenController,
    characterId: Long
) {
    var speciesContext by remember { mutableStateOf<SpeciesContext?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var speciesGateResolved by remember { mutableStateOf(false) }

    LaunchedEffect(characterId) {
        chatScreenController.getSpeciesContext(characterId) { context ->
            speciesContext = context
            if (context.existingProfile == null) showManualDialog = true else speciesGateResolved = true
        }
    }

    if (showManualDialog) {
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
            topBar = { TopBanner(text = stringResource(R.string.nav_chat), onBackClick = { navController.popBackStack() }) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val messages by chatScreenController.getHistory(characterId).collectAsState(emptyList())
    val mood by chatScreenController.getMood(characterId).collectAsState(50)
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMessage by remember { mutableStateOf<com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity?>(null) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        chatScreenController.markAssistantMessagesRead(characterId)
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.ui_chat_mood_title, mood),
                onBackClick = { navController.popBackStack() }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { "chat-message" }
                ) { message ->
                    val isUser = message.role == "user"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    selectedMessage = message
                                }
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
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
                                    enabled = !sending,
                                    onClick = {
                                        sending = true
                                        selectedMessage = null
                                        chatScreenController.resendMessage(characterId, message.id, message.content) { result ->
                                            sending = false
                                            result.onFailure { error = it.message }
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

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ui_chat_placeholder)) },
                    enabled = !sending
                )
                Spacer(modifier = Modifier.width(8.dp))
                VitalButton(
                    enabled = input.isNotBlank() && !sending,
                    onClick = {
                        val text = input
                        input = ""
                        sending = true
                        error = null
                        chatScreenController.sendMessage(characterId, text) { result ->
                            sending = false
                            result.onFailure { e -> error = e.message }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ui_send))
                }
            }
        }
    }
}
