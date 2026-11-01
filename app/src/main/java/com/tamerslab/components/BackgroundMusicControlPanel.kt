package com.tamerslab.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.tamerslab.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.audio.AppMusicController
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import kotlin.math.roundToInt

@Composable
fun BackgroundMusicControlPanel(
    musicController: AppMusicController,
    modifier: Modifier = Modifier
) {
    val settings by musicController.settings.collectAsStateWithLifecycle()
    val musicLabel = stringResource(R.string.immersion_music)
    val volumeLabel = stringResource(R.string.immersion_volume)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, SurfaceStroke)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(settings.enabled, role = Role.Switch, onValueChange = musicController::setEnabled),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = musicLabel,
                        color = TextPrimaryOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(if (settings.enabled) R.string.immersion_music_enabled else R.string.immersion_music_disabled),
                        color = TextSecondaryOnDark,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = null
                )
            }
            Text(
                text = stringResource(R.string.immersion_volume_percentage, (settings.volume * 100f).roundToInt()),
                color = VitalCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Slider(
                value = settings.volume,
                onValueChange = musicController::setVolume,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = volumeLabel }
            )
        }
    }
}

