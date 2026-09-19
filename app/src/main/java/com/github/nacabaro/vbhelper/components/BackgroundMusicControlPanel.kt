package com.github.nacabaro.vbhelper.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.audio.AppMusicController
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import kotlin.math.roundToInt

@Composable
fun BackgroundMusicControlPanel(
    musicController: AppMusicController,
    modifier: Modifier = Modifier
) {
    val settings by musicController.settings.collectAsState()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, SurfaceStroke)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background music",
                        color = TextPrimaryOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (settings.enabled) "Music is playing across the app" else "Music is off",
                        color = TextSecondaryOnDark,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = musicController::setEnabled
                )
            }
            Text(
                text = "Volume ${((settings.volume * 100f).roundToInt())}%",
                color = VitalCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Slider(
                value = settings.volume,
                onValueChange = musicController::setVolume,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
