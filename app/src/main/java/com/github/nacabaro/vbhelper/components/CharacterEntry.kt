package com.github.nacabaro.vbhelper.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.github.cfogrady.vbnfc.vb.SpecialMission
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.ui.theme.StatusBlue
import com.github.nacabaro.vbhelper.ui.theme.StatusBlueDim
import com.github.nacabaro.vbhelper.ui.theme.StatusGreen
import com.github.nacabaro.vbhelper.ui.theme.StatusGreenDim
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.ui.theme.StatusRedDim
import com.github.nacabaro.vbhelper.ui.theme.StatusYellow
import com.github.nacabaro.vbhelper.ui.theme.StatusYellowDim
import com.github.nacabaro.vbhelper.ui.theme.SurfaceHighlightPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.getObscuredBitmap
import androidx.compose.ui.res.stringResource


@Composable
fun CharacterEntry(
    icon: BitmapData,
    modifier: Modifier = Modifier,
    cardIcon: BitmapData? = null,
    obscure: Boolean = false,
    disabled: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    multiplier: Int = 4,
    idleFrame2: BitmapData? = null,
    animationKey: Any = icon.bitmap.contentHashCode(),
    speechBubbleText: String? = null,
    statusText: String? = null,
    grayscale: Boolean = false,
    vitalPoints: Int? = null,
    cardColors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ),
    onClick: () -> Unit = {  }
) {
    var animationFrame by remember { mutableIntStateOf(0) }
    val animationOffsetMillis = remember(animationKey) {
        (animationKey.hashCode().toLong() and 0x7fff_ffffL) % 750L
    }

    LaunchedEffect(animationKey, idleFrame2?.bitmap?.contentHashCode()) {
        // Keep a stable but unique phase for each Digimon, so a grid does not animate in lockstep.
        animationFrame = if (animationOffsetMillis > 375L) 1 else 0
        if (idleFrame2 != null) {
            delay(animationOffsetMillis)
            while (true) {
                delay(750L)
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

    Card(
        shape = shape,
        onClick = when (disabled) {
            true -> { {} }
            false -> onClick
        },
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp),
        colors = cardColors,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SurfaceHighlightPurple,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                )
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
                    color = VitalCyan,
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
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke),
                        modifier = Modifier
                            .align(Alignment.TopStart)
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
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(4.dp)
                            .clickable(onClick = onClick)
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
                    contentDescription = "Icon",
                    filterQuality = FilterQuality.None,
                    colorFilter = when {
                        obscure -> ColorFilter.tint(color = MaterialTheme.colorScheme.secondary)
                        grayscale -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        else -> null
                    },
                    modifier = Modifier
                        .size(dpSize)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = !disabled, onClick = onClick)
                )

                if (cardIcon != null) {
                    val cardBitmap = remember(icon.bitmap) { cardIcon.getBitmap() }
                    val iconBitmap = remember(cardBitmap) { cardBitmap.asImageBitmap() }
                    val cardIconDpSize = (icon.width * iconSizeMultiplier / density).dp

                    Image(
                        bitmap = iconBitmap,
                        contentDescription = "Card icon",
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
) {
    val context = LocalContext.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke),
        onClick = {
            Toast.makeText(context, definition, Toast.LENGTH_SHORT).show()
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
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
                    contentDescription = definition,
                    tint = VitalCyan,
                    modifier = Modifier.fillMaxSize(0.55f)
                )
            }
            Text(
                text = textValue,
                textAlign = TextAlign.Center,
                fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryOnDark,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (definition.isNotBlank()) {
                Text(
                    text = definition,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
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
    onClickCollect: (Long) -> Unit = {  }
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
        SpecialMission.Status.COMPLETED -> Triple(bannerColor, Color.Black, true)
        SpecialMission.Status.FAILED -> Triple(StatusRedDim, TextPrimaryOnDark, false)
        else -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, TextMutedOnDark, false)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        onClick = if (specialMission.status == SpecialMission.Status.COMPLETED) {
            { onClickCollect(specialMission.id) }
        } else if (specialMission.status == SpecialMission.Status.UNAVAILABLE) {
            { }
        } else {
            { onClickMission(specialMission.id) }
        },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isVivid) Color.Black.copy(alpha = 0.15f) else SurfaceStroke
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
                    .background(if (isVivid) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
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
                    text = completion,
                    fontFamily = MaterialTheme.typography.titleSmall.fontFamily,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            if (specialMission.status == SpecialMission.Status.COMPLETED) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
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
