package com.tamerslab.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.components.ChatComposer
import com.tamerslab.digiline.freechat.model.FreeChatChip
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark

/** Authored topic suggestions are capped at three so the composer never reads as a menu. */
internal const val FREE_CHAT_MAX_CHIPS = 3

/** A quiet, non-technical status line for boundary and closed-session states. */
data class FreeChatNotice(val message: String, val isSafety: Boolean = false)

/**
 * DigiLine Free Talk composer: authored topic chips plus the shared chat composer.
 *
 * The link is a conversation, not a messenger: chips stay subordinate (label typography, cut corners,
 * 48dp targets), the clear action is a quiet text button, and every status line is authored copy with
 * no technical detail. A pending authored scene card keeps the slot, so this composer is only placed
 * when no card is open.
 */
@Composable
fun FreeChatComposer(
    chips: List<FreeChatChip>,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    sendLabel: String,
    chipsLabel: String,
    clearLabel: String,
    onChipClick: (FreeChatChip) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
    enabled: Boolean = true,
    errorMessage: String? = null,
    notice: FreeChatNotice? = null,
    maxLength: Int = 160,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .semantics { contentDescription = chipsLabel }
                    .testTag("freechat-chips"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.take(FREE_CHAT_MAX_CHIPS).forEach { chip ->
                    OutlinedButton(
                        onClick = { onChipClick(chip) },
                        enabled = enabled && !sending,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = chip.text }
                            .testTag("freechat-chip-${chip.id}"),
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, SurfaceStroke),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryOnDark)
                    ) {
                        Text(
                            text = chip.text,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        notice?.takeIf { it.message.isNotBlank() }?.let { activeNotice ->
            Text(
                text = activeNotice.message,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("freechat-notice"),
                style = MaterialTheme.typography.bodySmall,
                color = if (activeNotice.isSafety) MaterialTheme.colorScheme.error else TextSecondaryOnDark
            )
        }

        ChatComposer(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            sendLabel = sendLabel,
            sending = sending,
            enabled = enabled,
            singleLine = true,
            maxLength = maxLength,
            errorMessage = errorMessage,
            onSend = onSend
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onClear,
                enabled = enabled && !sending,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("freechat-clear")
            ) {
                Text(
                    text = clearLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark
                )
            }
        }
    }
}
