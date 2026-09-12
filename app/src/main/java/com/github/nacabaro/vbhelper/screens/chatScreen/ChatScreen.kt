package com.github.nacabaro.vbhelper.screens.chatScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
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
        Scaffold(topBar = { TopBanner(text = "Conversar", onBackClick = { navController.popBackStack() }) }) { padding ->
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
    var messagePendingDeletion by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        chatScreenController.markAssistantMessagesRead(characterId)
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = "Conversar • Mood $mood",
                onBackClick = { navController.popBackStack() }
            )
        }
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
                items(messages) { message ->
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
                                    messagePendingDeletion = message.id
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

                    messagePendingDeletion?.let { messageId ->
                        AlertDialog(
                            onDismissRequest = { messagePendingDeletion = null },
                            title = { Text(stringResource(R.string.ui_delete_messages_title)) },
                            text = { Text(stringResource(R.string.ui_delete_messages_confirmation)) },
                            dismissButton = {
                                TextButton(onClick = { messagePendingDeletion = null }) {
                                    Text(stringResource(R.string.ui_cancel))
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        chatScreenController.deleteFromMessage(characterId, messageId)
                                        messagePendingDeletion = null
                                    }
                                ) {
                                    Text(stringResource(R.string.ui_delete))
                                }
                            }
                        )
                    }
                }
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
                Button(
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
