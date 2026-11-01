package com.tamerslab.home.ui.screens

// The firmware-side vitals counter is capped at this value across the app
// (see ItemsScreenControllerImpl), so we reuse it as the gauge's max.
const val MAX_VITAL_POINTS = 9999

@androidx.compose.runtime.Composable
fun shortStageName(stage: Int): String =
    androidx.compose.ui.res.stringArrayResource(com.tamerslab.R.array.arena_stages)
        .getOrNull(stage.coerceAtMost(5))
        ?: androidx.compose.ui.res.stringResource(com.tamerslab.R.string.dex_detail_stage, stage.toString())
