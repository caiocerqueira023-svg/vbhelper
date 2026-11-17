package com.tamerslab.transfer.ui.vpet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.VitalSectionHeader
import com.tamerslab.domain.vpet.VpetPendingRead
import com.tamerslab.hardware.vpet.VpetReadFailure
import com.tamerslab.hardware.vpet.VpetReadResult
import com.tamerslab.ui.theme.StatusGreen
import com.tamerslab.ui.theme.StatusRed
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark

/** Decoded read plus the actions that can follow it: import, species mapping or dismiss. */
@Composable
internal fun VpetResultCard(
    result: VpetReadResult,
    simulated: Boolean,
    importing: Boolean,
    pending: VpetPendingRead?,
    onImport: () -> Unit,
    onMapSpecies: () -> Unit,
    onDiscardPending: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHex by rememberSaveable { mutableStateOf(false) }
    val unknown = stringResource(R.string.vpet_result_unknown)
    CyberPanel(
        modifier = modifier.fillMaxWidth(),
        active = result.checksumValid && pending == null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VitalSectionHeader(
                title = stringResource(R.string.vpet_result_title),
                modifier = Modifier.weight(1f),
            )
            if (simulated) {
                Text(
                    text = stringResource(R.string.vpet_result_simulated),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark,
                )
            }
        }
        VpetResultRow(stringResource(R.string.vpet_result_name), result.name ?: unknown)
        VpetResultRow(stringResource(R.string.vpet_result_model), result.model.displayName)
        VpetResultRow(stringResource(R.string.vpet_result_version), result.version.toString())
        VpetResultRow(stringResource(R.string.vpet_result_index), result.index.toString())
        VpetResultRow(stringResource(R.string.vpet_result_stage), vpetStageLabel(result.stage))
        VpetResultRow(stringResource(R.string.vpet_result_attribute), vpetAttributeLabel(result.attribute))
        VpetResultRow(stringResource(R.string.vpet_result_power), result.power?.toString() ?: unknown)
        VpetResultRow(
            label = stringResource(R.string.vpet_result_checksum),
            value = stringResource(
                if (result.checksumValid) R.string.vpet_result_checksum_valid
                else R.string.vpet_result_checksum_invalid
            ),
            valueColor = if (result.checksumValid) StatusGreen else StatusRed,
        )
        TextButton(onClick = { showHex = !showHex }) {
            Text(
                stringResource(
                    if (showHex) R.string.vpet_result_hex_hide else R.string.vpet_result_hex_show
                )
            )
        }
        if (showHex) {
            Text(
                text = result.payloadHex.ifBlank { unknown },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = TextSecondaryOnDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }
        if (pending != null) {
            Text(
                text = stringResource(R.string.vpet_result_pending),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (pending == null) {
                VitalButton(
                    onClick = onImport,
                    enabled = !importing,
                    style = VitalButtonStyle.PRIMARY,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (importing) R.string.vpet_importing else R.string.vpet_import
                        )
                    )
                }
            } else {
                VitalButton(
                    onClick = onMapSpecies,
                    enabled = !importing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.vpet_map_species))
                }
                VitalButton(
                    onClick = onDiscardPending,
                    enabled = !importing,
                    style = VitalButtonStyle.DESTRUCTIVE,
                ) {
                    Text(stringResource(R.string.vpet_discard))
                }
            }
        }
        TextButton(onClick = onDismiss, enabled = !importing) {
            Text(stringResource(R.string.vpet_result_dismiss))
        }
    }
}

/** Protocol failure surface; a failed read never overwrites the last successful result silently. */
@Composable
internal fun VpetFailurePanel(failure: VpetReadFailure, modifier: Modifier = Modifier) {
    CyberPanel(modifier = modifier.fillMaxWidth(), active = true) {
        VitalSectionHeader(title = stringResource(R.string.vpet_read_title))
        Text(
            text = vpetFailureText(failure),
            style = MaterialTheme.typography.bodySmall,
            color = StatusRed,
        )
    }
}

@Composable
private fun VpetResultRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimaryOnDark,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondaryOnDark,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

@Composable
internal fun vpetFailureText(failure: VpetReadFailure): String = when (failure) {
    is VpetReadFailure.NoResponse ->
        stringResource(R.string.vpet_failure_no_response, failure.attempts)
    is VpetReadFailure.InvalidData ->
        stringResource(R.string.vpet_failure_invalid, failure.reason)
    is VpetReadFailure.UnsupportedModel ->
        stringResource(R.string.vpet_failure_unsupported, failure.wireKey)
    else -> stringResource(R.string.vpet_failure_unknown)
}

@Composable
internal fun vpetStageLabel(stage: Int?): String = when (stage) {
    0 -> stringResource(R.string.vpet_stage_baby_i)
    1 -> stringResource(R.string.vpet_stage_baby_ii)
    2 -> stringResource(R.string.vpet_stage_child)
    3 -> stringResource(R.string.vpet_stage_adult)
    4 -> stringResource(R.string.vpet_stage_perfect)
    5 -> stringResource(R.string.vpet_stage_ultimate)
    else -> stringResource(R.string.vpet_result_unknown)
}

@Composable
internal fun vpetAttributeLabel(attribute: Int?): String = when (attribute) {
    0 -> stringResource(R.string.vpet_attribute_vaccine)
    1 -> stringResource(R.string.vpet_attribute_data)
    2 -> stringResource(R.string.vpet_attribute_virus)
    3 -> stringResource(R.string.vpet_attribute_free)
    else -> stringResource(R.string.vpet_result_unknown)
}
