package com.tamerslab.adventure.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.CollectionRefreshNotice
import com.tamerslab.components.CollectionStatePanel
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalSectionBar
import com.tamerslab.components.rememberCollectionLoad
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.items.ui.ObtainedItemDialog
import com.tamerslab.navigation.AppRoute
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.utils.BitmapData
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun AdventureScreen(
    navController: NavController,
    storageScreenController: AdventureScreenControllerImpl
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val context = LocalContext.current
    val database = application.container.db
    val storageRepository = remember { application.container.storageRepository }
    val partnersLoad = rememberCollectionLoad("adventure-partners", storageRepository::getAllCharacters)
    val activeLoad = rememberCollectionLoad("adventure-active", storageRepository::getAdventureCharacters)
    val partners = partnersLoad.data.orEmpty()
    val activeAdventures = activeLoad.data.orEmpty()
    val finishesById = remember(activeAdventures) {
        activeAdventures.associate { it.id to it.finishesAdventure }
    }

    val currentTime by produceState(
        initialValue = Instant.now().epochSecond,
        key1 = finishesById,
    ) {
        if (finishesById.isEmpty()) return@produceState
        while (true) {
            value = Instant.now().epochSecond
            delay(1_000)
        }
    }

    val selectedIds = remember { mutableStateListOf<Long>() }
    var showDepartCheck by remember { mutableStateOf(false) }
    var showWithdrawCheck by remember { mutableStateOf(false) }
    var obtainedItem by remember { mutableStateOf<ItemDtos.PurchasedItem?>(null) }
    var obtainedCurrency by remember { mutableStateOf(0) }
    var cancelTarget by remember { mutableStateOf<CharacterDtos.CharacterWithSprites?>(null) }

    // A partner that leaves or is removed stops being a valid pending selection.
    LaunchedEffect(partners) {
        val selectable = partners.filter { !it.isInAdventure }.map { it.id }.toSet()
        selectedIds.removeAll { it !in selectable }
    }

    val running = remember(partners, finishesById) {
        partners.filter { finishesById.containsKey(it.id) }
            .sortedBy { finishesById[it.id] ?: Long.MAX_VALUE }
    }
    val runningOrder = remember(running) {
        running.mapIndexed { index, partner -> partner.id to index + 1 }.toMap()
    }
    val sortedPartners = remember(partners, finishesById) {
        partners.sortedWith { left, right ->
            val leftFinish = finishesById[left.id]
            val rightFinish = finishesById[right.id]
            when {
                leftFinish != null && rightFinish != null -> leftFinish.compareTo(rightFinish)
                leftFinish != null -> -1
                rightFinish != null -> 1
                else -> left.id.compareTo(right.id)
            }
        }
    }
    val readyCount = running.count { (finishesById[it.id] ?: Long.MAX_VALUE) <= currentTime }
    val firstReadyId = running.firstOrNull { (finishesById[it.id] ?: Long.MAX_VALUE) <= currentTime }?.id
    val nearestRemaining = running.asSequence()
        .mapNotNull { partner -> finishesById[partner.id]?.takeIf { it > currentTime }?.minus(currentTime) }
        .minOrNull() ?: 0L
    val timerText = formatClock(if (running.isEmpty()) 0L else nearestRemaining)

    fun claim(characterId: Long) {
        storageScreenController.getItemFromAdventure(characterId) { item, currency ->
            obtainedItem = item
            obtainedCurrency = currency
        }
    }

    val action = when {
        firstReadyId != null -> AdventureAction.COLLECT
        selectedIds.isNotEmpty() -> AdventureAction.DEPART
        running.isNotEmpty() -> AdventureAction.WITHDRAW
        else -> AdventureAction.DEPART
    }
    val actionEnabled = action != AdventureAction.DEPART || selectedIds.isNotEmpty()
    val actionHint = when {
        firstReadyId != null -> stringResource(R.string.adventure_collect_reward)
        selectedIds.isNotEmpty() -> stringResource(R.string.adventure_selected_count, selectedIds.size)
        running.isNotEmpty() -> null
        else -> stringResource(R.string.adventure_select_hint, AdventureMaxTeamSize)
    }
    val sectionDetail = when {
        firstReadyId != null -> stringResource(R.string.adventure_scene_ready_count, readyCount)
        selectedIds.isNotEmpty() -> stringResource(R.string.adventure_selected_count, selectedIds.size)
        else -> null
    }
    // Resolved during composition: the click handler below may run after the composition is gone,
    // and lint rejects querying resources from LocalContext inside it.
    val teamFullMessage = stringResource(R.string.adventure_team_full, AdventureMaxTeamSize)

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.adventure_title),
                onBackClick = { navController.popBackStack() },
            )
        }
    ) { contentPadding ->
        when {
            partnersLoad.blocking -> CollectionStatePanel(partnersLoad, Modifier.padding(contentPadding))
            partners.isEmpty() -> CyberEmptyState(
                message = stringResource(R.string.adventure_empty_state),
                modifier = Modifier.padding(contentPadding),
                actionLabel = stringResource(R.string.adventure_empty_action),
                onAction = { navController.navigate(AppRoute.Storage) },
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                CollectionRefreshNotice(
                    partnersLoad,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                AdventureViewport(
                    runners = running,
                    running = running.isNotEmpty(),
                    timerText = timerText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .aspectRatio(AdventureViewportAspect),
                )
                VitalSectionBar(
                    title = stringResource(R.string.adventure_character_list),
                    detail = sectionDetail,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedPartners, key = { it.id }) { partner ->
                        val finish = finishesById[partner.id]
                        val state = when {
                            finish != null -> PartnerStageState.Away(
                                order = runningOrder[partner.id] ?: 1,
                                remainingSeconds = (finish - currentTime).coerceAtLeast(0L),
                                ready = finish <= currentTime,
                            )
                            else -> {
                                val order = selectedIds.indexOf(partner.id)
                                if (order >= 0) PartnerStageState.Selected(order + 1) else PartnerStageState.Idle
                            }
                        }
                        AdventurePartnerSlot(
                            partner = partner,
                            state = state,
                            onClick = {
                                if (finish != null) {
                                    if (finish <= currentTime) claim(partner.id) else cancelTarget = partner
                                } else if (selectedIds.contains(partner.id)) {
                                    selectedIds.remove(partner.id)
                                } else if (selectedIds.size < AdventureMaxTeamSize) {
                                    selectedIds.add(partner.id)
                                } else {
                                    Toast.makeText(
                                        context,
                                        teamFullMessage,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                AdventureActionBar(
                    action = action,
                    enabled = actionEnabled,
                    hint = actionHint,
                    onAction = {
                        when (action) {
                            AdventureAction.COLLECT -> firstReadyId?.let(::claim)
                            AdventureAction.DEPART -> if (selectedIds.isNotEmpty()) showDepartCheck = true
                            AdventureAction.WITHDRAW -> showWithdrawCheck = true
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (obtainedItem != null) {
        ObtainedItemDialog(
            obtainedItem = obtainedItem!!,
            obtainedCurrency = obtainedCurrency,
            onClickDismiss = { obtainedItem = null },
        )
    }

    cancelTarget?.let { partner ->
        CancelAdventureDialog(
            characterSprite = BitmapData(
                bitmap = partner.spriteIdle,
                width = partner.spriteWidth,
                height = partner.spriteHeight,
            ),
            onDismissRequest = { cancelTarget = null },
            onClickConfirm = {
                storageScreenController.cancelAdventure(partner.id) { }
                cancelTarget = null
            },
        )
    }

    if (showDepartCheck) {
        val team = selectedIds.mapNotNull { id -> partners.firstOrNull { it.id == id } }
        AdventureDepartDialog(
            team = team,
            onConfirm = { minutes ->
                selectedIds.toList().forEach { characterId ->
                    storageScreenController.sendCharacterToAdventure(characterId, minutes)
                }
                selectedIds.clear()
                showDepartCheck = false
            },
            onDismissRequest = { showDepartCheck = false },
        )
    }

    if (showWithdrawCheck) {
        AdventureWithdrawDialog(
            count = running.size,
            onConfirm = {
                storageScreenController.withdrawAdventures(running.map { it.id }) { }
                showWithdrawCheck = false
            },
            onDismissRequest = { showWithdrawCheck = false },
        )
    }
}

