package com.tamerslab.home.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.motionEnabled
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.TransferNavigation
import com.tamerslab.home.ui.screens.MAX_VITAL_POINTS
import com.tamerslab.home.ui.screens.shortStageName
import com.tamerslab.storage.ui.StorageScreenController
import com.tamerslab.ui.MoodCondition
import com.tamerslab.ui.PartnerCondition
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.StatusGreen
import com.tamerslab.ui.theme.StatusRed
import com.tamerslab.ui.theme.StatusYellow
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getBitmap
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The partner deck: an always-visible 96dp slab plus a three-detent overview sheet over the farm.
 *
 * The detent is hoisted by the shell (`PartnerDeckContract.kt`) because the shell hides the place
 * dock when the sheet is expanded and owns the back-collapse ladder, so this composable never adds a
 * `BackHandler` of its own.
 *
 * Gesture arbitration with the world underneath: **drags are accepted only from Compose chrome** â€”
 * this composable is the chrome, so the sheet can never steal a camera gesture from the 3D surface.
 * There is no scrim at Peek or Half (the farm stays interactive and visible); the scrim exists only
 * at Expanded, where it also captures the tap that collapses to Half.
 *
 * Layout: drop this into the route `Box`. It fills that box and bottom-anchors itself, so
 * `maxHeight` is the usable route height the detent fractions are measured from.
 */
@Composable
fun PartnerDeck(
    navController: NavController,
    homeScreenController: HomeScreenController,
    storageScreenController: StorageScreenController,
    importingConnection: Boolean,
    onImportCards: () -> Unit,
    onImportConnection: () -> Unit,
    detent: PartnerDetent,
    onDetentChange: (PartnerDetent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberPartnerOverviewState(homeScreenController, storageScreenController)
    val allowMotion = motionEnabled()
    val density = LocalDensity.current

    var detailsCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTransformationId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedTransformation = state.transformationHistory.firstOrNull { it.id == selectedTransformationId }
    var collectedItem by remember { mutableStateOf<ItemDtos.PurchasedItem?>(null) }
    var collectedCurrency by remember { mutableStateOf<Int?>(null) }

    val activeMon = state.activeMon
    val openChat: () -> Unit = {
        activeMon?.let {
            navController.navigate(
                AppRoute.Chat(it.id)
            )
        }
    }
    val openTransfer: () -> Unit = { navController.navigate(TransferNavigation.route()) }
    val openStorage: () -> Unit = { navController.navigate(AppRoute.Storage) }
    val openDex: () -> Unit = { navController.navigate(AppRoute.Dex) }
    val onClickCollect: (ItemDtos.PurchasedItem?, Int?) -> Unit = { item, currency ->
        collectedItem = item
        collectedCurrency = currency
    }
    val onClickTransformation: (CharacterDtos.TransformationHistory) -> Unit = { transformation ->
        val stage = activeMon?.stage
        if (stage != null && transformation.stageId != activeMon.charId && transformation.stage <= stage) {
            selectedTransformationId = transformation.id
        }
    }

    // A state card replaces the slab entirely; there is nothing to expand, so the sheet takes the
    // height its own content asks for instead of a detent.
    val stateCard = state.readiness != HomeReadiness.READY || state.profileLoading
    val paneTitle = stringResource(R.string.home_title)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val peekPx = with(density) { PartnerPeekHeight.toPx() }
        val halfPx = maxHeightPx * HALF_FRACTION
        val expandedPx = maxHeightPx * EXPANDED_FRACTION

        fun heightOf(target: PartnerDetent): Float = when (target) {
            PartnerDetent.Peek -> peekPx
            PartnerDetent.Half -> halfPx
            PartnerDetent.Expanded -> expandedPx
        }

        val restPx = heightOf(detent)
        var dragging by remember { mutableStateOf(false) }
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }

        val animatedRestPx by animateFloatAsState(
            targetValue = restPx,
            animationSpec = tween(if (allowMotion) 260 else 0, easing = FastOutSlowInEasing),
            label = "partnerDeckDetent",
        )
        val sheetHeightPx = (if (dragging) restPx + dragOffsetPx else animatedRestPx)
            .coerceIn(peekPx, expandedPx)
        val sheetHeight: Dp = with(density) { sheetHeightPx.toDp() }

        val deckGesture = Modifier.pointerInput(detent, peekPx, halfPx, expandedPx) {
            detectDeckGesture(
                onDragStart = {
                    dragging = true
                    dragOffsetPx = 0f
                },
                onVerticalDrag = { delta ->
                    // Dragging down (positive delta) shrinks the sheet.
                    dragOffsetPx = (dragOffsetPx - delta)
                        .coerceIn(peekPx - restPx, expandedPx - restPx)
                },
                onVerticalEnd = { velocityY ->
                    val released = (restPx + dragOffsetPx).coerceIn(peekPx, expandedPx)
                    dragging = false
                    dragOffsetPx = 0f
                    onDetentChange(
                        resolveDetent(
                            current = detent,
                            releasedPx = released,
                            velocityY = velocityY,
                            peekPx = peekPx,
                            halfPx = halfPx,
                            expandedPx = expandedPx,
                        )
                    )
                },
                onVerticalCancel = {
                    dragging = false
                    dragOffsetPx = 0f
                },
                onHorizontalSwipe = { direction -> state.cycleFavorite(direction) },
            )
        }

        // Scrim exists only at Expanded: at Peek/Half the farm must stay interactive.
        val scrimAlpha by animateFloatAsState(
            targetValue = if (detent == PartnerDetent.Expanded) 1f else 0f,
            animationSpec = tween(if (allowMotion) 200 else 0),
            label = "partnerDeckScrim",
        )
        if (scrimAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SpaceBlack.copy(alpha = 0.28f * scrimAlpha))
                    .clickable { onDetentChange(PartnerDetent.Half) },
            )
        }

        val corner = CutCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (stateCard) {
                        Modifier.heightIn(min = PartnerPeekHeight)
                    } else {
                        Modifier.heightIn(min = PartnerPeekHeight).height(sheetHeight)
                    }
                )
                .clip(corner)
                .background(SurfaceElevatedPurple.copy(alpha = 0.82f))
                .border(1.dp, SurfaceStroke, corner)
                .semantics { this.paneTitle = paneTitle }
                .testTag("partner-deck"),
        ) {
            PartnerDeckBody(
                state = state,
                detent = detent,
                stateCard = stateCard,
                homeScreenController = homeScreenController,
                importingConnection = importingConnection,
                onImportCards = onImportCards,
                onImportConnection = onImportConnection,
                onOpenStorage = openStorage,
                onOpenDex = openDex,
                onOpenChat = openChat,
                onOpenTransfer = openTransfer,
                onClickCharacter = openChat,
                onLongClickCharacter = { activeMon?.let { detailsCharacterId = it.charId } },
                onFavoriteSwipe = state.cycleFavorite,
                onClickCollect = onClickCollect,
                onClickTransformation = onClickTransformation,
                onDetentChange = onDetentChange,
                deckGesture = deckGesture,
                allowMotion = allowMotion,
            )
        }
    }

    PartnerOverlayDialogs(
        state = state,
        homeScreenController = homeScreenController,
        detailsCharacterId = detailsCharacterId,
        onDetailsCharacterIdChange = { detailsCharacterId = it },
        selectedTransformation = selectedTransformation,
        onDismissTransformation = { selectedTransformationId = null },
        collectedItem = collectedItem,
        collectedCurrency = collectedCurrency,
        onDismissCollected = {
            collectedItem = null
            collectedCurrency = null
        },
    )
}

/** Body of the deck, split by detent so each height only composes what fits in it. */
@Composable
private fun ColumnScope.PartnerDeckBody(
    state: PartnerOverviewState,
    detent: PartnerDetent,
    stateCard: Boolean,
    homeScreenController: HomeScreenController,
    importingConnection: Boolean,
    onImportCards: () -> Unit,
    onImportConnection: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenDex: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenTransfer: () -> Unit,
    onClickCharacter: () -> Unit,
    onLongClickCharacter: () -> Unit,
    onFavoriteSwipe: (Int) -> Unit,
    onClickCollect: (ItemDtos.PurchasedItem?, Int?) -> Unit,
    onClickTransformation: (CharacterDtos.TransformationHistory) -> Unit,
    onDetentChange: (PartnerDetent) -> Unit,
    deckGesture: Modifier,
    allowMotion: Boolean,
) {
    val activeMon = state.activeMon
    val cardIcon = state.cardIcon

    when {
        // Loading, no partner yet, or a partner whose payload is missing: a self-contained card.
        stateCard || activeMon == null || cardIcon == null -> when {
            state.profileLoading || state.readiness == HomeReadiness.LOADING -> {
                LoadingSlab(modifier = deckGesture)
            }

            state.readiness == HomeReadiness.PROFILE_UNAVAILABLE -> {
                ProfileUnavailableSlab(
                    onReload = state.reload,
                    onOpenDex = onOpenDex,
                    modifier = deckGesture,
                )
            }

            else -> {
                ChoosePartnerSlab(
                    cardsReady = state.cardsReady,
                    connectionReady = state.connectionReady,
                    importingConnection = importingConnection,
                    onOpenStorage = onOpenStorage,
                    onImportCards = onImportCards,
                    onImportConnection = onImportConnection,
                    modifier = deckGesture,
                )
            }
        }

        else -> {
            PartnerSlab(
                activeMon = activeMon,
                cardIcon = cardIcon,
                mood = activeMon.mood,
                stageLabel = shortStageName(activeMon.stage),
                detent = detent,
                allowMotion = allowMotion,
                onOpenChat = onOpenChat,
                onExpand = {
                    // The chevron is an actual detent control: up from peek/half opens one step,
                    // and the down arrow shown while expanded pulls the sheet back down one step.
                    onDetentChange(
                        when (detent) {
                            PartnerDetent.Peek -> PartnerDetent.Half
                            PartnerDetent.Half -> PartnerDetent.Expanded
                            PartnerDetent.Expanded -> PartnerDetent.Half
                        }
                    )
                },
                onOpenDetails = onLongClickCharacter,
                gesture = deckGesture,
            )

            if (detent != PartnerDetent.Peek) {
                if (detent == PartnerDetent.Half) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        PartnerHalfBody(
                            state = state,
                            activeMon = activeMon,
                            cardIcon = cardIcon,
                            onClickCharacter = onClickCharacter,
                            onLongClickCharacter = onLongClickCharacter,
                            onFavoriteSwipe = onFavoriteSwipe,
                            onOpenChat = onOpenChat,
                            onOpenTransfer = onOpenTransfer,
                            onOpenDex = onOpenDex,
                        )
                    }
                } else {
                    // Expanded body owns its own scrolling inside the device renderers.
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        PartnerOverviewContent(
                            state = state,
                            homeScreenController = homeScreenController,
                            onClickCollect = onClickCollect,
                            onClickTransformation = onClickTransformation,
                            onClickCharacter = onClickCharacter,
                            onLongClickCharacter = onLongClickCharacter,
                            onFavoriteSwipe = onFavoriteSwipe,
                            contentPadding = PaddingValues(0.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The 96dp always-visible slab: portrait, name, `stage Â· attribute`, vitals bar with the numeric
 * value, mood dot, a 48dp chat button and the detent chevron. It is also the drag handle, the
 * favourite swiper and the tap target that opens the half sheet.
 */
@Composable
private fun PartnerSlab(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    mood: Int,
    stageLabel: String,
    detent: PartnerDetent,
    allowMotion: Boolean,
    onOpenChat: () -> Unit,
    onExpand: () -> Unit,
    onOpenDetails: () -> Unit,
    gesture: Modifier,
) {
    val paneTitle = stringResource(R.string.home_title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PartnerPeekHeight)
            .then(gesture)
            .combinedClickable(
                onClickLabel = paneTitle,
                onLongClickLabel = stringResource(R.string.home_open_dex_details),
                onLongClick = onOpenDetails,
                onClick = onExpand,
            )
            .semantics { contentDescription = paneTitle }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PartnerSlabPortrait(
            activeMon = activeMon,
            cardIcon = cardIcon,
            allowMotion = allowMotion,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = activeMon.nickname?.takeIf { it.isNotBlank() }
                    ?: activeMon.speciesName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.widget_digimon_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryOnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$stageLabel Â· ${activeMon.attribute.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(moodDotColor(mood)),
                )
            }
            PartnerVitalsBar(
                current = activeMon.vitalPoints,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onOpenChat, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.baseline_mood_24),
                contentDescription = stringResource(R.string.nav_chat),
                tint = VitalCyan,
            )
        }
        IconButton(onClick = onExpand, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(
                    when (detent) {
                        PartnerDetent.Peek, PartnerDetent.Half -> R.drawable.baseline_single_arrow_up
                        PartnerDetent.Expanded -> R.drawable.baseline_single_arrow_down
                    }
                ),
                contentDescription = stringResource(
                    if (detent == PartnerDetent.Expanded) {
                        R.string.home_partner_collapse
                    } else {
                        R.string.home_partner_expand
                    }
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Half-detent content: identity, glance row, win rates and the three partner actions. */
@Composable
private fun PartnerHalfBody(
    state: PartnerOverviewState,
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    onClickCharacter: () -> Unit,
    onLongClickCharacter: () -> Unit,
    onFavoriteSwipe: (Int) -> Unit,
    onOpenChat: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenDex: () -> Unit,
) {
    PartnerVitalsHeader(
        label = state.nickname?.takeIf { it.isNotBlank() }
            ?: state.speciesName?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.widget_digimon_label),
        vitalPoints = activeMon.vitalPoints,
    )
    PartnerIdentityBlock(
        activeMon = activeMon,
        cardIcon = cardIcon,
        speechBubbleText = state.speechBubbleText,
        favoriteTransitionDirection = state.favoriteTransitionDirection,
        favoriteIndex = state.favoriteIndex,
        favoriteCount = state.favoriteCharacters.size,
        onClickCharacter = onClickCharacter,
        onLongClickCharacter = onLongClickCharacter,
        onFavoriteSwipe = onFavoriteSwipe,
    )
    PartnerGlanceRow(
        mood = activeMon.mood,
        trophies = activeMon.trophies,
        transformationCountdownMinutes = activeMon.transformationCountdown,
    )
    PartnerWinRateRow(
        totalWon = activeMon.totalBattlesWon,
        totalLost = activeMon.totalBattlesLost,
        phaseWon = activeMon.currentPhaseBattlesWon,
        phaseLost = activeMon.currentPhaseBattlesLost,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VitalButton(onClick = onOpenTransfer, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.nav_transfer), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        VitalButton(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.nav_chat), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        VitalButton(
            onClick = onOpenDex,
            style = VitalButtonStyle.PRIMARY,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(R.string.home_open_dex_details),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** Invitation shown when there is no active partner to summarise. */
@Composable
private fun ChoosePartnerSlab(
    cardsReady: Boolean,
    connectionReady: Boolean,
    importingConnection: Boolean,
    onOpenStorage: () -> Unit,
    onImportCards: () -> Unit,
    onImportConnection: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.app_home_no_partner_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.app_home_no_partner_body),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryOnDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VitalButton(
                onClick = onOpenStorage,
                style = VitalButtonStyle.PRIMARY,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    stringResource(R.string.app_select_partner),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!cardsReady) {
                VitalButton(
                    onClick = onImportCards,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.app_setup_cards),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (!connectionReady) {
                VitalButton(
                    onClick = onImportConnection,
                    enabled = !importingConnection,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        stringResource(
                            if (importingConnection) R.string.app_connection_importing
                            else R.string.app_setup_connection
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Recovery slab for a partner whose card or device payload is missing. */
@Composable
private fun ProfileUnavailableSlab(
    onReload: () -> Unit,
    onOpenDex: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.app_profile_unavailable_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryOnDark,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.app_profile_unavailable_body),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryOnDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VitalButton(onClick = onReload, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_reload_details),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VitalButton(onClick = onOpenDex, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_open_dex),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Loading slab: cut-corner skeleton bars, no spinner over the world. */
@Composable
private fun LoadingSlab(modifier: Modifier) {
    val allowMotion = motionEnabled()
    val pulse by animateFloatAsState(
        targetValue = if (allowMotion) 0.55f else 0.35f,
        animationSpec = tween(if (allowMotion) 900 else 0),
        label = "partnerDeckSkeleton",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.app_loading_partner),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryOnDark,
            maxLines = 1,
        )
        Box(
            Modifier
                .fillMaxWidth(0.55f)
                .height(10.dp)
                .clip(CutCornerShape(4.dp))
                .background(SurfaceStroke.copy(alpha = pulse)),
        )
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(10.dp)
                .clip(CutCornerShape(4.dp))
                .background(SurfaceStroke.copy(alpha = pulse * 0.7f)),
        )
    }
}

/** 56dp nearest-neighbour portrait that alternates the two idle frames like the full card does. */
@Composable
private fun PartnerSlabPortrait(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    allowMotion: Boolean,
) {
    var frame by remember(activeMon.id) { mutableIntStateOf(0) }
    LaunchedEffect(activeMon.id, activeMon.spriteIdle2, allowMotion) {
        frame = 0
        if (allowMotion) {
            while (true) {
                delay(600)
                frame = 1 - frame
            }
        }
    }
    val source = if (frame == 1) {
        BitmapData(activeMon.spriteIdle2, activeMon.spriteWidth, activeMon.spriteHeight)
    } else {
        BitmapData(activeMon.spriteIdle, activeMon.spriteWidth, activeMon.spriteHeight)
    }
    val image = remember(source.bitmap) { source.getBitmap().asImageBitmap() }
    val cardImage = remember(cardIcon.bitmap) { cardIcon.getBitmap().asImageBitmap() }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CutCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, SurfaceStroke, CutCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(44.dp),
        )
        Image(
            bitmap = cardImage,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp),
        )
    }
}

/** Slim vitals readout: `1,234 / 9,999` plus a cyan track. */
@Composable
private fun PartnerVitalsBar(current: Int, modifier: Modifier = Modifier) {
    val ratio = (current.toFloat() / MAX_VITAL_POINTS.toFloat()).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%,d".format(current),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = VitalCyan,
                maxLines = 1,
            )
            Text(
                text = " / %,d".format(MAX_VITAL_POINTS),
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedOnDark,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(4.dp)
                    .background(VitalCyan),
            )
        }
    }
}

/** Mood dot: green settled-positive, yellow neutral, red low â€” the same thresholds as the copy. */
@Composable
private fun moodDotColor(mood: Int): Color = when (PartnerCondition.mood(mood)) {
    MoodCondition.POSITIVE -> StatusGreen
    MoodCondition.LOW -> StatusRed
    MoodCondition.SETTLED -> StatusYellow
}

// --- Detent geometry and gesture arbitration -------------------------------------------------

private const val HALF_FRACTION = 0.44f
private const val EXPANDED_FRACTION = 0.96f
private val SHEET_CORNER = 18.dp

/**
 * Maps the released sheet height (plus vertical velocity) to the detent it should settle in.
 *
 * A fast flick wins over proximity â€” that is what makes the 96dp slab feel like a handle rather than
 * a slider â€” otherwise the nearest detent by height wins.
 */
private fun resolveDetent(
    current: PartnerDetent,
    releasedPx: Float,
    velocityY: Float,
    peekPx: Float,
    halfPx: Float,
    expandedPx: Float,
): PartnerDetent {
    if (velocityY < -FLICK_VELOCITY) {
        return when (current) {
            PartnerDetent.Peek -> PartnerDetent.Half
            PartnerDetent.Half, PartnerDetent.Expanded -> PartnerDetent.Expanded
        }
    }
    if (velocityY > FLICK_VELOCITY) {
        return when (current) {
            PartnerDetent.Expanded -> PartnerDetent.Half
            PartnerDetent.Half, PartnerDetent.Peek -> PartnerDetent.Peek
        }
    }
    val peekToHalf = (peekPx + halfPx) / 2f
    val halfToExpanded = (halfPx + expandedPx) / 2f
    return when {
        releasedPx < peekToHalf -> PartnerDetent.Peek
        releasedPx < halfToExpanded -> PartnerDetent.Half
        else -> PartnerDetent.Expanded
    }
}

private const val FLICK_VELOCITY = 900f
private const val AXIS_UNDECIDED = 0
private const val AXIS_VERTICAL = 1
private const val AXIS_HORIZONTAL = 2

/**
 * Single axis-locked gesture for the deck chrome.
 *
 * The axis is decided once, at touch slop: vertical becomes a detent drag, horizontal becomes a
 * favourite-cycle swipe (40dp threshold, matching `CharacterEntry`'s portrait swipe). Deciding once
 * means one drag can never both move the sheet and change the partner.
 */
private suspend fun PointerInputScope.detectDeckGesture(
    onDragStart: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onVerticalEnd: (Float) -> Unit,
    onVerticalCancel: () -> Unit,
    onHorizontalSwipe: (Int) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var last: Offset = down.position
        var totalX = 0f
        var totalY = 0f
        var axis = AXIS_UNDECIDED
        var ended = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                ended = true
                break
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            totalX = change.position.x - down.position.x
            totalY = change.position.y - down.position.y
            if (axis == AXIS_UNDECIDED &&
                (abs(totalY) > viewConfiguration.touchSlop || abs(totalX) > viewConfiguration.touchSlop)
            ) {
                axis = if (abs(totalY) >= abs(totalX)) AXIS_VERTICAL else AXIS_HORIZONTAL
                if (axis == AXIS_VERTICAL) onDragStart()
            }
            when (axis) {
                AXIS_VERTICAL -> {
                    val delta = change.position.y - last.y
                    if (delta != 0f) {
                        change.consume()
                        onVerticalDrag(delta)
                    }
                }
                AXIS_HORIZONTAL -> change.consume()
                else -> Unit
            }
            last = change.position
        }

        when (axis) {
            AXIS_VERTICAL ->
                if (ended) onVerticalEnd(tracker.calculateVelocity().y) else onVerticalCancel()
            AXIS_HORIZONTAL -> {
                val threshold = 40.dp.toPx()
                if (abs(totalX) >= threshold) onHorizontalSwipe(if (totalX < 0f) 1 else -1)
            }
            else -> Unit
        }
    }
}


