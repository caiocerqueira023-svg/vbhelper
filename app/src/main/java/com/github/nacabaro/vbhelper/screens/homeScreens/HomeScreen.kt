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
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.Box
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.source.isMissingSecrets
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import com.github.nacabaro.vbhelper.components.showAppFeedback
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
    storageScreenController: StorageScreenControllerImpl,
    onImportCards: () -> Unit = { navController.navigate(NavigationItems.Dex.route) },
    onImportConnection: () -> Unit = { navController.navigate(NavigationItems.Settings.route) },
    importingConnection: Boolean = false,
) {
    val context = LocalContext.current
    val application = context.applicationContext as VBHelper

    val storageRepository = remember { StorageRepository(application.container.db) }
    val cardRepository = remember { CardRepository(application.container.db) }
    val dexRepository = remember { DexRepository(application.container.db) }

    var reload by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var collectionFailed by remember(reload) { mutableStateOf(false) }
    var cardsFailed by remember(reload) { mutableStateOf(false) }
    val collection by produceState(homeScreenController.cachedHomeCollection, storageRepository, reload) {
        try { storageRepository.getAllCharacters().collect { homeScreenController.cachedHomeCollection = it; value = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { collectionFailed = true }
    }
    val allCharacters = collection.orEmpty()
    val activeMon = allCharacters.firstOrNull { it.active }
    val cards by produceState(homeScreenController.cachedHomeCards, dexRepository, reload) {
        try { dexRepository.getAllDims().collect { homeScreenController.cachedHomeCards = it; value = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { cardsFailed = true }
    }
    val secrets by application.container.dataStoreSecretsRepository.secretsFlow.collectAsState(initial = null)
    val connectionReady = secrets?.let { !it.isMissingSecrets() }
    val preferences = remember { application.getSharedPreferences("app_preferences", 0) }
    var setupDismissed by rememberSaveable { mutableStateOf(preferences.getBoolean("home_setup_dismissed", false)) }
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

    val cachedDetails = homeScreenController.cachedPartnerDetails?.takeIf {
        it.characterId == activeMon?.id && it.cardCharacterId == activeMon?.charId
    }
    var profileLoading by remember(activeMon?.id, activeMon?.charId, reload) { mutableStateOf(activeMon != null && cachedDetails == null) }
    val partnerDetails by produceState(cachedDetails, activeMon?.id, activeMon?.charId, reload) {
        value = cachedDetails
        val partner = activeMon ?: return@produceState
        profileLoading = value == null
        try {
            val vb: Flow<VBCharacterData?> = if (partner.characterType == DeviceType.VBDevice)
                storageRepository.getCharacterVbData(partner.id) else flowOf(null)
            val be: Flow<BECharacterData?> = if (partner.characterType == DeviceType.BEDevice)
                storageRepository.getCharacterBeData(partner.id) else flowOf(null)
            combine(vb, be, cardRepository.getCardIconByCharaId(partner.charId)) {
                    vbData, beData, icon -> HomePartnerDetails(partner.id, partner.charId, vbData, beData, icon)
            }.collect { homeScreenController.cachedPartnerDetails = it; value = it; profileLoading = false }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
        finally { profileLoading = false }
    }
    val currentDetails = partnerDetails?.takeIf { it.characterId == activeMon?.id && it.cardCharacterId == activeMon?.charId }
    val cardIconData = currentDetails?.icon
    val vbData = currentDetails?.vbData
    val beData = currentDetails?.beData

    val speciesProfile by (
        activeMon
            ?.let { character ->
                application.container.db.speciesProfileDao()
                    .getByCardCharacterIdFlow(character.charId)
            }
            ?: flowOf<com.github.nacabaro.vbhelper.domain.species.SpeciesProfile?>(null)
    ).collectAsState(initial = null)

    val historyFlow = remember(storageRepository, activeMon?.id) {
        activeMon?.id?.let(storageRepository::getTransformationHistory) ?: flowOf(emptyList())
    }
    val transformationHistory by historyFlow.collectAsState(initial = emptyList())

    val vbSpecialMissions by (
        activeMon
            ?.takeIf { it.characterType == DeviceType.VBDevice }
            ?.let { chara ->
                storageRepository.getSpecialMissions(chara.id)
            }
            ?: flowOf(emptyList())
    ).collectAsState(initial = emptyList())

    var adventureMissionsFinished by rememberSaveable { mutableStateOf(false) }
    var collectedItem by remember { mutableStateOf<ItemDtos.PurchasedItem?>(null) }
    var collectedCurrency by remember { mutableStateOf<Int?>(null) }
    var selectedTransformationId by rememberSaveable(activeMon?.id) { mutableStateOf<Long?>(null) }
    val selectedTransformation = transformationHistory.firstOrNull { it.id == selectedTransformationId }
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
        val state = homeReadiness(collection?.size, cards?.size, connectionReady, activeMon != null,
            cardIconData != null && (beData != null || vbData != null))
        val contentModifier = Modifier.fillMaxSize().padding(contentPadding)
        val onRead = { navController.navigate(NavigationItems.Scan.route) }
        if (collectionFailed || (activeMon == null && cardsFailed)) {
            CyberEmptyState(stringResource(R.string.app_load_failed), contentModifier,
                actionLabel = stringResource(R.string.app_retry), onAction = { reload++ })
        } else if (state == HomeReadiness.LOADING || profileLoading) {
            HomeLoadingPanel(activeMon != null, contentModifier)
        } else if (state == HomeReadiness.SETUP && !setupDismissed) {
            HomeSetupPanel(cardsReady = !cards.isNullOrEmpty(), connectionReady = connectionReady == true,
                onImportCards = onImportCards, onImportConnection = onImportConnection, onRead = onRead,
                onSkip = { setupDismissed = true; preferences.edit().putBoolean("home_setup_dismissed", true).apply() },
                importingConnection = importingConnection,
                modifier = contentModifier)
        } else if (state == HomeReadiness.CHOOSE_PARTNER) {
            CyberEmptyState(stringResource(R.string.app_home_no_partner_body), contentModifier,
                title = stringResource(R.string.app_home_no_partner_title),
                actionLabel = stringResource(R.string.app_select_partner),
                onAction = { navController.navigate(NavigationItems.Storage.route) })
        } else if (state == HomeReadiness.PROFILE_UNAVAILABLE) {
            CyberEmptyState(stringResource(R.string.app_profile_unavailable_body), contentModifier,
                title = stringResource(R.string.app_profile_unavailable_title),
                actionLabel = stringResource(R.string.app_reload_details), onAction = { reload++ },
                secondaryLabel = stringResource(R.string.app_open_dex), onSecondaryAction = onImportCards)
        } else if (state == HomeReadiness.EMPTY || state == HomeReadiness.SETUP) {
            CyberEmptyState(stringResource(R.string.app_home_empty_body), contentModifier,
                title = stringResource(R.string.app_home_empty_title),
                actionLabel = stringResource(if (state == HomeReadiness.SETUP) R.string.app_setup_resume else R.string.app_scan_first),
                onAction = if (state == HomeReadiness.SETUP) ({ setupDismissed = false; preferences.edit().putBoolean("home_setup_dismissed", false).apply() }) else onRead,
                secondaryLabel = stringResource(R.string.app_open_dex),
                onSecondaryAction = { navController.navigate(NavigationItems.Dex.route) })
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                Box(Modifier.weight(1f)) {
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
                        speciesName = speciesProfile?.speciesName
                            ?.takeIf { it.isNotBlank() }
                            ?: speciesProfile?.matchedName?.takeIf { it.isNotBlank() },
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
                                selectedTransformationId = it.id
                            }
                        },
                        vitalsHistory = vitalsHistory
                    )
                } else if (!activeMon!!.isBemCard && activeMon!!.characterType == DeviceType.BEDevice && beData != null) {
                    BEDiMHomeScreen(
                        activeMon = activeMon!!,
                        beData = beData!!,
                        transformationHistory = transformationHistory,
                        nickname = activeMon!!.nickname,
                        speciesName = speciesProfile?.speciesName
                            ?.takeIf { it.isNotBlank() }
                            ?: speciesProfile?.matchedName?.takeIf { it.isNotBlank() },
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
                                selectedTransformationId = it.id
                            }
                        },
                        vitalsHistory = vitalsHistory
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
                                selectedTransformationId = it.id
                            }
                        },
                        vitalsHistory = vitalsHistory
                    )
                }

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
            onDismiss = { selectedTransformationId = null },
            onConfirm = {
                homeScreenController.degenerate(
                    characterId = currentCharacter.id,
                    transformation = transformation
                ) { result ->
                    selectedTransformationId = null
                    result.onFailure {
                        context.showAppFeedback(R.string.app_partner_update_failed, important = true)
                    }
                }
            }
        )
    }

}

internal data class HomePartnerDetails(
    val characterId: Long,
    val cardCharacterId: Long,
    val vbData: VBCharacterData?,
    val beData: BECharacterData?,
    val icon: CardDtos.CardIcon?,
)
