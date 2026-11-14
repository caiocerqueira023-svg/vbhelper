package com.tamerslab.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import com.tamerslab.quests.ui.QuestScreen
import com.tamerslab.dtos.WorldDtos
import com.tamerslab.components.RouteArrival
import kotlinx.coroutines.delay

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
    // Stable for the shell's lifetime, so the world subtree can skip when only the route changes.
    val onFarmResidentTap = remember(farmShell) {
        { renderId: String -> farmShell.selectedResidentId = renderId }
    }
    var battleFullScreen by remember { mutableStateOf(false) }
    val tabSwipeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val allowMotion = motionEnabled()
    // The farm is not a destination, so a route transition cannot move it: it is the depth layer the
    // places sit over. Destination pages are transparent over the global device pattern, so while the
    // change runs the farm *is* the arriving page's background - it crossfades with the page instead
    // of being cut, or the background swaps abruptly the moment the transition ends.
    val farmAlpha = remember { Animatable(if (isFarmHome) 1f else 0f) }
    LaunchedEffect(isFarmHome) {
        if (!allowMotion) {
            farmAlpha.snapTo(if (isFarmHome) 1f else 0f)
        } else {
            farmAlpha.animateTo(
                targetValue = if (isFarmHome) 1f else 0f,
                animationSpec = tween(durationMillis = FARM_BACKGROUND_FADE_MS, easing = FastOutSlowInEasing),
            )
        }
    }
    val farmDepth by animateFloatAsState(
        targetValue = if (allowMotion && !isFarmHome) FARM_DEPTH else 1f,
        animationSpec = tween(
            durationMillis = if (!allowMotion) 0 else if (isFarmHome) 360 else 280,
            easing = FastOutSlowInEasing,
        ),
        label = "farmDepth",
    )
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

    // Tell content that arrives with this route change not to stack its own entrance on top of the
    // page transition. Raised as the destination's effects start - before the items that arrive with
    // its data compose - and lowered once the enter motion has settled. Not Compose state on purpose:
    // nothing subscribes to it, and settling it never costs a recomposition wave.
    LaunchedEffect(currentDestination) {
        RouteArrival.markInFlight()
        delay(if (allowMotion) ROUTE_ARRIVAL_MS else 0L)
        RouteArrival.settle()
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
            // Two independent layers. The world keeps the shell's whole window for the shell's whole
            // life: it is deliberately not padded with the system-bar insets, so entering or leaving
            // Home never resizes or re-lays out the surface that owns the Filament swapchain - that
            // resize used to land in the same frames as the route transition. Only the NavHost takes
            // the insets and the IME.
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = farmAlpha.value
                            scaleX = farmDepth
                            scaleY = farmDepth
                        }
                ) {
                    // Persistent world surface: composed once for the shell's lifetime, paused and
                    // hidden whenever a place is on top. This is what stops the farm from re-loading
                    // on every tab change (the engine, island GLB and resident meshes all survive).
                    // The depth transform sits on the layer above, so it invalidates draw only.
                    FarmHomeWorld(
                        controller = farmShell.controller,
                        modifier = Modifier.fillMaxSize(),
                        isActive = isFarmHome && farmShell.detent != PartnerDetent.Expanded,
                        onResidentTap = onFarmResidentTap,
                    )
                }
                NavHost(
            navController = navController,
            startDestination = initialDestination ?: AppRoute.Home,
            enterTransition = {
                val direction = primaryTabTransitionDirection(
                    initialState.destination,
                    targetState.destination
                )
                when (
                    routeEnterMotion(
                        initialIsHome = initialState.destination.hasRoute<AppRoute.Home>(),
                        targetIsHome = targetState.destination.hasRoute<AppRoute.Home>(),
                        direction = direction,
                        motionAllowed = allowMotion,
                    )
                ) {
                    RouteEnterMotion.HomeReturn ->
                        // The farm is already standing underneath and settling forward; the interface
                        // lands a beat later, so arrival reads as coming back to the world rather than
                        // as another page fading in.
                        fadeIn(animationSpec = tween(durationMillis = 240, delayMillis = 40))
                    RouteEnterMotion.PlaceOverFarm ->
                        // A place rises as it dissolves over the farm. There is no sideways slide:
                        // Home is a place, not the first tab of a pager.
                        fadeIn(animationSpec = tween(220)) + slideInVertically(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetY = { height -> (height * 0.022f).toInt() }
                        )
                    RouteEnterMotion.TabSlide ->
                        slideInHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> if (direction > 0) width else -width }
                        ) + fadeIn(tween(170))
                    RouteEnterMotion.Fade ->
                        fadeIn(animationSpec = tween(if (allowMotion) 200 else 90))
                }
            },
            exitTransition = {
                val direction = primaryTabTransitionDirection(
                    initialState.destination,
                    targetState.destination
                )
                when (
                    routeExitMotion(
                        initialIsHome = initialState.destination.hasRoute<AppRoute.Home>(),
                        targetIsHome = targetState.destination.hasRoute<AppRoute.Home>(),
                        direction = direction,
                        motionAllowed = allowMotion,
                    )
                ) {
                    RouteExitMotion.HomeLeave ->
                        // Home's chrome clears out quickly; the world itself stays composited under
                        // the arriving place and is dropped once that place covers it.
                        fadeOut(animationSpec = tween(150))
                    RouteExitMotion.PlaceSink ->
                        // The place sinks and dissolves so the farm underneath is what survives the
                        // change, and the world's own settle carries the arrival.
                        fadeOut(animationSpec = tween(220)) + slideOutVertically(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                            targetOffsetY = { height -> (height * 0.018f).toInt() }
                        )
                    RouteExitMotion.TabSlide ->
                        slideOutHorizontally(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            targetOffsetX = { width -> if (direction > 0) -width else width }
                        ) + fadeOut(tween(130))
                    RouteExitMotion.Fade ->
                        fadeOut(animationSpec = tween(if (allowMotion) 140 else 80))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFarmHome) Modifier
                    else Modifier.padding(contentPadding).consumeWindowInsets(contentPadding)
                )
                .imePadding()

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

/**
 * Which motion a route change uses. Home is a full-bleed world rather than a page, so it never rides
 * the sideways tab slide: leaving, the place lifts over the farm while the world pushes in under it;
 * returning, the place sinks away and hands the screen back to the farm, which has settled forward.
 */
internal enum class RouteEnterMotion { HomeReturn, PlaceOverFarm, TabSlide, Fade }

internal enum class RouteExitMotion { HomeLeave, PlaceSink, TabSlide, Fade }

internal fun routeEnterMotion(
    initialIsHome: Boolean,
    targetIsHome: Boolean,
    direction: Int,
    motionAllowed: Boolean,
): RouteEnterMotion = when {
    !motionAllowed -> RouteEnterMotion.Fade
    targetIsHome -> RouteEnterMotion.HomeReturn
    initialIsHome -> RouteEnterMotion.PlaceOverFarm
    direction != 0 -> RouteEnterMotion.TabSlide
    else -> RouteEnterMotion.Fade
}

internal fun routeExitMotion(
    initialIsHome: Boolean,
    targetIsHome: Boolean,
    direction: Int,
    motionAllowed: Boolean,
): RouteExitMotion = when {
    !motionAllowed -> RouteExitMotion.Fade
    initialIsHome -> RouteExitMotion.HomeLeave
    targetIsHome -> RouteExitMotion.PlaceSink
    direction != 0 -> RouteExitMotion.TabSlide
    else -> RouteExitMotion.Fade
}

/**
 * How long the farm background layer takes to crossfade with the global device pattern when Home is
 * left or entered. It trails the page transition slightly so the background never swaps in one frame.
 */
private const val FARM_BACKGROUND_FADE_MS = 240

/** How far the farm pushes in while Home is left; returning retreats from the same depth. */
private const val FARM_DEPTH = 1.05f

/**
 * How long arrived content should treat itself as still being delivered by the route transition. It
 * covers the longest enter motion (320 ms) plus Home's delayed chrome, with a little margin for a
 * collection that emits its first page while the page is still settling.
 */
private const val ROUTE_ARRIVAL_MS = 360L

private fun NavDestination?.isWorldOrBattle(): Boolean = this != null &&
    (hasRoute<AppRoute.Battles>() || hasRoute<AppRoute.World>() || hasRoute<AppRoute.Radar>())












