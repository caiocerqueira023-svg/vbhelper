package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark

/** Reserve the recovery-action slot so incoming statuses do not move the Zoom controls. */
@Composable
internal fun RadarSessionStatusPanel(state: RadarSurfaceState, onRecovery: () -> Unit, modifier: Modifier = Modifier) {
    val error = state in listOf(RadarSurfaceState.STORAGE_FAILURE, RadarSurfaceState.POPULATION_FAILURE, RadarSurfaceState.BATTLE_SAVE_FAILURE)
    val action = when (state) {
        RadarSurfaceState.STORAGE_FAILURE -> R.string.ui_world_retry_world
        RadarSurfaceState.LOCATION_PERMISSION -> R.string.ui_world_enable_location
        RadarSurfaceState.POPULATION_FAILURE, RadarSurfaceState.EMPTY_REGION -> R.string.ui_world_refresh_radar
        RadarSurfaceState.BATTLE_SAVE_FAILURE -> R.string.ui_world_retry_battle_save
        else -> null
    }
    val textHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 3 }
    CyberPanel(modifier.fillMaxWidth().testTag("radar-session-status")) {
        Text(
            text = stringResource(state.messageResource()),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else TextSecondaryOnDark,
            modifier = Modifier.fillMaxWidth().heightIn(min = textHeight)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("radar-status-action-slot"), contentAlignment = Alignment.CenterStart) {
            action?.let { resource ->
                VitalButton(onClick = onRecovery, modifier = Modifier.heightIn(min = 48.dp).testTag("radar-status-recovery")) {
                    Text(stringResource(resource))
                }
            }
        }
    }
}

private fun RadarSurfaceState.messageResource(): Int = when (this) {
    RadarSurfaceState.RECONCILING -> R.string.ui_world_reconciling
    RadarSurfaceState.UPDATING -> R.string.ui_world_updating
    RadarSurfaceState.STORAGE_FAILURE -> R.string.ui_world_storage_failure
    RadarSurfaceState.UNSUPPORTED_WORLD -> R.string.ui_world_unsupported_rules
    RadarSurfaceState.LOCATION_PERMISSION -> R.string.ui_world_location_required
    RadarSurfaceState.LOCATING -> R.string.ui_world_locating
    RadarSurfaceState.STALE_LOCATION -> R.string.ui_world_location_stale
    RadarSurfaceState.NO_CARDS -> R.string.ui_world_no_spawn_cards
    RadarSurfaceState.CARDS_DISABLED -> R.string.ui_world_spawn_cards_disabled
    RadarSurfaceState.POPULATING -> R.string.ui_world_populating
    RadarSurfaceState.POPULATION_FAILURE -> R.string.ui_world_population_failure
    RadarSurfaceState.EMPTY_REGION -> R.string.ui_world_empty_region
    RadarSurfaceState.READY -> R.string.ui_world_radar_active
    RadarSurfaceState.APPROXIMATE_LOCATION -> R.string.ui_world_approximate_location
    RadarSurfaceState.FROZEN -> R.string.ui_world_battle_preparing_world
    RadarSurfaceState.SUSPENDED -> R.string.ui_world_session_suspended
    RadarSurfaceState.SAVING_BATTLE -> R.string.ui_world_saving_battle
    RadarSurfaceState.BATTLE_SAVE_FAILURE -> R.string.ui_world_battle_save_failure
}
