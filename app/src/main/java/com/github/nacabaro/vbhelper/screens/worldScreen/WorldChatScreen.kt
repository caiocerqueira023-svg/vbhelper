package com.github.nacabaro.vbhelper.screens.worldScreen

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.nacabaro.vbhelper.di.VBHelper
import kotlinx.coroutines.*
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.ChatComposer
import com.github.nacabaro.vbhelper.components.ChatHistoryPanel
import com.github.nacabaro.vbhelper.components.ChatMessageBubble
import com.github.nacabaro.vbhelper.components.ChatStatusPanel
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.navigation.NavigationItems

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
    val app=context.applicationContext as VBHelper
    val world=app.container.worldEcosystemCoordinator
    val lifecycle=LocalLifecycleOwner.current
    val lease=remember(individualId) { "wild-chat:${java.util.UUID.randomUUID()}" }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var conversationReady by remember { mutableStateOf(false) }
    val messages by controller.getHistory(individualId).collectAsState(emptyList())
    val mood by controller.observeMood(individualId).collectAsState(initial = null)
    val quests by controller.observeQuests(individualId).collectAsState(emptyList())
    val questByOfferMessage = remember(quests) {
        quests.mapNotNull { details ->
            details.quest.offerMessageId?.let { it to details.quest.id }
        }.toMap()
    }
    // Battle-result context is prompt-only state; it is never exhibited as chat history.
    val visibleMessages = remember(messages) { messages.filter { it.role != "system" } }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMessage by remember { mutableStateOf<com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity?>(null) }
    var eventDialog by remember { mutableStateOf<WildChatEvent?>(null) }
    val listState = rememberLazyListState()
    var followingBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index==listState.layoutInfo.totalItemsCount-1 }
            .collect { followingBottom=it }
    }

    LaunchedEffect(individualId) {
        // Entering the chat protects this encounter from distance-based eviction.
        controller.markInteracted(individualId)
        controller.prepareQuests(individualId)
    }

    LaunchedEffect(individualId,lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var opened:String?=null
            try {
                withContext(Dispatchers.IO) { world.acquire(lease,autonomous=false) }
                opened=withContext(Dispatchers.IO) {
                    app.container.worldRepository.interactions.beginPrivateChat("private-surface:${java.util.UUID.randomUUID()}",individualId)?.id
                }
                conversationId=opened
                conversationReady=true
                launch {
                    try { withContext(Dispatchers.IO) {
                        app.container.chatRepository.reactToPendingBattles(individualId,cardCharacterId)
                        app.container.chatRepository.reactToPendingQuestOffers(individualId,cardCharacterId)
                    } }
                    catch(failure:Exception) {
                        if(failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                        error=failure.message
                    }
                }
                val activeConversation=opened
                if(activeConversation==null) awaitCancellation() else while(isActive) {
                    delay(20_000)
                    withContext(Dispatchers.IO) { app.container.worldRepository.interactions.renewPrivateChat(activeConversation,individualId) }
                }
            } catch(failure:Exception) {
                if(failure is CancellationException) throw failure
                error=if(failure is com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionException)
                    resources.getString(failure.messageResource()) else failure.message
            } finally {
                conversationReady=false
                conversationId=null
                withContext(NonCancellable+Dispatchers.IO) {
                    try { opened?.let { app.container.worldRepository.interactions.finishPrivateChat(it,individualId) } }
                    finally { world.refreshPopulation();world.release(lease) }
                }
            }
        }
    }

    LaunchedEffect(visibleMessages.size) {
        if (followingBottom && visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.size - 1)
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
                isEmpty = visibleMessages.isEmpty(),
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
                        items = visibleMessages,
                        key = { it.id },
                        contentType = { "chat-message" }
                    ) { message ->
                        val visibleText=if(message.role=="user") message.content else
                            com.github.nacabaro.vbhelper.chat.WorldDialogueCodec.visibleText(message.content,individualId)
                                ?: com.github.nacabaro.vbhelper.chat.WorldDialogueCodec.unreadableReply(com.github.nacabaro.vbhelper.chat.PromptLocalization.currentLanguageTag())
                        val offerQuestId = questByOfferMessage[message.id]
                        androidx.compose.foundation.layout.Column {
                            ChatMessageBubble(
                                text = visibleText,
                                isUser = message.role == "user",
                                onLongClick = { selectedMessage = message.copy(content=visibleText) }
                            )
                            if (offerQuestId != null) {
                                TextButton(
                                    onClick = { com.github.nacabaro.vbhelper.screens.questScreen.openQuestDetails(navController, offerQuestId) }
                                ) { Text(stringResource(R.string.quest_view_quest)) }
                            }
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
                                        controller.resendMessage(
                                            individualId,
                                            cardCharacterId,
                                            message.id,
                                            message.content,
                                            conversationId=conversationId
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
                enabled = conversationReady,
                errorMessage = error,
                onSend = {
                    val text = input
                    sending = true
                    error = null
                    controller.sendMessage(individualId, cardCharacterId, text,conversationId=conversationId) { result ->
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

    LaunchedEffect(eventDialog) {
        val attack=(eventDialog as? WildChatEvent.Challenge)?.takeIf { !it.sparring || it.accepted } ?: return@LaunchedEffect
        eventDialog=null
        if(runCatching { navController.getBackStackEntry(NavigationItems.World.route) }.getOrNull()==null) navController.navigate(NavigationItems.World.route)
        navController.getBackStackEntry(NavigationItems.World.route).savedStateHandle["radar-challenge"]=attack.id
        navController.popBackStack(NavigationItems.World.route,false)
    }
    eventDialog?.takeUnless { it is WildChatEvent.Challenge && (!it.sparring || it.accepted) }?.let { event ->
        val (title, body) = when (event) {
            is WildChatEvent.Recruited -> stringResource(R.string.ui_world_recruited_title) to event.message
            is WildChatEvent.Pending -> stringResource(R.string.ui_world_pending_title) to event.message
            is WildChatEvent.QuestUpdated -> stringResource(R.string.quest_title) to event.message
            is WildChatEvent.Vanished -> stringResource(R.string.ui_world_vanished_title) to event.message
            is WildChatEvent.Challenge -> stringResource(R.string.ui_world_accept_challenge) to event.message
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
                        is WildChatEvent.QuestUpdated -> Unit
                        is WildChatEvent.Challenge -> {
                            if(runCatching { navController.getBackStackEntry(NavigationItems.World.route) }.getOrNull()==null) {
                                navController.navigate(NavigationItems.World.route)
                            }
                            navController.getBackStackEntry(NavigationItems.World.route).savedStateHandle["radar-challenge"] = event.id
                            navController.popBackStack(NavigationItems.World.route,false)
                        }
                        WildChatEvent.None -> Unit
                    }
                }) {
                    Text(stringResource(if(event is WildChatEvent.Challenge)R.string.ui_world_accept_challenge else R.string.ui_ok))
                }
            }
            ,dismissButton={ if(event is WildChatEvent.Challenge) TextButton(onClick={controller.declineChallenge(event.id);eventDialog=null}) { Text(stringResource(R.string.ui_world_decline_challenge)) } }
        )
    }
}
