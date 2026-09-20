package com.github.nacabaro.vbhelper.screens.digilineScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.digifarm.social.FarmConversationOrchestrator
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
    val orchestrator = remember { FarmConversationOrchestrator(repository, app.container.chatRepository) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var recipientsByMessage by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val dialogueUnavailable = stringResource(R.string.ui_digifarm_dialogue_unavailable)

    LaunchedEffect(messagesDesc.firstOrNull()?.sequence) {
        messagesDesc.firstOrNull()?.let { repository.markRead(farmId, it.sequence) }
        recipientsByMessage = withContext(Dispatchers.IO) {
            messagesDesc.associate { it.id to repository.recipientIds(it.id) }
        }
    }
    LaunchedEffect(farmId) {
        while (true) {
            runCatching { withContext(Dispatchers.IO) { repository.simulateStep(farmId) } }
            delay(900L)
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

    Scaffold(topBar = {
        TopBanner(text = farm?.name ?: stringResource(R.string.ui_digifarm_group), onBackClick = { navController.popBackStack() })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (residents.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(residents, key = { it.individualId }) { resident ->
                        Row(Modifier.clickable {
                            selected = if (resident.individualId in selected) selected - resident.individualId else selected + resident.individualId
                        }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(resident.individualId in selected, onCheckedChange = null)
                            Text(resident.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                items(messages, key = { it.id }) { message ->
                    val fromTamer = message.authorIndividualId == null
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromTamer) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (fromTamer) VitalCyan.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(.84f)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(message.authorNameSnapshot, style = MaterialTheme.typography.labelMedium, color = VitalCyan)
                                val recipientIds = recipientsByMessage[message.id].orEmpty()
                                val audience = if (recipientIds.isEmpty()) {
                                    stringResource(R.string.ui_digiline_message_all)
                                } else {
                                    val names = residents.filter { it.individualId in recipientIds }.joinToString { it.displayName }
                                    if (names.isBlank()) stringResource(R.string.ui_digiline_to_selected, recipientIds.size)
                                    else stringResource(R.string.ui_digiline_message_to, names)
                                }
                                Text(audience, style = MaterialTheme.typography.labelSmall)
                                Text(message.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(600) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (selected.isEmpty()) stringResource(R.string.ui_digiline_to_all) else stringResource(R.string.ui_digiline_to_selected, selected.size)) }
                )
                VitalButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isEmpty()) return@VitalButton
                        input = ""
                        sending = true
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching { orchestrator.sendTamerMessage(farmId, text, selected.toList()) }
                            withContext(Dispatchers.Main) {
                                sending = false
                                error = result.exceptionOrNull()?.message
                            }
                        }
                    },
                    enabled = !sending,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text(stringResource(R.string.ui_send)) }
            }
        }
    }
}
