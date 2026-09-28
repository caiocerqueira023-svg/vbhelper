package com.github.nacabaro.vbhelper.screens.adventureScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import java.util.Locale
import com.github.nacabaro.vbhelper.R

@Composable
fun AdventureEntry(
    icon: BitmapData,
    stage: Int,
    vitalPoints: Int,
    timeLeft: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bitmap = remember (icon.bitmap) { icon.getBitmap() }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density: Float = LocalContext.current.resources.displayMetrics.density
    val dpSize = (icon.width * 4 / density).dp

    val completed = timeLeft <= 0
    CyberPanel(
        onClick = onClick,
        active = completed,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            androidx.compose.material3.Surface(
                color = DeepPurpleBgAlt,
                shape = RectangleShape,
                modifier = Modifier.size(88.dp)
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.padding(8.dp).widthIn(max = dpSize)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = stringResource(
                        if (completed) R.string.adventure_status_complete
                        else R.string.adventure_status_in_progress
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimaryOnDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (completed) {
                        stringResource(R.string.adventure_collect_reward)
                    } else {
                        stringResource(R.string.adventure_time_left, formatSeconds(timeLeft))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (completed) VitalCyan else TextSecondaryOnDark
                )
                Text(
                    text = stringResource(R.string.adventure_stage_vitals, stage, vitalPoints),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark
                )
            }
        }
    }
}

fun formatSeconds(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}
