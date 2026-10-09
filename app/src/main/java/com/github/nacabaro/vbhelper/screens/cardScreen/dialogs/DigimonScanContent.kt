package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.source.DigimonScanPolicy
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
internal fun DigimonScanProgressContent(percentage: Int?, message: String?, isError: Boolean) {
    if (percentage != null && percentage > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.digimon_scan_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.digimon_scan_percentage, percentage),
                    style = MaterialTheme.typography.titleMedium, color = VitalCyan)
            }
            LinearProgressIndicator(
                progress = { percentage / DigimonScanPolicy.CONVERSION_PERCENTAGE.toFloat() },
                modifier = Modifier.fillMaxWidth(), color = VitalCyan,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            Text(stringResource(if (percentage == DigimonScanPolicy.CONVERSION_PERCENTAGE)
                R.string.digimon_scan_ready else R.string.digimon_scan_help), style = MaterialTheme.typography.bodySmall)
        }
    }
    // Conversion consumes the scan data; keep its result visible even after progress resets.
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else VitalCyan) }
}

@Composable
internal fun DigimonScanConvertButton(percentage: Int?, converting: Boolean, onClick: () -> Unit) {
    if (percentage != DigimonScanPolicy.CONVERSION_PERCENTAGE) return
    Button(onClick = onClick, enabled = !converting,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(if (converting) R.string.digimon_scan_converting else R.string.digimon_scan_convert))
    }
}
