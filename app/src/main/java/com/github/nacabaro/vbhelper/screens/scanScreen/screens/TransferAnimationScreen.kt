package com.github.nacabaro.vbhelper.screens.scanScreen.screens

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.StatusGreen
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalPurple
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.ImageBitmapData
import kotlinx.coroutines.delay

@Composable
fun TransferAnimationScreen(
    topBannerText: String,
    detectedTransportMessage: String?,
    transferStatusMessage: String?,
    characterPreview: ImageBitmapData? = null,
    onClickCancel: () -> Unit,
    isTransferring: Boolean = true,
) {
    val motionEnabled = motionEnabled()
    var pulseScale by remember { mutableStateOf(1f) }
    var animationProgress by remember { mutableStateOf(0) }

    val scale by animateFloatAsState(
        targetValue = pulseScale,
        animationSpec = tween(durationMillis = 800),
        label = "pulse_scale"
    )

    val progress by animateIntAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 100, easing = FastOutLinearInEasing),
        label = "progress"
    )

    LaunchedEffect(isTransferring, motionEnabled) {
        if (isTransferring && motionEnabled) {
            while (true) {
                pulseScale = 1.2f
                delay(400)
                pulseScale = 1f
                delay(400)
            }
        }
    }

    LaunchedEffect(isTransferring) {
        if (isTransferring) {
            while (animationProgress < 100) {
                delay(50)
                animationProgress += 2
            }
        }
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = topBannerText,
                onBackClick = onClickCancel
            )
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            characterPreview?.let { preview ->
                CyberPanel(
                    modifier = Modifier.padding(bottom = 24.dp),
                    active = true
                ) {
                    Image(
                        bitmap = preview.imageBitmap,
                        contentDescription = "Transferred character sprite",
                        modifier = Modifier
                            .size(preview.dpWidth, preview.dpHeight)
                            .padding(8.dp),
                        filterQuality = FilterQuality.None
                    )
                }
            }

            // Animated NFC link indicator.
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        color = VitalCyan.copy(alpha = 0.12f),
                        shape = CutCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = VitalPurple,
                            shape = CutCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = TextPrimaryOnDark,
                        modifier = Modifier
                            .size(44.dp)
                            .scale(if (scale > 1f) 1.1f else 1f)
                    )
                }
            }

            Text(
                text = stringResource(R.string.action_place_near_reader),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 24.dp),
                fontWeight = FontWeight.Medium
            )

            if (!detectedTransportMessage.isNullOrBlank()) {
                Text(
                    text = detectedTransportMessage,
                    modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    fontSize = 14.sp
                )
            }

            if (!transferStatusMessage.isNullOrBlank()) {
                Text(
                    text = transferStatusMessage,
                    modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = VitalPurple
                )
            }

            // Progress ring
            if (isTransferring) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .size(100.dp)
                        .padding(top = 24.dp),
                    strokeWidth = 4.dp,
                    color = VitalCyan
                )

                Text(
                    text = "$progress%",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 12.sp,
                    color = TextMutedOnDark
                )
            }

            VitalButton(
                onClick = onClickCancel,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
fun TransferCompleteScreen(
    topBannerText: String,
    resultMessage: String,
    onClickOk: () -> Unit,
    isSuccess: Boolean = true,
) {
    val motionEnabled = motionEnabled()
    var showCheckmark by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (showCheckmark) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutLinearInEasing),
        label = "checkmark_scale"
    )

    LaunchedEffect(motionEnabled) {
        if (motionEnabled) delay(300)
        showCheckmark = true
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = topBannerText,
                onBackClick = {}
            )
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        color = if (isSuccess) StatusGreen else StatusRed,
                        shape = CutCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = TextPrimaryOnDark,
                    modifier = Modifier.size(64.dp)
                )
            }

            Text(
                text = resultMessage,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            VitalButton(
                onClick = onClickOk,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
            ) {
                Text(stringResource(R.string.ui_ok))
            }
        }
    }
}
