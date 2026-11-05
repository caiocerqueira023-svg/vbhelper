package com.tamerslab.farm.ui.home

import androidx.activity.compose.BackHandler
import com.tamerslab.navigation.AppRoute
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.motionEnabled
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.TransferNavigation
import com.tamerslab.navigation.TransferSection
import com.tamerslab.navigation.navigatePrimary
import com.tamerslab.farm.ui.digifarm.FarmResidentSheet
import com.tamerslab.farm.ui.digifarm.FarmWorldSummary
import com.tamerslab.home.ui.HomeScreenControllerImpl
import com.tamerslab.home.ui.PartnerDeck
import com.tamerslab.home.ui.PartnerDetent
import com.tamerslab.storage.ui.FarmSwapRequest
import com.tamerslab.storage.ui.FarmSwapTarget
import com.tamerslab.storage.ui.StorageScreenControllerImpl
import kotlinx.coroutines.delay

/**
 * Home's chrome only: top status zone, place dock, partner deck and the sheets.
 *
 * The 3D world itself is composed by the app shell (`AppNavigation`) so it survives place
 * navigation — this route draws no background and no scene, only the interface over it.
 */
@Composable
fun FarmHomeScreen(
    navController: NavController,
    shell: FarmHomeShellState,
    homeScreenController: HomeScreenControllerImpl,
    storageScreenController: StorageScreenControllerImpl,
    importingConnection: Boolean = false,
    onImportCards: () -> Unit = { navController.navigatePrimary(NavigationItems.Dex) },
    onImportConnection: () -> Unit = { navController.navigatePrimary(NavigationItems.Settings) },
) {
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication
    val controller = shell.controller
    val summary by controller.summary
    val bits by remember(application) {
        application.container.currencyRepository.currencyValue
    }.collectAsStateWithLifecycle(initialValue = 0)

    var showRoster by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val onSheetProgress = remember(shell) { { progress: Float -> shell.sheetProgress = progress } }

    // Back ladder: Expanded -> Half -> Peek -> system back (leaves the app). The detent lives in the
    // shell because it also gates the world's frame loop.
    BackHandler(enabled = shell.detent != PartnerDetent.Peek) {
        shell.detent = when (shell.detent) {
            PartnerDetent.Expanded -> PartnerDetent.Half
            PartnerDetent.Half -> PartnerDetent.Peek
            PartnerDetent.Peek -> PartnerDetent.Peek
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!summary.loading && !summary.hasResidents) {
            CyberEmptyState(
                message = stringResource(R.string.home_farm_empty_body),
                title = stringResource(R.string.home_farm_empty_title),
                actionLabel = stringResource(R.string.home_farm_empty_action),
                onAction = { navController.navigatePrimary(NavigationItems.Storage) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The partner overview owns the screen when it is fully open: the status zone fades out and
        // lifts away instead of sitting on top of the sheet.
        val overviewOpen = shell.detent == PartnerDetent.Expanded
        val topHudAlpha by animateFloatAsState(
            targetValue = if (overviewOpen) 0f else 1f,
            animationSpec = tween(if (motionEnabled()) 220 else 0),
            label = "homeTopHudVisibility",
        )
        HomeTopHud(
            residentCount = summary.residentCount,
            capacity = FarmWorldSummary.CAPACITY,
            bits = bits,
            onFarmClick = { showRoster = true },
            onTransferClick = {
                navController.navigate(TransferNavigation.route(TransferSection.DEVICES))
            },

            onSettingsClick = { navController.navigatePrimary(NavigationItems.Settings) },
            onMenuClick = { showMenu = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Above the deck in z-order so the half-sheet's tap-on-the-farm catcher can never
                // swallow a tap meant for the status zone.
                .zIndex(1f)
                .graphicsLayer {
                    alpha = topHudAlpha
                    translationY = (1f - topHudAlpha) * -14.dp.toPx()
                }
                .then(
                    if (topHudAlpha < 0.01f) Modifier.semantics { invisibleToUser() } else Modifier
                ),
        )

        // The place row owns the very bottom of the screen, under the partner slab. It yields its band
        // to the sheet in the same motion the sheet is pulled: alpha and offset follow the sheet's own
        // progress, so the handoff is continuous - no detent-edge pop, no gap, no clipping. The
        // progress is read inside the graphics layer so a drag invalidates the draw phase only.
        val density = LocalDensity.current
        val bandPx = with(density) { PlaceRowBandHeight.toPx() }
        val rowHidden by remember(shell) {
            derivedStateOf { shell.sheetProgress > 0.5f }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val progress = shell.sheetProgress.coerceIn(0f, 1f)
                    alpha = 1f - progress
                    translationY = progress * bandPx
                }
                .then(
                    if (rowHidden) Modifier.semantics { invisibleToUser() } else Modifier
                ),
        ) {
            PlaceDock(
                selected = null,
                onPlaceClick = { place -> navController.navigateToPlace(place) },
            )
        }

        PartnerDeck(
            navController = navController,
            homeScreenController = homeScreenController,
            storageScreenController = storageScreenController,
            importingConnection = importingConnection,
            onImportCards = onImportCards,
            onImportConnection = onImportConnection,
            detent = shell.detent,
            onDetentChange = { shell.detent = it },
            placeRowBand = PlaceRowBandHeight,
            onSheetProgress = onSheetProgress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Claim the slab's drag area above the navigation-bar strip: without this, pulling
                // the partner sheet up from the bottom of the screen is stolen by the system Home
                // gesture and the app disappears to the launcher.
                .systemGestureExclusion(),
        )

        // Boot cover: the farm streams residents one asset per frame and the active partner loads after
        // the roster, so the data flag is not enough - the gate waits for the diorama's own "settled"
        // signal. It is shown once per session (the shell owns the flag), never during ordinary use,
        // and it has both a minimum and a hard timeout so it can neither flash nor stick.
        val sceneReady by controller.sceneReady
        var emptyStable by remember { mutableStateOf(false) }
        LaunchedEffect(summary.loading, summary.hasResidents) {
            if (summary.hasResidents) {
                emptyStable = false
                return@LaunchedEffect
            }
            if (summary.loading) return@LaunchedEffect
            // The roster reports "settled and empty" for a beat before the residents are assigned.
            // Hold the cover through that window instead of lifting on the transient empty state.
            delay(1_500)
            emptyStable = true
        }
        LaunchedEffect(Unit) {
            delay(BOOT_COVER_MAXIMUM_MS)
            BootCover.dismissed = true
        }
        // Deliberately no lifecycle shortcut here: a transient stop during startup (a config change, a
        // splash hand-off) used to retire the cover before the farm had assembled, which is exactly the
        // machinery this cover exists to hide.
        // No time floor: a fast device lifts the cover as soon as its scene is actually in place, a slow
        // one keeps it until then. The settle signal is the contract, not a stopwatch.
        val farmSettled = !summary.loading && (sceneReady || emptyStable)
        LaunchedEffect(farmSettled) {
            if (farmSettled) {
                delay(BOOT_COVER_MINIMUM_MS)
                BootCover.dismissed = true
            }
        }
        AnimatedVisibility(
            visible = !BootCover.dismissed,
            enter = EnterTransition.None,
            exit = fadeOut(tween(if (motionEnabled()) 320 else 0)),
        ) {
            FarmBootScreen()
        }

        shell.selectedResidentId?.let { renderId ->
            val resident = summary.residents.firstOrNull { it.renderId == renderId }
            if (resident != null) {
                FarmResidentSheet(
                    resident = resident,
                    isFollowed = summary.followedRenderId == renderId,
                    onDismiss = { shell.selectedResidentId = null },
                    onOpenChat = { characterId ->
                        shell.selectedResidentId = null
                        navController.navigate(
                            AppRoute.Chat(characterId)
                        )
                    },
                    onFollow = { followed -> controller.setFollowTarget(followed) },
                    onSetActive = { characterId ->
                        storageScreenController.setActive(
                            characterId = characterId,
                            announce = true,
                            onCompletion = {},
                            onRejected = {},
                        )
                    },
                    onRemoveFromFarm = {
                        controller.removeFromFarm(resident.characterId)
                    },
                    onSwitchFromStorage = {
                        FarmSwapRequest.target = FarmSwapTarget(
                            renderId = resident.renderId,
                            characterId = resident.characterId,
                            name = resident.name,
                            isActivePartner = resident.isActivePartner,
                        )
                        navController.navigatePrimary(NavigationItems.Storage)
                    },
                )
            }
        }
    }

    if (showRoster) {
        FarmRosterSheet(
            options = summary.memberOptions,
            residentLimit = summary.residentLimit,
            spriteFilter = summary.spriteFilter,
            onResidentLimitChange = { limit -> controller.setResidentLimit(limit) },
            onSpriteFilterChange = { filter -> controller.setSpriteFilter(filter) },
            onToggleMember = { characterId, member -> controller.setFarmMember(characterId, member) },
            onDismiss = { showRoster = false },
        )
    }

    if (showMenu) {
        HomeMenuSheet(navController = navController, onDismiss = { showMenu = false })
    }
}

/** Minimum time the cover stays up after the farm settles, so the fade never looks like a glitch. */
private const val BOOT_COVER_MINIMUM_MS = 450L


/** Hard ceiling: a scene that never reports settled must not trap the app behind the cover. */
private const val BOOT_COVER_MAXIMUM_MS = 12_000L
