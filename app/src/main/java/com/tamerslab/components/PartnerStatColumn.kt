package com.tamerslab.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R

/** Keep the approved square at normal sizes; grow with facts when larger text cannot fit. */
@Composable
fun PartnerStatColumn(level: String, attribute: String, days: String, modifier: Modifier = Modifier) {
    val minimumRow = with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp() + MaterialTheme.typography.bodyMedium.lineHeight.toDp() + 12.dp
    }
    BoxWithConstraints(modifier) {
        val grow = maxWidth < minimumRow * 3 + 32.dp
        Column(Modifier.fillMaxWidth().then(if (grow) Modifier else Modifier.aspectRatio(1f)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val cell = if (grow) Modifier.heightIn(min = minimumRow) else Modifier.weight(1f)
            InfoStatRow(R.drawable.baseline_trophy_24, stringResource(R.string.home_vbdim_level), level, cell)
            InfoStatRow(R.drawable.baseline_mood_24, stringResource(R.string.home_vbdim_attribute), attribute, cell)
            InfoStatRow(R.drawable.baseline_next_24, stringResource(R.string.home_vbdim_days), days, cell)
        }
    }
}
