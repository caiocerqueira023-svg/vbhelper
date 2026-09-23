package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.ui.theme.SurfaceDeepPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurpleBright

@Composable
fun ChatContextPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceElevatedPurple.copy(alpha = 0.58f))
            .cyberFrame()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun ChatStatusPanel(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    ChatContextPanel(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondaryOnDark
        )
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = VitalCyan,
            trackColor = SurfaceStroke
        )
    }
}

@Composable
fun ChatHistoryPanel(
    isEmpty: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(SurfaceDeepPurple.copy(alpha = 0.34f))
            .cyberFrame()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isEmpty) {
            Text(
                text = emptyMessage,
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark,
                textAlign = TextAlign.Center
            )
        } else {
            content()
        }
    }
}

@Composable
fun ChatMessageBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    authorLabel: String? = null,
    contextLabel: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    val bubbleColor = if (isUser) {
        VitalCyan.copy(alpha = 0.12f)
    } else {
        SurfaceElevatedPurple.copy(alpha = 0.72f)
    }
    val outlineColor = if (isUser) VitalCyan.copy(alpha = 0.72f) else SurfaceStroke
    val messageModifier = if (onLongClick != null) {
        modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
    } else {
        modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = messageModifier.fillMaxWidth(0.84f),
            color = bubbleColor,
            shape = CutCornerShape(8.dp),
            border = BorderStroke(1.dp, outlineColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                authorLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) VitalCyan else VitalPurpleBright
                    )
                }
                contextLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryOnDark
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimaryOnDark
                )
            }
        }
    }
}

@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    sendLabel: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    maxLength: Int? = null,
    errorMessage: String? = null
) {
    val canSend = enabled && !sending && value.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDeepPurple.copy(alpha = 0.72f))
            .cyberFrame()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = { next ->
                    onValueChange(maxLength?.let(next::take) ?: next)
                },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                placeholder = { Text(placeholder) },
                enabled = enabled && !sending,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 4,
                shape = CutCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = if (singleLine) ImeAction.Send else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (canSend) onSend() }
                ),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = TextPrimaryOnDark,
                    unfocusedTextColor = TextPrimaryOnDark,
                    disabledTextColor = TextMutedOnDark,
                    focusedContainerColor = SurfaceElevatedPurple,
                    unfocusedContainerColor = SurfaceElevatedPurple,
                    disabledContainerColor = SurfaceElevatedPurple,
                    cursorColor = VitalCyan,
                    focusedIndicatorColor = VitalCyan,
                    unfocusedIndicatorColor = SurfaceStroke,
                    disabledIndicatorColor = SurfaceStroke,
                    focusedPlaceholderColor = TextSecondaryOnDark,
                    unfocusedPlaceholderColor = TextSecondaryOnDark
                )
            )
            VitalButton(
                onClick = onSend,
                modifier = Modifier.heightIn(min = 52.dp),
                enabled = canSend,
                borderColor = if (canSend) VitalCyan else SurfaceStroke,
                contentColor = if (canSend) VitalCyan else TextSecondaryOnDark,
                disabledContentColor = TextMutedOnDark,
                containerColor = if (canSend) VitalCyan.copy(alpha = 0.1f) else SurfaceDeepPurple,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = VitalCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(sendLabel)
            }
        }
    }
}
