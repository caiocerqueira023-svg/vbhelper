package com.tamerslab.home.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import com.tamerslab.farm.ui.digiline.DigilinePin
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.navigatePrimary
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
 * Gesture arbitration with the world underneath: **drags are accepted only from Compose chrome** —
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
    /** Height of the place row that sits under the sheet at Peek; the sheet yields it while opening. */
    placeRowBand: Dp = 0.dp,
    /** Reports 0 at Peek and 1 at Half so the shell can fade the place row in the same motion. */
    onSheetProgress: (Float) -> Unit = {},
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
    // The slab's chat key opens the whole DigiLine list, with the partner it belongs to pinned on top.
    val openDigiline: () -> Unit = {
        activeMon?.let { DigilinePin.target = it.id }
        navController.navigatePrimary(NavigationItems.Digiline)
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
        // One owned height value. The drag writes it 1:1 under the finger and the settle animation
        // continues from wherever the finger left it, so there is no jump at drag start or release.
        val sheetHeightState = remember { mutableFloatStateOf(peekPx) }
        var settleToken by remember { mutableIntStateOf(0) }
        LaunchedEffect(restPx, dragging, settleToken, peekPx, expandedPx) {
            if (dragging) return@LaunchedEffect
            animate(
                initialValue = sheetHeightState.floatValue,
                targetValue = restPx.coerceIn(peekPx, expandedPx),
                animationSpec = tween(if (allowMotion) 300 else 0, easing = FastOutSlowInEasing),
            ) { value, _ -> sheetHeightState.floatValue = value }
        }
        // 0 at Peek, 1 at Half. The place row under the sheet reads this to fade and give up its band
        // in the same motion. The sheet's height itself is only read in layout and in the graphics
        // layer, never during composition: dragging then invalidates layout/draw instead of
        // recomposing the whole overview on every pointer frame, which is what made the drag feel
        // rougher than the detent button.
        val halfSpan = (halfPx - peekPx).coerceAtLeast(1f)
        val bandPx = with(density) { placeRowBand.toPx() }
        fun progressOf(heightPx: Float): Float =
            if (stateCard) 0f else ((heightPx - peekPx) / halfSpan).coerceIn(0f, 1f)
        LaunchedEffect(peekPx, halfPx, stateCard) {
            snapshotFlow { sheetHeightState.floatValue }.collect { onSheetProgress(progressOf(it)) }
        }
        // Compose the body as soon as the sheet has clearly left Peek, not after the drag lands: an
        // empty slab sliding up and then filling in was most of the "rebuilt every time" feel. The
        // threshold is a derived flag, so crossing it costs one recomposition per drag, not one per
        // frame.
        val pastPeekThreshold = with(density) { 44.dp.toPx() }
        val pastPeek by remember(peekPx, pastPeekThreshold) {
            derivedStateOf { sheetHeightState.floatValue > peekPx + pastPeekThreshold }
        }
        val bodyDetent = when {
            detent != PartnerDetent.Peek -> detent
            dragging && pastPeek -> PartnerDetent.Half
            else -> PartnerDetent.Peek
        }
        // Keep the body alive once it has been opened: rebuilding the whole overview on every pull was
        // the clank. At Peek it is simply clipped away by the sheet.
        var overviewWarm by remember { mutableStateOf(false) }
        LaunchedEffect(bodyDetent) {
            if (bodyDetent != PartnerDetent.Peek) overviewWarm = true
        }
        val showBody = overviewWarm

        val deckGesture = Modifier.pointerInput(detent, peekPx, halfPx, expandedPx) {
            detectDeckGesture(
                onDragStart = {
                    // Cancels the settle animation without moving the sheet: the finger takes over from
                    // exactly where the animation had got to.
                    dragging = true
                },
                onVerticalDrag = { delta ->
                    // Dragging down (positive delta) shrinks the sheet; 1:1 with the finger.
                    sheetHeightState.floatValue =
                        (sheetHeightState.floatValue - delta).coerceIn(peekPx, expandedPx)
                },
                onVerticalEnd = { velocityY ->
                    val released = sheetHeightState.floatValue
                    dragging = false
                    settleToken++
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
                    settleToken++
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

        // A tap on the farm lowers a half-open sheet. The catcher covers only the world above the
        // sheet, so the sheet keeps its own gestures; it is drawn before the sheet for the same reason.
        if (detent == PartnerDetent.Half) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val sheetTop = (constraints.maxHeight - sheetHeightState.floatValue)
                            .roundToInt().coerceIn(0, constraints.maxHeight)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = sheetTop, maxHeight = sheetTop)
                        )
                        layout(placeable.width, sheetTop) { placeable.place(0, 0) }
                    }
                    .pointerInput(detent) {
                        detectTapGestures { onDetentChange(PartnerDetent.Peek) }
                    },
            )
        }

        val closeSlopPx = with(density) { 16.dp.toPx() }

        // Overscroll hand-off state for the current touch. The sheet only takes over on a scroll that
        // *starts* with the inner content already at its end: a gesture that merely reaches the end
        // stops there, and the next one pulls the sheet. [consumed] tracks whether the inner list took
        // any of this gesture's scroll, [pulled] whether the sheet moved, [settled] whether the release
        // has been resolved.
        val overscrollGesture = remember { OverscrollGesture() }

        fun resolveOverscrollGesture(velocityY: Float, availableY: Float) {
            if (!overscrollGesture.pulled || overscrollGesture.settled) return
            overscrollGesture.settled = true
            dragging = false
            settleToken++
            val released = sheetHeightState.floatValue
            val pulledDown = (heightOf(detent) - released) > closeSlopPx
            // A pull on the content's overscroll closes: it travels all the way under the finger and
            // lands at Peek instead of stopping at the detent below.
            onDetentChange(
                if (pulledDown || availableY > 0f || velocityY > 0f) PartnerDetent.Peek else detent
            )
        }

        val sheetNestedScroll = remember(peekPx, halfPx, expandedPx, detent) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y == 0f) {
                        // The inner list took this scroll: the gesture did not start at its end.
                        overscrollGesture.consumed = true
                        return Offset.Zero
                    }
                    if (available.y < 0f) return Offset.Zero
                    if (overscrollGesture.consumed) return Offset.Zero
                    val current = sheetHeightState.floatValue
                    if (current <= peekPx + 0.5f) return Offset.Zero
                    dragging = true
                    overscrollGesture.pulled = true
                    val next = (current - available.y).coerceIn(peekPx, expandedPx)
                    sheetHeightState.floatValue = next
                    return Offset(0f, current - next)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    resolveOverscrollGesture(velocityY = available.y, availableY = available.y)
                    return Velocity(0f, available.y)
                }
            }
        }

        val corner = CutCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Drop into the place row's band as the sheet opens: at Peek the slab sits above the
                // row, at Half and beyond it owns the bottom of the screen.
                .graphicsLayer {
                    translationY = -(bandPx * (1f - progressOf(sheetHeightState.floatValue)))
                }
                .then(
                    if (stateCard) {
                        Modifier.heightIn(min = PartnerPeekHeight)
                    } else {
                        Modifier
                            .heightIn(min = PartnerPeekHeight)
                            .layout { measurable, constraints ->
                                val heightPx = sheetHeightState.floatValue.roundToInt()
                                    .coerceIn(peekPx.roundToInt(), expandedPx.roundToInt())
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = heightPx, maxHeight = heightPx)
                                )
                                layout(placeable.width, heightPx) { placeable.place(0, 0) }
                            }
                    }
                )
                .nestedScroll(sheetNestedScroll)
                .pointerInput(detent) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.changes.none { it.pressed }) {
                                resolveOverscrollGesture(velocityY = 0f, availableY = 0f)
                                overscrollGesture.consumed = false
                                overscrollGesture.pulled = false
                                overscrollGesture.settled = false
                            }
                        }
                    }
                }
                .clip(corner)
                .background(SurfaceElevatedPurple.copy(alpha = PartnerSlabAlpha))
                .slabFrame(SurfaceStroke, 1.dp, SHEET_CORNER)
                .semantics { this.paneTitle = paneTitle }
                .testTag("partner-deck"),
        ) {
            PartnerDeckBody(
                state = state,
                detent = detent,
                bodyDetent = bodyDetent,
                showBody = showBody,
                stateCard = stateCard,
                homeScreenController = homeScreenController,
                importingConnection = importingConnection,
                onImportCards = onImportCards,
                onImportConnection = onImportConnection,
                onOpenStorage = openStorage,
                onOpenDex = openDex,
                onOpenChat = openDigiline,
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
    /** Which body the sheet should show this frame; may lead [detent] while the sheet is being pulled. */
    bodyDetent: PartnerDetent,
    /** Once opened, the body stays composed so re-opening never rebuilds it. */
    showBody: Boolean,
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
                    // The chevron is an actual detent control: up from peek/half opens one step, and
                    // the down arrow shown while expanded drops the sheet all the way to peek.
                    onDetentChange(
                        when (detent) {
                            PartnerDetent.Peek -> PartnerDetent.Half
                            PartnerDetent.Half -> PartnerDetent.Expanded
                            PartnerDetent.Expanded -> PartnerDetent.Peek
                        }
                    )
                },
                onOpenDetails = onLongClickCharacter,
                gesture = deckGesture,
            )

            if (showBody) {
                if (bodyDetent == PartnerDetent.Half) {
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
 * The 96dp always-visible slab: portrait, name, `stage · attribute`, vitals bar with the numeric
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
                    text = "$stageLabel · ${activeMon.attribute.name}",
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

/** Mood dot: green settled-positive, yellow neutral, red low — the same thresholds as the copy. */
@Composable
private fun moodDotColor(mood: Int): Color = when (PartnerCondition.mood(mood)) {
    MoodCondition.POSITIVE -> StatusGreen
    MoodCondition.LOW -> StatusRed
    MoodCondition.SETTLED -> StatusYellow
}

// --- Detent geometry and gesture arbitration -------------------------------------------------

private const val HALF_FRACTION = 0.44f
private const val EXPANDED_FRACTION = 0.96f

/**
 * Sheet frame: chamfers, top edge and sides only. The bottom edge is deliberately left open so the
 * sheet flows into the place row under it - a stroked bottom edge is exactly the hard line that made
 * the two zones look glued together.
 */
private fun Modifier.slabFrame(color: Color, stroke: Dp, corner: Dp): Modifier = drawWithContent {
    drawContent()
    val strokePx = stroke.toPx()
    val cornerPx = corner.toPx()
    val half = strokePx / 2f
    val w = size.width
    // Upper edge only: the two chamfers and the top line separate the sheet from the farm. Sides and
    // bottom stay open so the sheet has no silhouette where it meets the place row beside/below it.
    drawLine(color, Offset(half, cornerPx), Offset(cornerPx, half), strokePx)
    drawLine(color, Offset(cornerPx, half), Offset(w - cornerPx, half), strokePx)
    drawLine(color, Offset(w - cornerPx, half), Offset(w - half, cornerPx), strokePx)
}

private val SHEET_CORNER = 18.dp

/**
 * Maps the released sheet height (plus vertical velocity) to the detent it should settle in.
 *
 * A fast flick wins over proximity — that is what makes the 96dp slab feel like a handle rather than
 * a slider — otherwise the nearest detent by height wins.
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

/** Per-touch bookkeeping for the overview's overscroll hand-off. */
private class OverscrollGesture {
    /** The inner content consumed scroll during this touch, so it was not already at its end. */
    var consumed = false
    /** The sheet moved with this touch. */
    var pulled = false
    /** The release of this touch has already been resolved into a detent. */
    var settled = false
}
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


