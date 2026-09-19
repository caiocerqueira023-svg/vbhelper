package com.github.nacabaro.vbhelper.screens.worldScreen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.world.WorldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WorldRecruitsScreen(navController: NavController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as VBHelper
    val worldRepository: WorldRepository = app.container.worldRepository
    val chatRepository: ChatRepository = app.container.chatRepository
    val scope = rememberCoroutineScope()

    val recruits by worldRepository.observePendingRecruits().collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<WorldDtos.SpawnWithDetails?>(null) }
    var requirementsMet by remember { mutableStateOf(false) }
    var isRecruiting by remember { mutableStateOf(false) }

    LaunchedEffect(selected) {
        selected?.let {
            requirementsMet = withContext(Dispatchers.IO) { worldRepository.meetsRecruitmentRequirements() }
        }
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.ui_world_recruits_title),
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { contentPadding ->
        if (recruits.isEmpty()) {
            Column(
                Modifier.padding(contentPadding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.ui_world_recruits_empty))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = contentPadding
            ) {
                items(
                    items = recruits,
                    key = { it.id },
                    contentType = { "world-recruit" }
                ) { spawn ->
                    CharacterEntry(
                        icon = BitmapData(
                            bitmap = spawn.spriteIdle,
                            width = spawn.spriteWidth,
                            height = spawn.spriteHeight
                        ),
                        statusText = spawn.speciesName,
                        onClick = { selected = spawn }
                    )
                }
            }
        }
    }

    selected?.let { spawn ->
        AlertDialog(
            onDismissRequest = { if (!isRecruiting) selected = null },
            title = { Text(spawn.speciesName ?: stringResource(R.string.ui_world_wild_digimon)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.ui_world_recruit_requirement_vitals,
                            WorldRepository.RECRUIT_VITALS_REQUIREMENT
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (requirementsMet) {
                            stringResource(R.string.ui_world_recruit_requirements_met)
                        } else {
                            stringResource(R.string.ui_world_recruit_requirements_not_met)
                        },
                        color = if (requirementsMet) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            },
            confirmButton = {
                VitalButton(
                    enabled = requirementsMet && !isRecruiting,
                    onClick = {
                        isRecruiting = true
                        scope.launch(Dispatchers.IO) {
                            val languageTag = PromptLocalization.currentLanguageTag()
                            val recruitment = runCatching {
                                worldRepository.recruitSpawn(spawn.id).getOrThrow()
                            }
                            if (recruitment.isSuccess) {
                                // The recruit is already in Storage. Its reaction is optional and must not
                                // hold up the action when the chat service is slow or unavailable.
                                launch {
                                    runCatching {
                                        chatRepository.triggerReactionForWildEncounter(
                                            spawn.individualId,
                                            spawn.cardCharacterId,
                                            PromptLocalization.wildRecruitConfirmedInstruction(languageTag)
                                        )
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                isRecruiting = false
                                selected = null
                                recruitment.onSuccess {
                                    Toast.makeText(context, resources.getString(R.string.ui_world_recruited_toast), Toast.LENGTH_LONG).show()
                                }
                                recruitment.onFailure {
                                    Toast.makeText(context, it.message ?: resources.getString(R.string.ui_unknown_error), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ui_world_recruit_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isRecruiting) selected = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
}
