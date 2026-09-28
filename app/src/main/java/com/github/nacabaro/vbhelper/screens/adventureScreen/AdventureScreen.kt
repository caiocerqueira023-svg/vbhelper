package com.github.nacabaro.vbhelper.screens.adventureScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.screens.itemsScreen.ObtainedItemDialog
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.utils.BitmapData
import kotlinx.coroutines.delay
import java.time.Instant
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberEmptyState

@Composable
fun AdventureScreen(
    navController: NavController,
    storageScreenController: AdventureScreenControllerImpl
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val database = application.container.db
    val storageRepository = remember { StorageRepository(database) }
    val characterList by storageRepository.getAdventureCharacters().collectAsState(emptyList())

    var obtainedItem by remember {
        mutableStateOf<ItemDtos.PurchasedItem?>(null)
    }
    var obtainedCurrency by remember {
        mutableStateOf(0)
    }

    val currentTime by produceState(
        initialValue = Instant.now().epochSecond,
        key1 = characterList.isNotEmpty()
    ) {
        if (characterList.isEmpty()) return@produceState
        while (true) {
            value = Instant.now().epochSecond
            delay(1000)
        }
    }

    var cancelAdventureDialog by remember {
        mutableStateOf<CharacterDtos.AdventureCharacterWithSprites?>(null)
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.adventure_title),
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    ) { contentPadding ->
        if (characterList.isEmpty()) {
            CyberEmptyState(
                message = stringResource(R.string.adventure_empty_state),
                modifier = Modifier.padding(contentPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(contentPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = characterList,
                    key = { it.id },
                    contentType = { "adventure-character" }
                ) {
                    AdventureEntry(
                        icon = BitmapData(
                            bitmap = it.spriteIdle,
                            width = it.spriteWidth,
                            height = it.spriteHeight
                        ),
                        stage = it.stage,
                        vitalPoints = it.vitalPoints,
                        timeLeft = it.finishesAdventure - currentTime,
                        onClick = {
                            if (it.finishesAdventure < currentTime) {
                                storageScreenController
                                    .getItemFromAdventure(it.id) { adventureResult, generatedCurrency ->
                                        obtainedItem = adventureResult
                                        obtainedCurrency = generatedCurrency
                                    }
                            } else {
                                cancelAdventureDialog = it
                            }
                        }
                    )
                }
            }
        }
    }

    if (obtainedItem != null) {
        ObtainedItemDialog(
            obtainedItem = obtainedItem!!,
            obtainedCurrency = obtainedCurrency,
            onClickDismiss = {
                obtainedItem = null
            }
        )
    }

    if (cancelAdventureDialog != null) {
        CancelAdventureDialog(
            characterSprite = BitmapData(
                bitmap = cancelAdventureDialog!!.spriteIdle,
                width = cancelAdventureDialog!!.spriteWidth,
                height = cancelAdventureDialog!!.spriteHeight
            ),
            onDismissRequest = {
                cancelAdventureDialog = null
            },
            onClickConfirm = {
                storageScreenController.cancelAdventure(cancelAdventureDialog!!.id) {
                    navController.navigate(NavigationItems.Storage.route)
                }
                cancelAdventureDialog = null
            }
        )
    }
}
