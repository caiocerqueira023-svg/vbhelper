package com.tamerslab.transfer.ui.scan.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.R
import com.tamerslab.components.motionEnabled
import com.tamerslab.ui.theme.StatusRed
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * Direction of one NFC transfer. The watch is the passive tag: the phone is always the reader, so
 * only the data flow changes between receiving a Digimon and sending it back.
 */
enum class NfcTransferDirection {
    /** Watch -> app: a character is being read into the app. */
    WATCH_TO_APP,

    /** App -> watch: a character is being written back to the bracelet. */
    APP_TO_WATCH,
}

/** Coarse state of the NFC link, mapped to the original VB Lab scan artwork. */
enum class NfcGuidePhase {
    WAITING,
    LINKED,
    TRANSFERRING,
    SUCCESS,
    ERROR,
}

/**
 * Pure mappings behind [NfcTransferGuide] and [DigimonTransferAura].
 *
 * Kept free of Compose state so the direction/phase contract is covered by JVM tests: the UI is
 * only allowed to pick artwork and labels from these results.
 */
object NfcTransferGuideLogic {

    fun phaseFor(
        error: Boolean = false,
        success: Boolean = false,
        transferring: Boolean = false,
        linked: Boolean = false,
    ): NfcGuidePhase = when {
        error -> NfcGuidePhase.ERROR
        success -> NfcGuidePhase.SUCCESS
        transferring -> NfcGuidePhase.TRANSFERRING
        linked -> NfcGuidePhase.LINKED
        else -> NfcGuidePhase.WAITING
    }

    fun modeFor(direction: NfcTransferDirection): DigimonTransferMode =
        if (direction == NfcTransferDirection.WATCH_TO_APP) {
            DigimonTransferMode.ARRIVING
        } else {
            DigimonTransferMode.DEPARTING
        }

    @StringRes
    fun statusLabelFor(phase: NfcGuidePhase): Int = when (phase) {
        NfcGuidePhase.WAITING -> R.string.scan_guide_waiting
        NfcGuidePhase.LINKED -> R.string.scan_guide_touch
        NfcGuidePhase.TRANSFERRING -> R.string.scan_guide_transferring
        NfcGuidePhase.SUCCESS -> R.string.scan_guide_success
        NfcGuidePhase.ERROR -> R.string.scan_guide_error
    }

    @StringRes
    fun directionLabelFor(direction: NfcTransferDirection): Int = when (direction) {
        NfcTransferDirection.WATCH_TO_APP -> R.string.scan_guide_receive_direction
        NfcTransferDirection.APP_TO_WATCH -> R.string.scan_guide_send_direction
    }
}

/**
 * The original VB Lab "how to transfer" artwork, laid out the way the app used it.
 *
 * Source assets were extracted from the discontinued VB Lab 1.4.6 APK (Unity textures
 * `scan_images` / `scan_images1` and the separate `scan_images1_0` success sprite, located by the
 * `scan_F_success` / `scan_R_success` UI objects in the reverse-engineered scene):
 *
 *  - `vb_scan_wait` — the instruction infographic shown while the watch has to be touched
 *    (phone, bracelet, polling ring, direction arrow and the WAITING…/TOUCH! banners).
 *  - `vb_scan_connecting` — the connecting illustration (`scan_images1` without its baked-in
 *    "SUCCESSFUL!" header, which the app draws from a separate sprite).
 *  - `vb_scan_successful` — the original `SUCCESSFUL!` sprite, overlaid only on success.
 *
 * Personal preservation use only; the artwork is not redistributed. No part of the illustration is
 * redrawn here — the composable only positions, scales and animates the extracted originals.
 */
@Composable
fun NfcTransferGuide(
    direction: NfcTransferDirection,
    phase: NfcGuidePhase,
    modifier: Modifier = Modifier,
) {
    val motion = motionEnabled()
    val phaseColor = when (phase) {
        NfcGuidePhase.WAITING, NfcGuidePhase.TRANSFERRING -> VitalCyan
        NfcGuidePhase.LINKED, NfcGuidePhase.SUCCESS -> VitalCyan
        NfcGuidePhase.ERROR -> StatusRed
    }
    val pulse = if (motion && (phase == NfcGuidePhase.TRANSFERRING || phase == NfcGuidePhase.LINKED)) {
        val transition = rememberInfiniteTransition(label = "vb_scan_pulse")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "vb_scan_pulse_alpha",
        ).value
    } else {
        1f
    }

    val description = stringResource(
        R.string.scan_guide_content_description,
        stringResource(NfcTransferGuideLogic.directionLabelFor(direction)),
        stringResource(NfcTransferGuideLogic.statusLabelFor(phase)),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(NfcTransferGuideLogic.directionLabelFor(direction)),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryOnDark,
            letterSpacing = 1.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .aspectRatio(
                    if (phase == NfcGuidePhase.WAITING || phase == NfcGuidePhase.LINKED) {
                        840f / 750f // scan_images instruction infographic
                    } else {
                        1024f / 822f // scan_images1 connecting illustration
                    }
                )
                .background(
                    // White paper for the instruction infographic; the connecting art has its own
                    // dark navy backdrop and must not be letterboxed onto white.
                    if (phase == NfcGuidePhase.WAITING || phase == NfcGuidePhase.LINKED) {
                        Color.White
                    } else {
                        Color(0xFF0D0505)
                    },
                    CutCornerShape(10.dp),
                )
                .clearAndSetSemantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                NfcGuidePhase.WAITING, NfcGuidePhase.LINKED -> {
                    Image(
                        painter = painterResource(R.drawable.vb_scan_wait),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .graphicsLayer { alpha = if (phase == NfcGuidePhase.LINKED) pulse else 1f },
                        contentScale = ContentScale.Fit,
                    )
                }

                NfcGuidePhase.TRANSFERRING, NfcGuidePhase.SUCCESS, NfcGuidePhase.ERROR -> {
                    Image(
                        painter = painterResource(R.drawable.vb_scan_connecting),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    if (phase == NfcGuidePhase.SUCCESS) {
                        Image(
                            painter = painterResource(R.drawable.vb_scan_successful),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (phase == NfcGuidePhase.ERROR) {
                        Image(
                            painter = painterResource(R.drawable.vb_scan_ring_red),
                            contentDescription = null,
                            modifier = Modifier
                                .size(84.dp)
                                .align(Alignment.Center),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
        if (phase == NfcGuidePhase.TRANSFERRING || phase == NfcGuidePhase.ERROR) {
            Text(
                text = stringResource(NfcTransferGuideLogic.statusLabelFor(phase)),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = phaseColor,
                modifier = Modifier
                    .background(phaseColor.copy(alpha = 0.12f), CutCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
        if (phase == NfcGuidePhase.TRANSFERRING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(top = 2.dp),
                color = VitalCyan,
                trackColor = VitalCyan.copy(alpha = 0.18f),
            )
        }
    }
}
