package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.SurfaceHighlightPurple
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

/**
 * Top header bar styled after the Vital Bracelet / Pendulum companion apps:
 * a dark rounded pill housing the screen title, with small chip-style icon
 * buttons docked to either side.
 */
@Composable
fun TopBanner(
    text: String,
    modifier: Modifier = Modifier,
    onGearClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onScanClick: (() -> Unit)? = null,
    onAdventureClick: (() -> Unit)? = null,
    onModifyClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(
            text = text.uppercase(),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp)
        )

        if (onGearClick != null) {
            TopBannerIconChip(
                icon = R.drawable.baseline_settings_24,
                contentDescription = stringResource(R.string.ui_settings),
                onClick = onGearClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        } else if (onAdventureClick != null) {
            TopBannerIconChip(
                icon = R.drawable.baseline_fort_24,
                contentDescription = stringResource(R.string.ui_adventure),
                onClick = onAdventureClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        } else if (onModifyClick != null) {
            TopBannerIconChip(
                icon = R.drawable.baseline_edit_24,
                contentDescription = stringResource(R.string.ui_adventure),
                onClick = onModifyClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        if (onScanClick != null) {
            TopBannerIconChip(
                icon = R.drawable.baseline_nfc_24,
                contentDescription = stringResource(R.string.ui_scan),
                onClick = onScanClick,
                accent = true,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        } else if (onBackClick != null) {
            TopBannerIconChip(
                icon = R.drawable.baseline_arrow_back_24,
                contentDescription = stringResource(R.string.ui_back_icon),
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }
}

@Composable
private fun TopBannerIconChip(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) VitalCyan.copy(alpha = 0.18f) else SurfaceHighlightPurple)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = if (accent) VitalCyan else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
