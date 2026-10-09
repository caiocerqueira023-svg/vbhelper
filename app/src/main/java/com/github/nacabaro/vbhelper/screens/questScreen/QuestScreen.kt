package com.github.nacabaro.vbhelper.screens.questScreen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.navigation.navigatePrimary
import com.github.nacabaro.vbhelper.quests.QuestActionType
import com.github.nacabaro.vbhelper.quests.QuestInstance
import com.github.nacabaro.vbhelper.quests.QuestSteps
import com.github.nacabaro.vbhelper.screens.digilineScreen.QuestJournal
import com.github.nacabaro.vbhelper.screens.digilineScreen.QuestPartnerPicker
import com.github.nacabaro.vbhelper.screens.digilineScreen.trackQuestTarget
import com.github.nacabaro.vbhelper.screens.worldScreen.WildChatEvent
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldChatScreenControllerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the standalone Quests tab focused on one saved quest. */
fun openQuestDetails(navController: NavController, questId: String) {
    navController.navigatePrimary(NavigationItems.Quests)
    runCatching {
        navController.getBackStackEntry(NavigationItems.Quests.route)
            .savedStateHandle["quest-focus"] = questId
    }
}

/** Standalone quest journal. All accept/track/turn-in actions live here, not in chat. */
@Composable
fun QuestScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    val controller = remember { WorldChatScreenControllerImpl(context as ComponentActivity) }
    val quests by app.container.questRepository.observeAll().collectAsState(emptyList())
    val questPartners by controller.observeQuestPartners().collectAsState(emptyList())
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var partnerQuest by remember { mutableStateOf<QuestInstance?>(null) }
    var eventDialog by remember { mutableStateOf<WildChatEvent?>(null) }

    val questEntry = remember(navController) {
        runCatching { navController.getBackStackEntry(NavigationItems.Quests.route) }.getOrNull()
    }
    val focusId by (questEntry?.savedStateHandle?.getStateFlow<String?>("quest-focus", null)
        ?.collectAsState() ?: remember { mutableStateOf<String?>(null) })

    LaunchedEffect(quests.map { it.quest.giverId }.toSet()) {
        withContext(Dispatchers.IO) {
            quests.map { it.quest.giverId }.toSet().forEach { app.container.questRepository.refreshGiver(it) }
        }
    }

    fun performQuestAction(quest: QuestInstance, action: QuestActionType, partnerId: String? = null) {
        sending = true
        error = null
        controller.questAction(quest.giverId, quest.id, quest.revision, action, null, partnerId) { result ->
            sending = false
            result.onSuccess { if (it !is WildChatEvent.None) eventDialog = it }
            result.onFailure { error = it.message }
        }
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.quest_title),
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 4.dp)
        ) {
            if (focusId != null) {
                TextButton(
                    onClick = { questEntry?.savedStateHandle?.set("quest-focus", null) },
                    enabled = !sending
                ) { Text(stringResource(R.string.quest_show_all)) }
            }
            error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            QuestJournal(
                quests,
                Modifier.fillMaxSize(),
                busy = sending,
                focusQuestId = focusId,
                onOpen = { quest ->
                    navController.navigate(
                        NavigationItems.WildContact.route
                            .replace("{individualId}", android.net.Uri.encode(quest.giverId))
                            .replace("{cardCharacterId}", quest.giverCardCharacterId.toString())
                    )
                },
                onTrack = { trackQuestTarget(navController, it) },
                onConfigurePartner = { quest ->
                    questPartners.firstOrNull { it.individualId == quest.partnerId }?.let { partner ->
                        navController.navigate(
                            NavigationItems.TechniqueLoadout.route.replace("{characterId}", partner.characterId.toString())
                        )
                    }
                },
                onAction = { quest, action ->
                    val objectives = quests.firstOrNull { it.quest.id == quest.id }?.objectives.orEmpty()
                    if (action == QuestActionType.ACCEPT && QuestSteps.needsPartner(objectives)) {
                        partnerQuest = quest
                    } else {
                        performQuestAction(quest, action)
                    }
                }
            )
        }
    }

    partnerQuest?.let { quest ->
        QuestPartnerPicker(
            quest,
            quests.firstOrNull { it.quest.id == quest.id }?.objectives.orEmpty(),
            questPartners,
            onDismiss = { partnerQuest = null },
            onSelected = { partnerId ->
                partnerQuest = null
                performQuestAction(quest, QuestActionType.ACCEPT, partnerId)
            }
        )
    }

    (eventDialog as? WildChatEvent.Recruited)?.let { recruited ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { eventDialog = null },
            title = { Text(stringResource(R.string.quest_title)) },
            text = { Text(recruited.message) },
            confirmButton = {
                com.github.nacabaro.vbhelper.components.VitalButton(onClick = { eventDialog = null }) {
                    Text(stringResource(R.string.ui_ok))
                }
            },
            dismissButton = {}
        )
    }
}
