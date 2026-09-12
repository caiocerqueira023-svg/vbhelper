package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalCyanDim
import kotlin.math.max

/**
 * Big header stat that mirrors the "Vitals 1,250 / 2,500" readout at the
 * top of the reference Vital Bracelet home screen: a round icon chip, a
 * label, the current value in a large accent color and a slim progress
 * track underneath showing how full the gauge currently is.
 */
@Composable
fun VitalsHeaderStat(
    label: String,
    current: Int,
    max: Int,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.baseline_vitals_24
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    tint = TextSecondaryHint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = label.uppercase(),
                    color = TextMutedOnDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%,d".format(current),
                        color = VitalCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    )
                    Text(
                        text = " / %,d".format(max),
                        color = TextMutedOnDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                val ratio = if (max <= 0) 0f else (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(listOf(VitalCyanDim, VitalCyan))
                            )
                    )
                }
            }
        }
    }
}

private val TextSecondaryHint = VitalCyan

/**
 * Small icon + label + value row, used for the Level / Attribute / Days
 * style readouts next to the character portrait.
 */
@Composable
fun InfoStatRow(
    icon: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = VitalCyan
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = TextMutedOnDark,
                modifier = Modifier.size(14.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$label: ",
                        color = TextPrimaryOnDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = value,
                        color = valueColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Weekly vitals bar chart, echoing the "Weekly Vitals Graph" widget from
 * the reference home screen: one bar per day of recorded vitals history,
 * with the value above each bar and the day-of-month label underneath.
 */
@Composable
fun WeeklyVitalsChart(
    history: List<VitalsHistory>,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    val recent = history.takeLast(7)
    val maxValue = max(1, recent.maxOf { it.vitalPoints })

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.baseline_vitals_24),
                    contentDescription = null,
                    tint = VitalCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.home_vbdim_weekly_vitals),
                    color = TextPrimaryOnDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                recent.forEach { entry ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "%,d".format(entry.vitalPoints),
                            color = TextMutedOnDark,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        val ratio = entry.vitalPoints.toFloat() / maxValue.toFloat()
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .weight(1f)
                                .fillMaxWidth(0.55f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((100f * ratio.coerceIn(0.04f, 1f)).dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(VitalCyan, VitalCyanDim)
                                        )
                                    )
                            )
                        }
                        Text(
                            text = "%d/%d".format(entry.month, entry.day),
                            color = TextMutedOnDark,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
