package com.github.nacabaro.vbhelper.screens.worldScreen

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner

@Composable
fun WorldChatScreen(
    navController: NavController,
    controller: WorldChatScreenControllerImpl,
    individualId: String,
    cardCharacterId: Long,
    speciesName: String
) {
    val context = LocalContext.current
    val messages by controller.getHistory(individualId).collectAsState(emptyList())
    val mood by controller.observeMood(individualId).collectAsState(initial = null)
    val isFollowing by controller.observeIsFollowing(individualId).collectAsState(initial = false)
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMessage by remember { mutableStateOf<com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity?>(null) }
    var eventDialog by remember { mutableStateOf<WildChatEvent?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = speciesName.ifBlank { stringResource(R.string.ui_world_wild_digimon) },
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            mood?.let { moodValue ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.ui_world_mood_label, moodValue),
                        style = MaterialTheme.typography.labelMedium
                    )
                    LinearProgressIndicator(
                        progress = { moodValue / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isFollowing == true) {
                        Text(
                            text = stringResource(R.string.ui_world_following_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

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
                                onLongClick = { selectedMessage = message }
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
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Digimon message", message.content))
                                    selectedMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.ui_copy)) }
                            if (message.role == "user") {
                                Button(
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
                            OutlinedButton(
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
                        controller.sendMessage(individualId, cardCharacterId, text) { result ->
                            sending = false
                            result.onSuccess { event ->
                                if (event !is WildChatEvent.None) eventDialog = event
                            }
                            result.onFailure { e -> error = e.message }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ui_send))
                }
            }
        }
    }

    eventDialog?.let { event ->
        val (title, body) = when (event) {
            is WildChatEvent.Recruited -> stringResource(R.string.ui_world_recruited_title) to event.message
            is WildChatEvent.Pending -> stringResource(R.string.ui_world_pending_title) to event.message
            is WildChatEvent.Vanished -> stringResource(R.string.ui_world_vanished_title) to event.message
            is WildChatEvent.StartedFollowing -> stringResource(R.string.ui_world_following_title) to event.message
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
                Button(onClick = {
                    eventDialog = null
                    when (event) {
                        is WildChatEvent.Recruited -> {
                            Toast.makeText(context, context.getString(R.string.ui_world_recruited_toast), Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                        is WildChatEvent.Vanished -> {
                            Toast.makeText(context, context.getString(R.string.ui_world_vanished_toast), Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                        is WildChatEvent.Pending -> {
                            Toast.makeText(context, context.getString(R.string.ui_world_pending_toast), Toast.LENGTH_LONG).show()
                        }
                        is WildChatEvent.StartedFollowing -> {
                            Toast.makeText(context, context.getString(R.string.ui_world_following_toast), Toast.LENGTH_LONG).show()
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
