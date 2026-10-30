package com.github.nacabaro.vbhelper.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.audio.AppMusicController
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.screens.BattlesScreen
import com.github.nacabaro.vbhelper.screens.cardScreen.CardsScreen
import com.github.nacabaro.vbhelper.screens.cardScreen.CardViewScreen
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.CardOriginDialog
import com.github.nacabaro.vbhelper.screens.chatScreen.ChatScreen
import com.github.nacabaro.vbhelper.screens.chatScreen.ChatScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.homeScreens.HomeScreen
import com.github.nacabaro.vbhelper.screens.itemsScreen.ItemsScreen
import com.github.nacabaro.vbhelper.screens.scanScreen.ScanScreen
import com.github.nacabaro.vbhelper.screens.scanScreen.ScanScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.settingsScreen.SettingsScreen
import com.github.nacabaro.vbhelper.screens.spriteViewer.SpriteViewer
import com.github.nacabaro.vbhelper.screens.homeScreens.HomeScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageScreen
import com.github.nacabaro.vbhelper.screens.transferScreen.TransferScreen
import com.github.nacabaro.vbhelper.screens.offlineBattle.TechniqueLoadoutScreen
import com.github.nacabaro.vbhelper.screens.itemsScreen.ChooseCharacterScreen
import com.github.nacabaro.vbhelper.screens.itemsScreen.ItemsScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.settingsScreen.SettingsScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.adventureScreen.AdventureScreen
import com.github.nacabaro.vbhelper.screens.adventureScreen.AdventureScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.cardScreen.CardAdventureScreen
import com.github.nacabaro.vbhelper.screens.cardScreen.CardScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.settingsScreen.CreditsScreen
import com.github.nacabaro.vbhelper.screens.spriteViewer.SpriteViewerControllerImpl
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageScreenControllerImpl
import com.github.nacabaro.vbhelper.source.StorageRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.github.nacabaro.vbhelper.components.FeedbackDestination
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.screens.lorebookScreen.LorebookScreen
import com.github.nacabaro.vbhelper.screens.lorebookScreen.LorebookScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.farmHome.FarmHomeScreen
import com.github.nacabaro.vbhelper.screens.digifarmScreen.FarmHomeWorld
import com.github.nacabaro.vbhelper.screens.farmHome.rememberFarmHomeShellState
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerDetent
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldScreen
import com.github.nacabaro.vbhelper.screens.worldScreen.RadarPlaceScreen
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldChatScreen
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldConversationScreen
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldChatScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.digilineScreen.DigilineScreen
import com.github.nacabaro.vbhelper.screens.digilineScreen.FarmGroupScreen
import com.github.nacabaro.vbhelper.screens.questScreen.QuestScreen
import com.github.nacabaro.vbhelper.dtos.WorldDtos

data class AppNavigationHandlers(
    val settingsScreenController: SettingsScreenControllerImpl,
    val scanScreenController: ScanScreenControllerImpl,
    val itemsScreenController: ItemsScreenControllerImpl,
    val adventureScreenController: AdventureScreenControllerImpl,
    val storageScreenController: StorageScreenControllerImpl,
    val homeScreenController: HomeScreenControllerImpl,
    val spriteViewerController: SpriteViewerControllerImpl,
    val cardScreenController: CardScreenControllerImpl,
    val chatScreenController: ChatScreenControllerImpl,
    val lorebookScreenController: LorebookScreenControllerImpl,
    val worldChatScreenController: WorldChatScreenControllerImpl
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AppNavigation(
    applicationNavigationHandlers: AppNavigationHandlers,
    initialRoute: String? = null
) {
    val navController = rememberNavController()
    val settingsScreenController = applicationNavigationHandlers.settingsScreenController
    val pendingCardOriginPrompts by settingsScreenController.pendingCardOriginPrompts.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // Home is a place, not a panel: it owns the whole window, draws its own insets and hides the
    // app chrome. Every other destination keeps the standard inset-boxed shell.
    val isFarmHome = currentRoute == null || currentRoute == NavigationItems.Home.route
    // The farm world is a shell-level surface: it stays composed (paused, invisible) while a place is
    // open, so returning to the farm never rebuilds the Filament engine or re-streams residents.
    val farmShell = rememberFarmHomeShellState()
    var battleFullScreen by remember { mutableStateOf(false) }
    val tabSwipeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val allowMotion = motionEnabled()
    val battleChromeMotionDuration = if (allowMotion) 240 else 0
    val battleChromeFadeDuration = if (allowMotion) 160 else 0
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedback = (context.applicationContext as VBHelper).feedback
    val snackbar = remember { SnackbarHostState() }
    val keyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(feedback, lifecycleOwner, resources) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            feedback.messages.collect { message ->
                val label = when (message.destination) {
                    FeedbackDestination.SETTINGS -> resources.getString(R.string.ui_settings)
                    FeedbackDestination.BATTLES -> resources.getString(R.string.nav_battle)
                    null -> null
                }
                val result = snackbar.showSnackbar(message.resolve(resources), actionLabel = label,
                    withDismissAction = message.important,
                    duration = if (message.important) SnackbarDuration.Long else SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) when (message.destination) {
                    FeedbackDestination.SETTINGS -> navController.navigatePrimary(NavigationItems.Settings)
                    FeedbackDestination.BATTLES -> navController.navigatePrimary(NavigationItems.Battles)
                    null -> Unit
                }
            }
        }
    }
    val musicController = remember { AppMusicController(context) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != NavigationItems.Battles.route && currentRoute != NavigationItems.World.route &&
            currentRoute != NavigationItems.Radar.route
        ) {
            battleFullScreen = false
        }
        if (currentRoute != NavigationItems.Battles.route) {
            musicController.play(BattleAssetPaths.HOME_MUSIC)
        }
    }

    DisposableEffect(lifecycleOwner, musicController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> musicController.setAppInForeground(true)
                Lifecycle.Event.ON_STOP -> musicController.setAppInForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        musicController.setAppInForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            musicController.release()
        }
    }

    BoxWithConstraints {
        val expandedNavigation = maxWidth >= 840.dp
        Scaffold(
            // Reserve system bars here and the remaining IME once in the content root. The farm home
            // is edge-to-edge: its HUD applies the status/navigation insets itself so the world can
            // bleed behind the system bars.
            contentWindowInsets = if (isFarmHome) WindowInsets(0) else
                WindowInsets.safeDrawing.exclude(WindowInsets.ime),
            snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
        ) { contentPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isFarmHome) Modifier
                        else Modifier.padding(contentPadding).consumeWindowInsets(contentPadding)
                    )
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                ) {
                // Persistent world surface: composed once for the shell's lifetime, paused and hidden
                // whenever a place is on top. This is what stops the farm from re-loading on every tab
                // change (the engine, island GLB and resident meshes all survive).
                FarmHomeWorld(
                    controller = farmShell.controller,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (isFarmHome) 1f else 0f },
                    isActive = isFarmHome && farmShell.detent != PartnerDetent.Expanded,
                    onResidentTap = { renderId -> farmShell.selectedResidentId = renderId },
                )
                NavHost(
            navController = navController,
            startDestination = initialRoute ?: NavigationItems.Home.route,
            enterTransition = {
                val direction = primaryTabTransitionDirection(
                    initialState.destination.route,
                    targetState.destination.route
                )
                val touchesFarmHome = initialState.destination.route == NavigationItems.Home.route ||
                    targetState.destination.route == NavigationItems.Home.route
                if (!allowMotion || direction == 0 || touchesFarmHome) {
                    // The world never slides sideways like a page; entering or leaving Home is a
                    // fade-through so the place reads as arriving, not as a tab switch.
                    fadeIn(animationSpec = tween(if (allowMotion) 200 else 80))
                } else {
                    slideInHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialOffsetX = { width -> if (direction > 0) width else -width }
                    ) + fadeIn(tween(170))
                }
            },
            exitTransition = {
                val direction = primaryTabTransitionDirection(
                    initialState.destination.route,
                    targetState.destination.route
                )
                val touchesFarmHome = initialState.destination.route == NavigationItems.Home.route ||
                    targetState.destination.route == NavigationItems.Home.route
                if (!allowMotion || direction == 0 || touchesFarmHome) {
                    fadeOut(animationSpec = tween(if (allowMotion) 140 else 60))
                } else {
                    slideOutHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        targetOffsetX = { width -> if (direction > 0) -width else width }
                    ) + fadeOut(tween(130))
                }
            },
            modifier = Modifier.fillMaxSize()

        ) {
            composable(NavigationItems.Battles.route) {
                BattlesScreen(
                    musicController = musicController,
                    onFullScreenBattleChanged = { battleFullScreen = it }
                )
            }
            composable(NavigationItems.Home.route) {
                val importingConnection by settingsScreenController.importingConnection.collectAsState()
                FarmHomeScreen(
                    navController = navController,
                    shell = farmShell,
                    homeScreenController = applicationNavigationHandlers.homeScreenController,
                    storageScreenController = applicationNavigationHandlers.storageScreenController,
                    importingConnection = importingConnection,
                    onImportCards = {
                        navController.navigatePrimary(NavigationItems.Dex)
                        settingsScreenController.onClickImportCardsFromDex()
                    },
                    onImportConnection = settingsScreenController::onClickImportApk,
                )
            }
            composable(NavigationItems.World.route) { entry ->
                WorldScreen(
                    navController = navController,
                    worldEntry = entry,
                    onFullScreenBattleChanged = { battleFullScreen = it }
                )
            }
            composable(NavigationItems.Radar.route) { entry ->
                RadarPlaceScreen(
                    navController = navController,
                    worldEntry = entry,
                    onFullScreenBattleChanged = { battleFullScreen = it }
                )
            }
            composable(NavigationItems.Digiline.route) { DigilineScreen(navController) }
            composable(NavigationItems.Quests.route) { QuestScreen(navController) }
            composable(NavigationItems.FarmGroup.route) { entry ->
                entry.arguments?.getString("farmId")?.let { FarmGroupScreen(navController, it) }
            }
            composable(NavigationItems.WorldConversation.route) { entry ->
                entry.arguments?.getString("interactionId")?.let { WorldConversationScreen(navController,it) }
            }
            composable(NavigationItems.WildContact.route) { entry ->
                val individualId = entry.arguments?.getString("individualId")
                val cardCharacterId = entry.arguments?.getString("cardCharacterId")?.toLongOrNull()
                if (individualId != null && cardCharacterId != null) {
                    val context = LocalContext.current.applicationContext as VBHelper
                    var speciesName by remember { mutableStateOf("") }
                    LaunchedEffect(cardCharacterId) {
                        speciesName = context.container.db.speciesProfileDao()
                            .getByCardCharacterId(cardCharacterId)?.speciesName.orEmpty()
                    }
                    WorldChatScreen(
                        navController = navController,
                        controller = applicationNavigationHandlers.worldChatScreenController,
                        individualId = individualId,
                        cardCharacterId = cardCharacterId,
                        speciesName = speciesName
                    )
                }
            }
            composable(NavigationItems.WorldRecruits.route) {
                com.github.nacabaro.vbhelper.screens.worldScreen.WorldRecruitsScreen(navController = navController)
            }
            composable(NavigationItems.Storage.route) {
                StorageScreen(
                    navController = navController,
                    adventureScreenController = applicationNavigationHandlers.adventureScreenController,
                    storageScreenController = applicationNavigationHandlers.storageScreenController
                )
            }
            composable(TransferNavigation.PATTERN, arguments = listOf(
                androidx.navigation.navArgument("section") { defaultValue = "saves" },
                androidx.navigation.navArgument("characterId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                androidx.navigation.navArgument("individualId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                com.github.nacabaro.vbhelper.screens.transferScreen.TransferHubScreen(navController,
                    applicationNavigationHandlers.scanScreenController,
                    TransferNavigation.section(entry.arguments?.getString("section")),
                    TransferNavigation.character(entry.arguments?.getString("characterId")),
                    TransferNavigation.individual(entry.arguments?.getString("individualId")))
            }
            composable(NavigationItems.TechniqueLoadout.route) { entry ->
                entry.arguments?.getString("characterId")?.toLongOrNull()?.let { characterId ->
                    TechniqueLoadoutScreen(
                        characterId = characterId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(NavigationItems.Scan.route) {
                val characterIdString = it.arguments?.getString("characterId")
                var characterId by remember { mutableStateOf(characterIdString?.toLongOrNull()) }
                val launchedFromHomeScreen = (characterIdString?.toLongOrNull() == null)

                if (characterId == null) {
                    val context = LocalContext.current.applicationContext as VBHelper
                    val storageRepository = remember { StorageRepository(context.container.db) }
                    val characterData by storageRepository.getActiveCharacter().collectAsState(null)
                    if (characterData != null) {
                        characterId = characterData!!.id
                    }
                }

                ScanScreen(
                    navController = navController,
                    scanScreenController = applicationNavigationHandlers.scanScreenController,
                    characterId = characterId,
                    launchedFromHomeScreen = launchedFromHomeScreen
                )
            }
            composable(NavigationItems.Dex.route) {
                val imports by settingsScreenController.cardImportState.collectAsState()
                CardsScreen(
                    navController = navController,
                    cardScreenController = applicationNavigationHandlers.cardScreenController,
                    onImportCards = settingsScreenController::onClickImportCardsFromDex,
                    importState = imports,
                    onStopImport = settingsScreenController::stopCardImport,
                    onDismissImportResult = settingsScreenController::dismissCardImportResult,
                )
            }
            composable(NavigationItems.Settings.route) {
                SettingsScreen(
                    navController = navController,
                    settingsScreenController = applicationNavigationHandlers.settingsScreenController,
                    musicController = musicController
                )
            }
            composable(NavigationItems.Lorebook.route) {
                LorebookScreen(
                    navController = navController,
                    controller = applicationNavigationHandlers.lorebookScreenController
                )
            }
            composable(NavigationItems.Viewer.route) {
                SpriteViewer(
                    navController = navController,
                    spriteViewerController = applicationNavigationHandlers.spriteViewerController
                )
            }
            composable(NavigationItems.CardView.route) {
                val cardId = it.arguments?.getString("cardId")
                if (cardId != null) {
                    CardViewScreen(
                        navController = navController,
                        cardId = cardId.toLong()
                    )
                }
            }
            composable(
                ItemsDestination.PATTERN,
                arguments = listOf(
                    androidx.navigation.navArgument(ItemsDestination.ARG_TAB) {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ItemsDestination.TAB_MINE
                    }
                )
            ) { entry ->
                ItemsScreen(
                    navController = navController,
                    initialTab = entry.arguments?.getString(ItemsDestination.ARG_TAB)
                        ?: ItemsDestination.TAB_MINE
                )
            }
            composable(NavigationItems.ApplyItem.route) {
                val itemId = it.arguments?.getString("itemId")
                if (itemId != null) {
                    ChooseCharacterScreen(
                        itemsScreenController = applicationNavigationHandlers
                            .itemsScreenController,
                        navController = navController,
                        itemId = itemId.toLong()
                    )
                }
            }
            composable(NavigationItems.Adventure.route) {
                AdventureScreen(
                    navController = navController,
                    storageScreenController = applicationNavigationHandlers
                        .adventureScreenController
                )
            }
            composable(NavigationItems.Credits.route) {
                CreditsScreen(
                    navController = navController
                )
            }
            composable(NavigationItems.CardAdventure.route) {
                val cardId = it.arguments?.getString("cardId")
                if (cardId != null) {
                    CardAdventureScreen(
                        navController = navController,
                        cardId = cardId.toLong(),
                        cardScreenController = applicationNavigationHandlers
                            .cardScreenController
                    )
                }
            }
            composable(NavigationItems.Chat.route) {
                val characterId = it.arguments?.getString("characterId")?.toLongOrNull()
                if (characterId != null) {
                    ChatScreen(
                        navController = navController,
                        chatScreenController = applicationNavigationHandlers.chatScreenController,
                        characterId = characterId
                    )
                }
            }
            composable(NavigationItems.WorldChat.route) {
                val spawnId = it.arguments?.getString("spawnId")?.toLongOrNull()
                if (spawnId != null) {
                    val context = LocalContext.current.applicationContext as VBHelper
                    var spawnDetails by remember { mutableStateOf<WorldDtos.SpawnWithDetails?>(null) }
                    LaunchedEffect(spawnId) {
                        spawnDetails = context.container.worldRepository.getSpawn(spawnId)
                    }
                    val details = spawnDetails
                    if (details != null) {
                        WorldChatScreen(
                            navController = navController,
                            controller = applicationNavigationHandlers.worldChatScreenController,
                            individualId = details.individualId,
                            cardCharacterId = details.cardCharacterId,
                            speciesName = details.speciesName ?: ""
                        )
                    } else {
                        Scaffold { padding ->
                            Box(
                                Modifier.padding(padding).fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
                }
                }
            }
        }
    }

    pendingCardOriginPrompts.firstOrNull { it.selectedStatus == null }?.let { prompt ->
        CardOriginDialog(
            cardName = prompt.cardName,
            isImporting = prompt.isImporting,
            onDismiss = { settingsScreenController.dismissPendingCardOriginPrompt(prompt.cardId) },
            onSelect = { status -> settingsScreenController.setPendingCardOrigin(prompt.cardId, status) }
        )
    }
}

private fun primaryTabTransitionDirection(initialRoute: String?, targetRoute: String?): Int {
    val initialIndex = primaryDestinations.indexOfFirst { it.route == initialRoute }
    val targetIndex = primaryDestinations.indexOfFirst { it.route == targetRoute }
    return if (initialIndex >= 0 && targetIndex >= 0) {
        (targetIndex - initialIndex).coerceIn(-1, 1)
    } else {
        0
    }
}
