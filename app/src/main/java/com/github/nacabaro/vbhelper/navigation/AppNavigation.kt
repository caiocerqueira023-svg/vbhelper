package com.github.nacabaro.vbhelper.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.github.nacabaro.vbhelper.screens.lorebookScreen.LorebookScreen
import com.github.nacabaro.vbhelper.screens.lorebookScreen.LorebookScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldScreen
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldChatScreen
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldChatScreenControllerImpl
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
fun AppNavigation(
    applicationNavigationHandlers: AppNavigationHandlers,
    initialRoute: String? = null
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val tabSwipeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val allowMotion = motionEnabled()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val musicController = remember { AppMusicController(context) }

    LaunchedEffect(currentRoute) {
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
            bottomBar = {
                if (!expandedNavigation) BottomNavigationBar(navController = navController)
            }
        ) { contentPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                if (expandedNavigation) VitalNavigationRail(navController = navController)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tabSwipeNavigation(
                            currentRoute = currentRoute,
                            thresholdPx = tabSwipeThresholdPx,
                            onNavigate = { destination ->
                                navController.navigatePrimary(destination)
                            }
                        )
                ) {
                NavHost(
            navController = navController,
            startDestination = initialRoute ?: NavigationItems.Home.route,
            enterTransition = {
                val direction = primaryTabTransitionDirection(
                    initialState.destination.route,
                    targetState.destination.route
                )
                if (!allowMotion || direction == 0) {
                    fadeIn(animationSpec = tween(if (allowMotion) 160 else 80))
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
                if (!allowMotion || direction == 0) {
                    fadeOut(animationSpec = tween(if (allowMotion) 120 else 60))
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
                BattlesScreen(musicController = musicController)
            }
            composable(NavigationItems.Home.route) {
                HomeScreen(
                    navController = navController,
                    homeScreenController = applicationNavigationHandlers.homeScreenController,
                    storageScreenController = applicationNavigationHandlers.storageScreenController
                )
            }
            composable(NavigationItems.World.route) { WorldScreen(navController = navController) }
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
                CardsScreen(
                    navController = navController,
                    cardScreenController = applicationNavigationHandlers.cardScreenController
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
            composable(NavigationItems.Items.route) {
                ItemsScreen(
                    navController = navController
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
}

/**
 * Keeps the primary tabs reachable with a deliberate horizontal swipe while
 * leaving secondary destinations (dialogs, chat, settings, etc.) untouched.
 */
private fun Modifier.tabSwipeNavigation(
    currentRoute: String?,
    thresholdPx: Float,
    onNavigate: (NavigationItems) -> Unit
): Modifier = pointerInput(currentRoute, thresholdPx) {
    val currentIndex = primaryDestinations.indexOfFirst { it.route == currentRoute }
    if (currentIndex < 0) return@pointerInput

    var dragDistance = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragDistance = 0f },
        onHorizontalDrag = { _, dragAmount ->
            dragDistance += dragAmount
        },
        onDragEnd = {
            val targetIndex = when {
                dragDistance <= -thresholdPx -> currentIndex + 1
                dragDistance >= thresholdPx -> currentIndex - 1
                else -> -1
            }
            primaryDestinations.getOrNull(targetIndex)?.let(onNavigate)
            dragDistance = 0f
        },
        onDragCancel = { dragDistance = 0f }
    )
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
