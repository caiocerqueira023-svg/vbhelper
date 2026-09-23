package com.github.nacabaro.vbhelper.screens.digilineScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ChatComposer
import com.github.nacabaro.vbhelper.components.ChatContextPanel
import com.github.nacabaro.vbhelper.components.ChatHistoryPanel
import com.github.nacabaro.vbhelper.components.ChatMessageBubble
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.digifarm.social.FarmConversationOrchestrator
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FarmGroupScreen(navController: NavController, farmId: String) {
    val app = LocalContext.current.applicationContext as VBHelper
    val repository = app.container.digifarmRepository
    val farms by repository.observeFarms().collectAsState(emptyList())
    val farm = farms.firstOrNull { it.id == farmId }
    val residents by repository.observeResidents(farmId).collectAsState(emptyList())
    val messagesDesc by repository.observeMessages(farmId).collectAsState(emptyList())
    val messages = messagesDesc.asReversed()
    val messageListState = rememberLazyListState()
    var previousMessageCount by remember(farmId) { mutableStateOf(0) }
    val orchestrator = remember { FarmConversationOrchestrator(repository, app.container.chatRepository) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var recipientsByMessage by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val dialogueUnavailable = stringResource(R.string.ui_digifarm_dialogue_unavailable)

    LaunchedEffect(farmId, messagesDesc.firstOrNull()?.id, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (previousMessageCount == 0) {
            messageListState.scrollToItem(messages.lastIndex)
        } else {
            val lastVisibleIndex = messageListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisibleIndex >= previousMessageCount - 1) {
                messageListState.animateScrollToItem(messages.lastIndex)
            }
        }
        previousMessageCount = messages.size
    }

    LaunchedEffect(messagesDesc.firstOrNull()?.sequence) {
        messagesDesc.firstOrNull()?.let { repository.markRead(farmId, it.sequence) }
        recipientsByMessage = withContext(Dispatchers.IO) {
            messagesDesc.associate { it.id to repository.recipientIds(it.id) }
        }
    }
    val coordinator = app.container.farmSessionCoordinator
    LaunchedEffect(farmId) {
        withContext(Dispatchers.IO) { coordinator.acquire(farmId) }
        try {
            while (true) delay(900L)
        } finally {
            withContext(Dispatchers.IO) { coordinator.release(farmId) }
        }
    }
    LaunchedEffect(farmId, residents.size, farm?.autonomousDialogueEnabled) {
        if (residents.size < 2 || farm?.autonomousDialogueEnabled != true) return@LaunchedEffect
        while (true) {
            delay(20_000L)
            val result = runCatching {
                withContext(Dispatchers.IO) { orchestrator.maybeGenerateAutonomous(farmId) }
            }
            if (result.isFailure) {
                error = dialogueUnavailable
                break
            }
        }
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = farm?.name ?: stringResource(R.string.ui_digifarm_group),
                onBackClick = { navController.popBackStack() }
            )
        },
        // MainApplication already reserves the bottom navigation area. Avoid
        // applying the system bottom inset a second time on this nested screen,
        // while retaining the top inset for the status bar.
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (residents.isNotEmpty()) {
                ChatContextPanel {
                    Text(
                        stringResource(R.string.ui_digifarm_residents),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondaryOnDark
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(residents, key = { it.individualId }) { resident ->
                            val isSelected = resident.individualId in selected
                            VitalButton(
                                onClick = {
                                    selected = if (isSelected) {
                                        selected - resident.individualId
                                    } else {
                                        selected + resident.individualId
                                    }
                                },
                                modifier = Modifier.height(48.dp),
                                borderColor = if (isSelected) VitalCyan else SurfaceStroke,
                                contentColor = if (isSelected) VitalCyan else TextPrimaryOnDark,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    resident.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            ChatHistoryPanel(
                isEmpty = messagesDesc.isEmpty(),
                emptyMessage = stringResource(R.string.ui_digiline_empty_group),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    state = messageListState
                ) {
                    items(messages, key = { it.id }) { message ->
                        val fromTamer = message.authorIndividualId == null
                        val recipientIds = recipientsByMessage[message.id].orEmpty()
                        val audience = if (recipientIds.isEmpty()) {
                            stringResource(R.string.ui_digiline_message_all)
                        } else {
                            val names = residents.filter { it.individualId in recipientIds }.joinToString { it.displayName }
                            if (names.isBlank()) stringResource(R.string.ui_digiline_to_selected, recipientIds.size)
                            else stringResource(R.string.ui_digiline_message_to, names)
                        }
                        ChatMessageBubble(
                            text = message.body,
                            isUser = fromTamer,
                            authorLabel = message.authorNameSnapshot,
                            contextLabel = audience
                        )
                    }
                }
            }
            ChatComposer(
                value = input,
                onValueChange = { input = it },
                placeholder = if (selected.isEmpty()) {
                    stringResource(R.string.ui_digiline_to_all)
                } else {
                    stringResource(R.string.ui_digiline_to_selected, selected.size)
                },
                sendLabel = stringResource(R.string.ui_send),
                sending = sending,
                singleLine = true,
                maxLength = 600,
                errorMessage = error,
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        sending = true
                        error = null
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching { orchestrator.sendTamerMessage(farmId, text, selected.toList()) }
                            withContext(Dispatchers.Main) {
                                sending = false
                                result.onSuccess {
                                    if (input.trim() == text) input = ""
                                }
                                error = result.exceptionOrNull()?.message
                            }
                        }
                    }
                }
            )
        }
    }
}
