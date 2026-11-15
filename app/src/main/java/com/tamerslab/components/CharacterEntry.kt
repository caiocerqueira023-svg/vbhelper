package com.tamerslab.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import com.tamerslab.hardware.nfc.vb.SpecialMission
import com.tamerslab.R
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.ui.theme.StatusBlue
import com.tamerslab.ui.theme.StatusBlueDim
import com.tamerslab.ui.theme.StatusGreen
import com.tamerslab.ui.theme.StatusGreenDim
import com.tamerslab.ui.theme.StatusRed
import com.tamerslab.ui.theme.StatusRedDim
import com.tamerslab.ui.theme.StatusYellow
import com.tamerslab.ui.theme.StatusYellowDim
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.OnStatus
import com.tamerslab.ui.theme.SurfaceHighlightPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.InactiveOutline
import com.tamerslab.utils.getObscuredBitmap
import androidx.compose.ui.res.stringResource
import kotlin.math.abs


@Composable
fun CharacterEntry(
    icon: BitmapData,
    modifier: Modifier = Modifier,
    cardIcon: BitmapData? = null,
    obscure: Boolean = false,
    disabled: Boolean = false,
    shape: Shape = RectangleShape,
    multiplier: Int = 4,
    idleFrame2: BitmapData? = null,
    // Byte-array identity is a stable, cheap fallback. Computing a full
    // sprite content hash during every recomposition is costly in long grids.
    animationKey: Any = icon.bitmap,
    speechBubbleText: String? = null,
    statusText: String? = null,
    grayscale: Boolean = false,
    vitalPoints: Int? = null,
    cardColors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ),
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onVerticalSwipe: ((Int) -> Unit)? = null,
    entryPadding: Dp = 8.dp,
    fitSpriteToBounds: Boolean = false,
    selected: Boolean = false,
    iconDescription: String? = null,
    unavailable: Boolean = false,
    onClick: () -> Unit = {  }
) {
    val effectiveShape = if (vitalPoints != null) RectangleShape else shape
    val motionEnabled = motionEnabled()
    val inactiveDescription = stringResource(R.string.participation_inactive)
    val signalColor = if (unavailable) InactiveOutline else VitalCyan
    // A grid that is arriving with a route change does not run its own entrance: the page transition
    // already carries the arrival, and one animated layer per visible card on top of it was the
    // difference between a smooth change and a dropped-frame one on a mid-range phone.
    var hasEntered by remember(animationKey) { mutableStateOf(RouteArrival.inFlight) }
    LaunchedEffect(animationKey, motionEnabled) { hasEntered = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (!motionEnabled || hasEntered) 1f else 0f,
        animationSpec = tween(if (motionEnabled) 260 else 0),
        label = "characterEntryIn"
    )
    var animationFrame by remember { mutableIntStateOf(0) }
    val animationTiming = remember(animationKey) {
        // A mixed seed avoids consecutive database IDs producing almost identical timings.
        val seed = (animationKey.hashCode().toLong() * 1_103_515_245L + 12_345L) and 0x7fff_ffffL
        val intervalMillis = 550L + (seed % 301L)
        val offsetMillis = (seed / 997L) % intervalMillis
        offsetMillis to intervalMillis
    }

    LaunchedEffect(animationKey, idleFrame2?.bitmap, motionEnabled, unavailable) {
        // Keep a stable but unique phase for each Digimon, so a grid does not animate in lockstep.
        animationFrame = if (animationTiming.first * 2L >= animationTiming.second) 1 else 0
        if (idleFrame2 != null && motionEnabled && !unavailable) {
            delay(animationTiming.first)
            while (true) {
                delay(animationTiming.second)
                animationFrame = 1 - animationFrame
            }
        }
    }

    val displayedFrame = if (animationFrame == 1 && idleFrame2 != null) {
        idleFrame2
    } else {
        icon
    }
    val bitmap = remember(displayedFrame.bitmap, obscure) {
        if (obscure) displayedFrame.getObscuredBitmap() else displayedFrame.getBitmap()
    }
    val iconSizeMultiplier = 3
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density: Float = LocalContext.current.resources.displayMetrics.density
    val dpSize = (icon.width * multiplier / density).dp

    var verticalDrag by remember { mutableStateOf(0f) }
    val swipeModifier = if (onVerticalSwipe != null) {
        Modifier.pointerInput(onVerticalSwipe) {
            val threshold = 40.dp.toPx()
            detectVerticalDragGestures(
                onDragStart = { verticalDrag = 0f },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    verticalDrag += dragAmount
                },
                onDragEnd = {
                    if (abs(verticalDrag) >= threshold) {
                        onVerticalSwipe(if (verticalDrag < 0f) 1 else -1)
                    }
                    verticalDrag = 0f
                },
                onDragCancel = { verticalDrag = 0f }
            )
        }
    } else {
        Modifier
    }

    Card(
        shape = effectiveShape,
        modifier = modifier
            .aspectRatio(1f)
            .padding(entryPadding)
            .cyberFrame(active = vitalPoints != null || selected, inactive = unavailable)
            .then(swipeModifier)
            .semantics(mergeDescendants = true) {
                iconDescription?.let { contentDescription = it }
                this.selected = selected
                if (unavailable) stateDescription = inactiveDescription
            }
            .combinedClickable(
                role = Role.Button,
                enabled = !disabled,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = longClickLabel
            )
            .graphicsLayer(
                alpha = entranceAlpha,
                scaleX = 0.96f + entranceAlpha * 0.04f,
                scaleY = 0.96f + entranceAlpha * 0.04f
            ),
        colors = cardColors,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (unavailable) InactiveOutline else if (selected) VitalCyan else SurfaceStroke)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.86f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .align(Alignment.Center)
            )
            vitalPoints?.let { vitals ->
                CircularProgressIndicator(
                    progress = { (vitals / 9_999f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxSize(0.86f)
                        .align(Alignment.Center),
                    color = signalColor,
                    trackColor = SurfaceStroke,
                    strokeWidth = 4.dp
                )
            }

            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                if (!statusText.isNullOrBlank()) {
                    Card(
                        shape = CutCornerShape(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(2f)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimaryOnDark,
                            maxLines = 2
                        )
                    }
                }
                if (!speechBubbleText.isNullOrBlank()) {
                    Card(
                        shape = CutCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(2f)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = speechBubbleText.take(40) + if (speechBubbleText.length > 40) "…" else "",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimaryOnDark,
                            maxLines = 2
                        )
                    }
                }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    filterQuality = FilterQuality.None,
                    colorFilter = when {
                        obscure -> ColorFilter.tint(color = MaterialTheme.colorScheme.secondary)
                        grayscale || unavailable -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        else -> null
                    },
                    modifier = if (fitSpriteToBounds) {
                        Modifier.fillMaxSize(0.82f).align(Alignment.Center)
                    } else {
                        Modifier.size(dpSize).align(Alignment.BottomCenter)
                    }
                )

                if (cardIcon != null) {
                    val cardBitmap = remember(cardIcon.bitmap) { cardIcon.getBitmap() }
                    val iconBitmap = remember(cardBitmap) { cardBitmap.asImageBitmap() }
                    val cardIconDpSize = (icon.width * iconSizeMultiplier / density).dp

                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(cardIconDpSize)
                    )
                }
            }
        }
    }
}

@Composable
fun ItemDisplay(
    icon: Int,
    textValue: String,
    modifier: Modifier = Modifier,
    definition: String = "",
    detailText: String? = null,
) {
    var detailsExpanded by remember(detailText) { mutableStateOf(false) }
    val actionLabel = stringResource(if (detailsExpanded) R.string.immersion_hide_details else R.string.immersion_details)
    val panelModifier = if (detailText == null) modifier else modifier.clickable(role = Role.Button,
        onClickLabel = actionLabel, onClick = { detailsExpanded = !detailsExpanded })
    Card(
        modifier = panelModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(VitalCyan.copy(alpha = 0.16f))
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = VitalCyan,
                    modifier = Modifier.fillMaxSize(0.55f)
                )
            }
            Text(
                text = textValue,
                textAlign = TextAlign.Center,
                fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryOnDark,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (detailsExpanded && detailText != null) Text(detailText, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (definition.isNotBlank()) {
                Text(
                    text = if (detailText == null) definition else "$definition · $actionLabel",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedOnDark,
                    minLines = 2,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun SpecialMissionsEntry(
    specialMission: SpecialMissions,
    modifier: Modifier = Modifier,
    onClickMission: (Long) -> Unit = {  },
    onClickCollect: (Long) -> Unit = {  },
    onClickAssign: (Int) -> Unit = {  },
    canAssign: Boolean = false,
) {
    val textValue = when (specialMission.missionType) {
        SpecialMission.Type.NONE -> stringResource(R.string.special_mission_none)
        SpecialMission.Type.STEPS -> stringResource(
            R.string.special_mission_steps,
            specialMission.goal
        )
        SpecialMission.Type.BATTLES -> stringResource(
            R.string.special_mission_battles,
            specialMission.goal
        )
        SpecialMission.Type.WINS -> stringResource(
            R.string.special_mission_wins,
            specialMission.goal
        )
        SpecialMission.Type.VITALS -> stringResource(
            R.string.special_mission_vitals,
            specialMission.goal
        )
    }

    val progress = if (specialMission.status == SpecialMission.Status.COMPLETED) {
        specialMission.goal
    } else {
        specialMission.progress
    }

    val completion = when (specialMission.missionType) {
        SpecialMission.Type.NONE -> ""
        SpecialMission.Type.STEPS -> stringResource(
            R.string.special_mission_steps_progress,
            progress
        )
        SpecialMission.Type.BATTLES -> stringResource(
            R.string.special_mission_battles_progress,
            progress
        )
        SpecialMission.Type.WINS -> stringResource(
            R.string.special_mission_wins_progress,
            progress
        )
        SpecialMission.Type.VITALS -> stringResource(
            R.string.special_mission_vitals_progress,
            progress
        )
    }

    val icon = when (specialMission.missionType) {
        SpecialMission.Type.NONE -> R.drawable.baseline_free_24
        SpecialMission.Type.STEPS -> R.drawable.baseline_agility_24
        SpecialMission.Type.BATTLES -> R.drawable.baseline_swords_24
        SpecialMission.Type.WINS -> R.drawable.baseline_trophy_24
        SpecialMission.Type.VITALS -> R.drawable.baseline_vitals_24
    }

    // Colorful ribbon-style banner colors, echoing the mission-log cards
    // from the reference UI (each mission type gets its own bold color).
    val (bannerColor, bannerColorDim) = when (specialMission.missionType) {
        SpecialMission.Type.STEPS -> StatusYellow to StatusYellowDim
        SpecialMission.Type.BATTLES -> StatusBlue to StatusBlueDim
        SpecialMission.Type.WINS -> StatusRed to StatusRedDim
        SpecialMission.Type.VITALS -> StatusGreen to StatusGreenDim
        SpecialMission.Type.NONE -> SurfaceHighlightPurple to SurfaceHighlightPurple
    }

    val (containerColor, contentColor, isVivid) = when (specialMission.status) {
        SpecialMission.Status.IN_PROGRESS -> Triple(bannerColorDim, TextPrimaryOnDark, false)
        SpecialMission.Status.COMPLETED -> Triple(bannerColor, OnStatus, true)
        SpecialMission.Status.FAILED -> Triple(StatusRedDim, TextPrimaryOnDark, false)
        else -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, TextMutedOnDark, false)
    }

    // An empty slot that this host can fill tells the visitor what tapping it will do.
    val emptySlotHint = stringResource(R.string.home_special_mission_slot_empty)
    val detailText = if (specialMission.status == SpecialMission.Status.UNAVAILABLE && canAssign) {
        emptySlotHint
    } else {
        completion
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        onClick = when {
            specialMission.status == SpecialMission.Status.COMPLETED -> { { onClickCollect(specialMission.id) } }
            specialMission.status == SpecialMission.Status.UNAVAILABLE ->
                if (canAssign) { { onClickAssign(specialMission.watchId) } } else { { } }
            else -> { { onClickMission(specialMission.id) } }
        },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isVivid) SpaceBlack.copy(alpha = 0.15f) else SurfaceStroke
        )

    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isVivid) SpaceBlack.copy(alpha = 0.15f) else TextPrimaryOnDark.copy(alpha = 0.08f))
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = stringResource(R.string.special_mission_icon_content_description),
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = textValue,
                    fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = detailText,
                    fontFamily = MaterialTheme.typography.titleSmall.fontFamily,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            if (specialMission.status == SpecialMission.Status.COMPLETED) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpaceBlack.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "OK",
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor
                    )
                }
            }
        }
    }
}
