package com.tamerslab.world.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.TopBanner
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.quests.QuestCategory
import com.tamerslab.farm.ui.digiline.QuestJournal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recruitment invitations are contact-backed; a live radar spawn is not required. */
@Composable
fun WorldRecruitsScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as TamersLabApplication
    val contacts by app.container.db.wildRelationshipDao().observeUnlocked().collectAsStateWithLifecycle(emptyList())
    val quests by app.container.questRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(contacts.map { it.individualId to it.trust }) {
        withContext(Dispatchers.IO) { contacts.forEach { app.container.questRepository.refreshGiver(it.individualId) } }
    }
    Scaffold(topBar = { TopBanner(text = stringResource(R.string.quest_recruitment), onBackClick = { navController.popBackStack() }) }) { padding ->
        QuestJournal(quests.filter { it.quest.category == QuestCategory.RECRUITMENT }, Modifier.padding(padding).fillMaxSize(),
            onOpen = { quest ->
                com.tamerslab.quests.ui.openQuestDetails(navController, quest.id)
            })
    }
}

