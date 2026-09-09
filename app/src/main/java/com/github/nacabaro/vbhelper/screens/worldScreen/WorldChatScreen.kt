package com.github.nacabaro.vbhelper.screens.worldScreen

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
import androidx.compose.material3.MaterialTheme
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
    val messages by controller.getHistory(individualId).collectAsState(emptyList())
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var messagePendingDeletion by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = speciesName.ifBlank { "Digimon selvagem" },
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
                                onLongClick = { messagePendingDeletion = message.id }
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
                                controller.deleteFromMessage(individualId, messageId)
                                messagePendingDeletion = null
                            }
                        ) {
                            Text(stringResource(R.string.ui_delete))
                        }
                    }
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
