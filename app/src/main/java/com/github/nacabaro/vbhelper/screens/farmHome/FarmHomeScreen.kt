package com.github.nacabaro.vbhelper.screens.farmHome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.navigation.navigatePrimary
import com.github.nacabaro.vbhelper.screens.digifarmScreen.FarmResidentSheet
import com.github.nacabaro.vbhelper.screens.digifarmScreen.FarmWorldSummary
import com.github.nacabaro.vbhelper.screens.homeScreens.HomeScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerDeck
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerDetent
import com.github.nacabaro.vbhelper.screens.homeScreens.PartnerPeekHeight
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageScreenControllerImpl

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
    val application = context.applicationContext as VBHelper
    val controller = shell.controller
    val summary by controller.summary
    val bits by remember(application) {
        application.container.currencyRepository.currencyValue
    }.collectAsState(initial = 0)

    var showRoster by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }

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

        HomeTopHud(
            residentCount = summary.residentCount,
            capacity = FarmWorldSummary.CAPACITY,
            bits = bits,
            onFarmClick = { showRoster = true },
            onQuestsClick = { navController.navigatePrimary(NavigationItems.Quests) },
            onSettingsClick = { navController.navigatePrimary(NavigationItems.Settings) },
            onMenuClick = { showMenu = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AnimatedVisibility(
            visible = shell.detent != PartnerDetent.Expanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = PartnerPeekHeight + 8.dp),
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Claim the slab's drag area above the navigation-bar strip: without this, pulling
                // the partner sheet up from the bottom of the screen is stolen by the system Home
                // gesture and the app disappears to the launcher.
                .systemGestureExclusion(),
        )

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
                            NavigationItems.Chat.route.replace("{characterId}", characterId.toString())
                        )
                    },
                    onFollow = { followed -> controller.setFollowTarget(followed) },
                )
            }
        }
    }

    if (showRoster) {
        FarmRosterSheet(
            residents = summary.residents,
            followedRenderId = summary.followedRenderId,
            residentLimit = summary.residentLimit,
            candidateCount = summary.candidateCount,
            spriteFilter = summary.spriteFilter,
            onResidentLimitChange = { limit -> controller.setResidentLimit(limit) },
            onSpriteFilterChange = { filter -> controller.setSpriteFilter(filter) },
            onFollow = { renderId ->
                showRoster = false
                controller.setFollowTarget(renderId)
            },
            onDismiss = { showRoster = false },
        )
    }

    if (showMenu) {
        HomeMenuSheet(navController = navController, onDismiss = { showMenu = false })
    }
}