package com.tamerslab.navigation

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.navDeepLink
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.audio.AppMusicController
import com.tamerslab.battle.BattleAssetPaths
import com.tamerslab.components.motionEnabled
import com.tamerslab.battle.ui.BattlesScreen
import com.tamerslab.species.ui.card.CardsScreen
import com.tamerslab.species.ui.card.CardViewScreen
import com.tamerslab.species.ui.card.dialogs.CardOriginDialog
import com.tamerslab.chat.ui.ChatScreen
import com.tamerslab.chat.ui.ChatScreenControllerImpl
import com.tamerslab.home.ui.HomeScreen
import com.tamerslab.items.ui.ItemsScreen
import com.tamerslab.transfer.ui.scan.ScanScreen
import com.tamerslab.transfer.ui.scan.ScanScreenControllerImpl
import com.tamerslab.settings.ui.SettingsScreen
import com.tamerslab.species.ui.spriteviewer.SpriteViewer
import com.tamerslab.home.ui.HomeScreenControllerImpl
import com.tamerslab.storage.ui.StorageScreen
import com.tamerslab.transfer.ui.TransferScreen
import com.tamerslab.battle.ui.offline.TechniqueLoadoutScreen
import com.tamerslab.items.ui.ChooseCharacterScreen
import com.tamerslab.items.ui.ItemsScreenControllerImpl
import com.tamerslab.settings.ui.SettingsScreenControllerImpl
import com.tamerslab.adventure.ui.AdventureScreen
import com.tamerslab.adventure.ui.AdventureScreenControllerImpl
import com.tamerslab.species.ui.card.CardAdventureScreen
import com.tamerslab.species.ui.card.CardScreenControllerImpl
import com.tamerslab.settings.ui.CreditsScreen
import com.tamerslab.species.ui.spriteviewer.SpriteViewerControllerImpl
import com.tamerslab.storage.ui.StorageScreenControllerImpl
import com.tamerslab.storage.data.StorageRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.tamerslab.components.FeedbackDestination
import com.tamerslab.R
import com.tamerslab.chat.ui.lorebook.LorebookScreen
import com.tamerslab.chat.ui.lorebook.LorebookScreenControllerImpl
import com.tamerslab.farm.ui.home.FarmHomeScreen
import com.tamerslab.farm.ui.digifarm.FarmHomeWorld
import com.tamerslab.farm.ui.home.rememberFarmHomeShellState
import com.tamerslab.home.ui.PartnerDetent
import com.tamerslab.world.ui.WorldScreen
import com.tamerslab.world.ui.RadarPlaceScreen
import com.tamerslab.world.ui.WorldChatScreen
import com.tamerslab.world.ui.WorldConversationScreen
import com.tamerslab.world.ui.WorldChatScreenControllerImpl
import com.tamerslab.farm.ui.digiline.DigilineScreen
import com.tamerslab.farm.ui.digiline.FarmGroupScreen
import com.tamerslab.quests.ui.QuestScreen
import com.tamerslab.dtos.WorldDtos

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
    initialDestination: AppRoute? = null
) {
    val navController = rememberNavController()
    val settingsScreenController = applicationNavigationHandlers.settingsScreenController
    val pendingCardOriginPrompts by settingsScreenController.pendingCardOriginPrompts.collectAsStateWithLifecycle()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    // Home is a place, not a panel: it owns the whole window, draws its own insets and hides the
    // app chrome. Every other destination keeps the standard inset-boxed shell.
    val isFarmHome = currentDestination == null || currentDestination.hasRoute<AppRoute.Home>()
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
    val feedback = (context.applicationContext as TamersLabApplication).feedback
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

    LaunchedEffect(initialDestination) {
        val target = initialDestination ?: return@LaunchedEffect
        if (currentDestination?.hasRoute(target::class) != true) navController.navigate(target)
    }

    LaunchedEffect(currentDestination) {
        if (!currentDestination.isWorldOrBattle()) battleFullScreen = false
        if (currentDestination?.hasRoute<AppRoute.Battles>() != true) {
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
            startDestination = initialDestination ?: AppRoute.Home,
            enterTransition = {
                val direction = primaryTabTransitionDirection(
                    initialState.destination,
                    targetState.destination
                )
                val touchesFarmHome = initialState.destination.hasRoute<AppRoute.Home>() ||
                    targetState.destination.hasRoute<AppRoute.Home>()
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
                    initialState.destination,
                    targetState.destination
                )
                val touchesFarmHome = initialState.destination.hasRoute<AppRoute.Home>() ||
                    targetState.destination.hasRoute<AppRoute.Home>()
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
            composable<AppRoute.Battles>(deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "vbhelper://auth" }, androidx.navigation.navDeepLink { uriPattern = "http://localhost:8080/authenticate" }, androidx.navigation.navDeepLink { uriPattern = "http://127.0.0.1:8080/authenticate" })) {
                BattlesScreen(
                    musicController = musicController,
                    onFullScreenBattleChanged = { battleFullScreen = it }
                )
            }
            composable<AppRoute.Home> {
                val importingConnection by settingsScreenController.importingConnection.collectAsStateWithLifecycle()
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
            composable<AppRoute.World> { entry ->
                WorldScreen(
                    navController = navController,
                    worldEntry = entry,
                    onFullScreenBattleChanged = { battleFullScreen = it }
                )
            }
            composable<AppRoute.Radar> { entry ->
                RadarPlaceScreen(
                    navController = navController,
                    worldEntry = entry,
                    onFullScreenBattleChanged = { battleFullScreen = it }
                )
            }
            composable<AppRoute.Digiline> { DigilineScreen(navController) }
            composable<AppRoute.Quests> { QuestScreen(navController) }
            composable<AppRoute.FarmGroup> { entry ->
                FarmGroupScreen(navController, entry.toRoute<AppRoute.FarmGroup>().farmId)
            }
            composable<AppRoute.WorldConversation> { entry ->
                WorldConversationScreen(navController, entry.toRoute<AppRoute.WorldConversation>().interactionId)
            }
            composable<AppRoute.WildContact> { entry ->
                val route = entry.toRoute<AppRoute.WildContact>()
                val context = LocalContext.current.applicationContext as TamersLabApplication
                var speciesName by remember { mutableStateOf("") }
                LaunchedEffect(route.cardCharacterId) {
                    speciesName = context.container.db.speciesProfileDao()
                        .getByCardCharacterId(route.cardCharacterId)?.speciesName.orEmpty()
                }
                WorldChatScreen(
                    navController = navController,
                    controller = applicationNavigationHandlers.worldChatScreenController,
                    individualId = route.individualId,
                    cardCharacterId = route.cardCharacterId,
                    speciesName = speciesName
                )
            }
            composable<AppRoute.WorldRecruits> {
                com.tamerslab.world.ui.WorldRecruitsScreen(navController = navController)
            }
            composable<AppRoute.Storage> {
                StorageScreen(
                    navController = navController,
                    adventureScreenController = applicationNavigationHandlers.adventureScreenController,
                    storageScreenController = applicationNavigationHandlers.storageScreenController
                )
            }
            composable<AppRoute.Transfer> { entry ->
                val route = entry.toRoute<AppRoute.Transfer>()
                com.tamerslab.transfer.ui.TransferHubScreen(navController,
                    applicationNavigationHandlers.scanScreenController,
                    TransferNavigation.section(route.section),
                    route.characterId,
                    route.individualId)
            }
            composable<AppRoute.TechniqueLoadout> { entry ->
                TechniqueLoadoutScreen(
                    characterId = entry.toRoute<AppRoute.TechniqueLoadout>().characterId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable<AppRoute.Scan> { entry ->
                var characterId by remember { mutableStateOf(entry.toRoute<AppRoute.Scan>().characterId) }
                val launchedFromHomeScreen = characterId == null

                if (characterId == null) {
                    val context = LocalContext.current.applicationContext as TamersLabApplication
                    val storageRepository = remember { StorageRepository(context.container.db) }
                    val characterData by storageRepository.getActiveCharacter().collectAsStateWithLifecycle(null)
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
            composable<AppRoute.Dex> {
                val imports by settingsScreenController.cardImportState.collectAsStateWithLifecycle()
                CardsScreen(
                    navController = navController,
                    cardScreenController = applicationNavigationHandlers.cardScreenController,
                    onImportCards = settingsScreenController::onClickImportCardsFromDex,
                    importState = imports,
                    onStopImport = settingsScreenController::stopCardImport,
                    onDismissImportResult = settingsScreenController::dismissCardImportResult,
                )
            }
            composable<AppRoute.Settings> {
                SettingsScreen(
                    navController = navController,
                    settingsScreenController = applicationNavigationHandlers.settingsScreenController,
                    musicController = musicController
                )
            }
            composable<AppRoute.Lorebook> {
                LorebookScreen(
                    navController = navController,
                    controller = applicationNavigationHandlers.lorebookScreenController
                )
            }
            composable<AppRoute.Viewer> {
                SpriteViewer(
                    navController = navController,
                    spriteViewerController = applicationNavigationHandlers.spriteViewerController
                )
            }
            composable<AppRoute.CardView> { entry ->
                CardViewScreen(
                    navController = navController,
                    cardId = entry.toRoute<AppRoute.CardView>().cardId
                )
            }
            composable<AppRoute.Items> { entry ->
                ItemsScreen(
                    navController = navController,
                    initialTab = entry.toRoute<AppRoute.Items>().tab
                )
            }
            composable<AppRoute.ApplyItem> { entry ->
                ChooseCharacterScreen(
                    itemsScreenController = applicationNavigationHandlers.itemsScreenController,
                    navController = navController,
                    itemId = entry.toRoute<AppRoute.ApplyItem>().itemId
                )
            }
            composable<AppRoute.Adventure> {
                AdventureScreen(
                    navController = navController,
                    storageScreenController = applicationNavigationHandlers
                        .adventureScreenController
                )
            }
            composable<AppRoute.Credits> {
                CreditsScreen(
                    navController = navController
                )
            }
            composable<AppRoute.CardAdventure> { entry ->
                CardAdventureScreen(
                    navController = navController,
                    cardId = entry.toRoute<AppRoute.CardAdventure>().cardId,
                    cardScreenController = applicationNavigationHandlers.cardScreenController
                )
            }
            composable<AppRoute.Chat> { entry ->
                ChatScreen(
                    navController = navController,
                    chatScreenController = applicationNavigationHandlers.chatScreenController,
                    characterId = entry.toRoute<AppRoute.Chat>().characterId
                )
            }
            composable<AppRoute.WorldChat> { entry ->
                run {
                    val spawnId = entry.toRoute<AppRoute.WorldChat>().spawnId
                    val context = LocalContext.current.applicationContext as TamersLabApplication
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

private fun primaryTabTransitionDirection(initial: NavDestination, target: NavDestination): Int {
    val initialIndex = primaryDestinations.indexOfFirst { initial.hasRoute(it.route::class) }
    val targetIndex = primaryDestinations.indexOfFirst { target.hasRoute(it.route::class) }
    return if (initialIndex >= 0 && targetIndex >= 0) {
        (targetIndex - initialIndex).coerceIn(-1, 1)
    } else {
        0
    }
}

private fun NavDestination?.isWorldOrBattle(): Boolean = this != null &&
    (hasRoute<AppRoute.Battles>() || hasRoute<AppRoute.World>() || hasRoute<AppRoute.Radar>())












