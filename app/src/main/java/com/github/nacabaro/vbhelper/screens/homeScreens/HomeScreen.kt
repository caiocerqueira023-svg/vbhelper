package com.github.nacabaro.vbhelper.screens.homeScreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.utils.DeviceType
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.homeScreens.screens.BEBEmHomeScreen
import com.github.nacabaro.vbhelper.screens.homeScreens.screens.BEDiMHomeScreen
import com.github.nacabaro.vbhelper.screens.homeScreens.screens.VBDiMHomeScreen
import com.github.nacabaro.vbhelper.screens.itemsScreen.ObtainedItemDialog
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.source.CardRepository
import com.github.nacabaro.vbhelper.source.DexRepository
import com.github.nacabaro.vbhelper.source.VitalWearCharacterExporter
import android.widget.Toast
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.screens.homeScreens.dialogs.DegenerateDialog
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexCharaDetailsDialog
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageScreenControllerImpl
import kotlinx.coroutines.flow.flowOf
import kotlin.collections.emptyList
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    homeScreenController: HomeScreenControllerImpl,
    storageScreenController: StorageScreenControllerImpl
) {
    val context = LocalContext.current
    val application = context.applicationContext as VBHelper

    val storageRepository = remember { StorageRepository(application.container.db) }
    val cardRepository = remember { CardRepository(application.container.db) }
    val dexRepository = remember { DexRepository(application.container.db) }

    val activeMon by storageRepository
        .getActiveCharacter()
        .collectAsState(initial = null)

    val allCharacters by storageRepository
        .getAllCharacters()
        .collectAsState(initial = emptyList())
    val favoriteCharacters = remember(allCharacters) {
        allCharacters
            .filter { it.isFavorite && !it.isInAdventure }
            .sortedBy { it.id }
    }
    val favoriteIndex = favoriteCharacters.indexOfFirst { it.id == activeMon?.id }
    var favoriteTransitionDirection by rememberSaveable { mutableStateOf(1) }
    var favoriteSwitchInFlight by remember { mutableStateOf(false) }
    var detailsCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detailsCharacterFlow = remember(detailsCharacterId) {
        detailsCharacterId?.let(dexRepository::getCharacterProgress) ?: flowOf(null)
    }
    val detailsCharacter by detailsCharacterFlow.collectAsState(initial = null)

    val cycleFavorite: (Int) -> Unit = { direction ->
        if (!favoriteSwitchInFlight && favoriteIndex >= 0 && favoriteCharacters.size > 1) {
            val nextIndex = (favoriteIndex + direction + favoriteCharacters.size) % favoriteCharacters.size
            val nextCharacter = favoriteCharacters[nextIndex]
            favoriteTransitionDirection = direction
            favoriteSwitchInFlight = true
            storageScreenController.setActive(
                characterId = nextCharacter.id,
                announce = false,
                onCompletion = { favoriteSwitchInFlight = false }
            )
        }
    }

    val latestReaction by (
        activeMon?.let { character ->
            application.container.db.userCharacterDao().getIndividualId(character.id)
                .flatMapLatest { individualId ->
                    application.container.chatRepository.getLatestUnreadAssistantMessage(individualId)
                }
        } ?: flowOf(null)
    ).collectAsState(initial = null)

    LaunchedEffect(activeMon?.id) {
        activeMon?.let { homeScreenController.checkDailyDiary(it.id) }
    }

    val cardIconData by (
        activeMon
            ?.let { chara ->
                cardRepository.getCardIconByCharaId(chara.charId)
            }
            ?: flowOf<CardDtos.CardIcon?>(null)
    ).collectAsState(initial = null)

    val speciesProfile by (
        activeMon
            ?.let { character ->
                application.container.db.speciesProfileDao()
                    .getByCardCharacterIdFlow(character.charId)
            }
            ?: flowOf<com.github.nacabaro.vbhelper.domain.species.SpeciesProfile?>(null)
    ).collectAsState(initial = null)

    val transformationHistory by (
        activeMon
            ?.let { chara ->
                storageRepository.getTransformationHistory(chara.id)
            }
            ?: flowOf(emptyList())
    ).collectAsState(initial = emptyList())

    val vbSpecialMissions by (
        activeMon
            ?.takeIf { it.characterType == DeviceType.VBDevice }
            ?.let { chara ->
                storageRepository.getSpecialMissions(chara.id)
            }
            ?: flowOf(emptyList())
    ).collectAsState(initial = emptyList())

    val vbData by (
        activeMon
            ?.takeIf { it.characterType == DeviceType.VBDevice }
            ?.let { chara ->
                storageRepository.getCharacterVbData(chara.id)
            }
            ?: flowOf<VBCharacterData?>(null)
    ).collectAsState(initial = null)

    val beData by (
        activeMon
            ?.takeIf { it.characterType == DeviceType.BEDevice }
            ?.let { chara ->
                storageRepository.getCharacterBeData(chara.id)
            }
            ?: flowOf<BECharacterData?>(null)
    ).collectAsState(initial = null)

    var adventureMissionsFinished by rememberSaveable { mutableStateOf(false) }
    var collectedItem by remember { mutableStateOf<ItemDtos.PurchasedItem?>(null) }
    var collectedCurrency by remember { mutableStateOf<Int?>(null) }
    var selectedTransformation by remember {
        mutableStateOf<CharacterDtos.TransformationHistory?>(null)
    }
    var vitalsHistory by remember {
        mutableStateOf<List<com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory>>(emptyList())
    }

    LaunchedEffect(activeMon?.id) {
        val character = activeMon
        vitalsHistory = if (character != null) {
            application.container.db.userCharacterDao().getVitalsHistory(character.id)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(true) {
        homeScreenController
            .didAdventureMissionsFinish {
                adventureMissionsFinished = it
            }
    }

    Scaffold (
        topBar = {
            TopBanner(
                text = stringResource(R.string.home_title),
                onScanClick = {
                    navController.navigate(NavigationItems.Scan.route)
                },
                onGearClick = {
                    navController.navigate(NavigationItems.Settings.route)
                }
            )
        }
    ) { contentPadding ->
        if (activeMon == null || (beData == null && vbData == null) || cardIconData == null || transformationHistory.isEmpty()) {
            Column (
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding())
            ) {
                Text(text = stringResource(R.string.adventure_empty_state))
            }
        } else {
            Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
                val cardIcon = BitmapData(
                    bitmap = cardIconData!!.cardIcon,
                    width = cardIconData!!.cardIconWidth,
                    height = cardIconData!!.cardIconHeight
                )

                if (activeMon!!.isBemCard && beData != null) {
                    BEBEmHomeScreen(
                        activeMon = activeMon!!,
                        beData = beData!!,
                        transformationHistory = transformationHistory,
                        nickname = activeMon!!.nickname,
                        contentPadding = PaddingValues(0.dp),
                        cardIcon = cardIcon,
                        speechBubbleText = latestReaction?.content,
                        onClickCharacter = {
                            navController.navigate(
                                NavigationItems.Chat.route.replace("{characterId}", activeMon!!.id.toString())
                            )
                        },
                        onLongClickCharacter = { detailsCharacterId = activeMon!!.charId },
                        onFavoriteSwipe = cycleFavorite,
                        favoriteTransitionDirection = favoriteTransitionDirection,
                        favoriteIndex = favoriteIndex,
                        favoriteCount = favoriteCharacters.size,
                        onClickTransformation = {
                            if (it.stageId != activeMon!!.charId && it.stage <= activeMon!!.stage) {
                                selectedTransformation = it
                            }
                        }
                    )
                } else if (!activeMon!!.isBemCard && activeMon!!.characterType == DeviceType.BEDevice && beData != null) {
                    BEDiMHomeScreen(
                        activeMon = activeMon!!,
                        beData = beData!!,
                        transformationHistory = transformationHistory,
                        nickname = activeMon!!.nickname,
                        contentPadding = PaddingValues(0.dp),
                        cardIcon = cardIcon,
                        speechBubbleText = latestReaction?.content,
                        onClickCharacter = {
                            navController.navigate(
                                NavigationItems.Chat.route.replace("{characterId}", activeMon!!.id.toString())
                            )
                        },
                        onLongClickCharacter = { detailsCharacterId = activeMon!!.charId },
                        onFavoriteSwipe = cycleFavorite,
                        favoriteTransitionDirection = favoriteTransitionDirection,
                        favoriteIndex = favoriteIndex,
                        favoriteCount = favoriteCharacters.size,
                        onClickTransformation = {
                            if (it.stageId != activeMon!!.charId && it.stage <= activeMon!!.stage) {
                                selectedTransformation = it
                            }
                        }
                    )
                } else if (vbData != null) {
                    VBDiMHomeScreen(
                        activeMon = activeMon!!,
                        vbData = vbData!!,
                        transformationHistory = transformationHistory,
                        nickname = activeMon!!.nickname,
                        speciesName = speciesProfile?.speciesName
                            ?.takeIf { it.isNotBlank() }
                            ?: speciesProfile?.matchedName?.takeIf { it.isNotBlank() },
                        contentPadding = PaddingValues(0.dp),
                        specialMissions = vbSpecialMissions,
                        homeScreenController = homeScreenController,
                        onClickCollect = { item, currency ->
                            collectedItem = item
                            collectedCurrency = currency
                        },
                        cardIcon = cardIcon,
                        speechBubbleText = latestReaction?.content,
                        onClickCharacter = {
                            navController.navigate(
                                NavigationItems.Chat.route.replace("{characterId}", activeMon!!.id.toString())
                            )
                        },
                        onLongClickCharacter = { detailsCharacterId = activeMon!!.charId },
                        onFavoriteSwipe = cycleFavorite,
                        favoriteTransitionDirection = favoriteTransitionDirection,
                        favoriteIndex = favoriteIndex,
                        favoriteCount = favoriteCharacters.size,
                        onClickTransformation = {
                            if (it.stageId != activeMon!!.charId && it.stage <= activeMon!!.stage) {
                                selectedTransformation = it
                            }
                        },
                        vitalsHistory = vitalsHistory
                    )
                }

                VitalButton(
                    onClick = {
                        try {
                            val intent = VitalWearCharacterExporter(application, application.container.db)
                                .buildShareIntent(activeMon!!.id)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            application.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                application,
                                "Could not send character to VitalWear: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = "Send to VitalWear")
                }
            }
        }
    }

    if (collectedItem != null) {
        ObtainedItemDialog(
            obtainedItem = collectedItem!!,
            obtainedCurrency = collectedCurrency!!,
            onClickDismiss = {
                collectedItem = null
                collectedCurrency = null
            }
        )
    }

    detailsCharacter?.let { character ->
        DexCharaDetailsDialog(
            currentChara = character,
            obscure = false,
            onClickClose = { detailsCharacterId = null },
            onClickCharacter = { detailsCharacterId = it }
        )
    }

    if (adventureMissionsFinished) {
        Dialog(
            onDismissRequest = { adventureMissionsFinished = false },
        ) {
            Card {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_adventure_mission_finished),
                        textAlign = TextAlign.Center
                    )
                    VitalButton(
                        onClick = {
                            adventureMissionsFinished = false
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.beta_warning_button_dismiss))
                    }
                }
            }
        }
    }

    val transformation = selectedTransformation
    val currentCharacter = activeMon
    if (transformation != null && currentCharacter != null) {
        DegenerateDialog(
                targetStage = transformation.stage,
            onDismiss = { selectedTransformation = null },
            onConfirm = {
                homeScreenController.degenerate(
                    characterId = currentCharacter.id,
                    transformation = transformation
                ) { result ->
                    selectedTransformation = null
                    result.onFailure {
                        Toast.makeText(
                            application,
                            it.message ?: "Could not degenerate this Digimon.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

}
