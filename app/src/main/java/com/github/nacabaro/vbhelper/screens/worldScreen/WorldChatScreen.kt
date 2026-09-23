package com.github.nacabaro.vbhelper.screens.worldScreen

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ChatComposer
import com.github.nacabaro.vbhelper.components.ChatHistoryPanel
import com.github.nacabaro.vbhelper.components.ChatMessageBubble
import com.github.nacabaro.vbhelper.components.ChatStatusPanel
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton

@Composable
fun WorldChatScreen(
    navController: NavController,
    controller: WorldChatScreenControllerImpl,
    individualId: String,
    cardCharacterId: Long,
    speciesName: String
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val messages by controller.getHistory(individualId).collectAsState(emptyList())
    val mood by controller.observeMood(individualId).collectAsState(initial = null)
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMessage by remember { mutableStateOf<com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity?>(null) }
    var eventDialog by remember { mutableStateOf<WildChatEvent?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(individualId) {
        // Entering the chat protects this encounter from distance-based eviction.
        controller.markInteracted(individualId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = speciesName.ifBlank { stringResource(R.string.ui_world_wild_digimon) },
                onBackClick = { navController.popBackStack() }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mood?.let { moodValue ->
                ChatStatusPanel(
                    label = stringResource(R.string.ui_world_mood_label, moodValue),
                    value = moodValue
                )
            }

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
                                    enabled = !sending,
                                    onClick = {
                                        sending = true
                                        selectedMessage = null
                                        controller.resendMessage(
                                            individualId,
                                            cardCharacterId,
                                            message.id,
                                            message.content
                                        ) { result ->
                                            sending = false
                                            result.onSuccess { event ->
                                                if (event !is WildChatEvent.None) eventDialog = event
                                            }
                                            result.onFailure { error = it.message }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.ui_resend)) }
                            }
                            VitalButton(
                                onClick = {
                                    controller.deleteFromMessage(individualId, message.id)
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
                errorMessage = error,
                onSend = {
                    val text = input
                    sending = true
                    error = null
                    controller.sendMessage(individualId, cardCharacterId, text) { result ->
                        sending = false
                        result.onSuccess { event ->
                            if (event !is WildChatEvent.None) eventDialog = event
                            if (input == text) input = ""
                        }
                        result.onFailure { error = it.message }
                    }
                }
            )
        }
    }

    eventDialog?.let { event ->
        val (title, body) = when (event) {
            is WildChatEvent.Recruited -> stringResource(R.string.ui_world_recruited_title) to event.message
            is WildChatEvent.Pending -> stringResource(R.string.ui_world_pending_title) to event.message
            is WildChatEvent.Vanished -> stringResource(R.string.ui_world_vanished_title) to event.message
            WildChatEvent.None -> return@let
        }
        AlertDialog(
            onDismissRequest = {
                eventDialog = null
                if (event is WildChatEvent.Recruited || event is WildChatEvent.Vanished) {
                    navController.popBackStack()
                }
            },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                VitalButton(onClick = {
                    eventDialog = null
                    when (event) {
                        is WildChatEvent.Recruited -> {
                            Toast.makeText(context, resources.getString(R.string.ui_world_recruited_toast), Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                        is WildChatEvent.Vanished -> {
                            Toast.makeText(context, resources.getString(R.string.ui_world_vanished_toast), Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                        is WildChatEvent.Pending -> {
                            Toast.makeText(context, resources.getString(R.string.ui_world_pending_toast), Toast.LENGTH_LONG).show()
                        }
                        WildChatEvent.None -> Unit
                    }
                }) {
                    Text(stringResource(R.string.ui_ok))
                }
            }
        )
    }
}
