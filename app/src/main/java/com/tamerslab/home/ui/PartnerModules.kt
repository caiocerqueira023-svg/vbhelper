package com.tamerslab.home.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.ActiveDigimonCard
import com.tamerslab.components.ItemDisplay
import com.tamerslab.components.PartnerStatColumn
import com.tamerslab.components.VitalsHeaderStat
import com.tamerslab.components.moodSummary
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.home.ui.screens.MAX_VITAL_POINTS
import com.tamerslab.home.ui.screens.shortStageName
import com.tamerslab.utils.BitmapData

/**
 * The reusable modules of the partner overview.
 *
 * A VB device and a BE device showed the same vitals header, the same animated portrait with the
 * same stat column, the same mood/trophies/next-evolution row, the same win-rate row and the same
 * training-gains row — previously copy-pasted into [VBDiMHomeScreen] and [BEHomeScreen]. Those
 * identical blocks live here once so the legacy Home page and the farm partner deck cannot drift.
 *
 * Every block keeps the exact geometry, strings and label keys it had inline, because
 * `AppHomeParityTest` asserts those labels and the old Home page must not change.
 */

/** 96dp-ish vitals gauge: label, `current / 9999` and a cyan progress track. */
@Composable
fun PartnerVitalsHeader(
    label: String,
    vitalPoints: Int,
    modifier: Modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
) {
    VitalsHeaderStat(
        label = label,
        current = vitalPoints,
        max = MAX_VITAL_POINTS,
        modifier = modifier,
    )
}

/**
 * Animated portrait beside the Stage / Attribute / Days column. Swiping the portrait vertically
 * cycles favourites; tapping it opens the conversation; long-pressing opens the Dex details.
 */
@Composable
fun PartnerIdentityBlock(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    speechBubbleText: String?,
    favoriteTransitionDirection: Int,
    favoriteIndex: Int,
    favoriteCount: Int,
    onClickCharacter: () -> Unit,
    onLongClickCharacter: () -> Unit,
    onFavoriteSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        ActiveDigimonCard(
            activeMon = activeMon,
            cardIcon = cardIcon,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
            multiplier = 8,
            speechBubbleText = speechBubbleText,
            transitionDirection = favoriteTransitionDirection,
            favoriteIndex = favoriteIndex,
            favoriteCount = favoriteCount,
            onClick = onClickCharacter,
            onLongClick = onLongClickCharacter,
            onFavoriteSwipe = onFavoriteSwipe,
        )
        PartnerStatColumn(
            shortStageName(activeMon.stage),
            activeMon.attribute.name,
            activeMon.ageInDays.toString(),
            Modifier.weight(1f),
        )
    }
}

/** Mood, trophies and the next-evolution countdown, one equal tile each. */
@Composable
fun PartnerGlanceRow(
    mood: Int,
    trophies: Int,
    transformationCountdownMinutes: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        val tile = Modifier
            .weight(1f)
            .heightIn(min = 104.dp)
            .padding(8.dp)
        ItemDisplay(
            icon = R.drawable.baseline_mood_24,
            textValue = moodSummary(mood),
            definition = stringResource(R.string.home_vbdim_mood),
            modifier = tile,
        )
        ItemDisplay(
            icon = R.drawable.baseline_trophy_24,
            textValue = trophies.toString(),
            definition = stringResource(R.string.home_vbdim_trophies),
            modifier = tile,
        )
        ItemDisplay(
            icon = R.drawable.baseline_next_24,
            textValue = countdownText(transformationCountdownMinutes),
            definition = stringResource(R.string.home_vbdim_next_timer),
            modifier = tile,
        )
    }
}

/** Lifetime and current-form win rates. */
@Composable
fun PartnerWinRateRow(
    totalWon: Int,
    totalLost: Int,
    phaseWon: Int,
    phaseLost: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        val tile = Modifier
            .weight(1f)
            .heightIn(min = 104.dp)
            .padding(8.dp)
        ItemDisplay(
            icon = R.drawable.vb_icon_battle,
            textValue = battleWinPercent(totalWon, totalLost),
            definition = stringResource(R.string.home_vbdim_total_battle_win),
            modifier = tile,
        )
        ItemDisplay(
            icon = R.drawable.vb_icon_battle,
            textValue = battleWinPercent(phaseWon, phaseLost),
            definition = stringResource(R.string.home_vbdim_current_phase_win),
            modifier = tile,
        )
    }
}

/** Training gains reported by a BE device and by an app-only partner alike. */
@Composable
fun PartnerTrainingGainsRow(
    trainingHp: Int,
    trainingBp: Int,
    trainingAp: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        val tile = Modifier
            .weight(1f)
            .heightIn(min = 104.dp)
            .padding(8.dp)
        ItemDisplay(
            icon = R.drawable.baseline_health_24,
            textValue = "+$trainingHp",
            definition = stringResource(R.string.home_be_training_hp),
            modifier = tile,
        )
        ItemDisplay(
            icon = R.drawable.baseline_agility_24,
            textValue = "+$trainingBp",
            definition = stringResource(R.string.home_be_training_bp),
            modifier = tile,
        )
        ItemDisplay(
            icon = R.drawable.baseline_attack_24,
            textValue = "+$trainingAp",
            definition = stringResource(R.string.home_be_training_ap),
            modifier = tile,
        )
    }
}

/** `m` under an hour, `h` above it — the wording the vitals tiles have always used. */
fun countdownText(minutes: Int): String {
    val hours = minutes / 60
    return if (hours == 0) "$minutes m" else "$hours h"
}

/** Win percentage over a battle record, formatted with the app locale. */
@Composable
fun battleWinPercent(won: Int, lost: Int): String {
    val total = won + lost
    if (total == 0) return "0.00 %"
    val locale = LocalConfiguration.current.locales[0]
    return String.format(locale, "%.2f", won.toFloat() / total.toFloat() * 100) + " %"
}
