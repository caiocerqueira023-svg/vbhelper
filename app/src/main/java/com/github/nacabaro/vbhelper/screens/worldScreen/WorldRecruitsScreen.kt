package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.quests.QuestCategory
import com.github.nacabaro.vbhelper.screens.digilineScreen.QuestJournal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recruitment invitations are contact-backed; a live radar spawn is not required. */
@Composable
fun WorldRecruitsScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as VBHelper
    val contacts by app.container.db.wildRelationshipDao().observeUnlocked().collectAsState(emptyList())
    val quests by app.container.questRepository.observeAll().collectAsState(emptyList())
    LaunchedEffect(contacts.map { it.individualId to it.trust }) {
        withContext(Dispatchers.IO) { contacts.forEach { app.container.questRepository.refreshGiver(it.individualId) } }
    }
    Scaffold(topBar = { TopBanner(text = stringResource(R.string.quest_recruitment), onBackClick = { navController.popBackStack() }) }) { padding ->
        QuestJournal(quests.filter { it.quest.category == QuestCategory.RECRUITMENT }, Modifier.padding(padding).fillMaxSize(),
            onOpen = { quest ->
                com.github.nacabaro.vbhelper.screens.questScreen.openQuestDetails(navController, quest.id)
            })
    }
}
