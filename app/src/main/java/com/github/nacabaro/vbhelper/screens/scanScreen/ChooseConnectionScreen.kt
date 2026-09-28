package com.github.nacabaro.vbhelper.screens.scanScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.cyberFrame
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
fun ChooseConnectOption(
    onClickRead: (() -> Unit)? = null,
    onClickWrite: (() -> Unit)? = null,
    feedbackMessage: String? = null,
    onFeedbackAction: (() -> Unit)? = null,
    onDismissFeedback: (() -> Unit)? = null,
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.scan_title),
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    ) { contentPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp)
        ) {
            feedbackMessage?.let { message ->
                CyberPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    active = true
                ) {
                    Text(message, color = TextPrimaryOnDark)
                    if (onFeedbackAction != null) {
                        TextButton(onClick = onFeedbackAction) {
                            Text(stringResource(R.string.ui_settings), color = StatusRed)
                        }
                    }
                    if (onDismissFeedback != null) {
                        TextButton(onClick = onDismissFeedback) {
                            Text(stringResource(R.string.ui_ok), color = TextPrimaryOnDark)
                        }
                    }
                }
            }
            CyberPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                active = true
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    tint = VitalCyan,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = stringResource(R.string.scan_supported_devices),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryOnDark
                )
                Text(
                    text = stringResource(R.string.scan_direction_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ScanButton(
                text = stringResource(R.string.scan_vb_to_app),
                disabled = onClickRead == null,
                onClick = onClickRead?: {  },
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
            )
            ScanButton(
                text = stringResource(R.string.scan_app_to_vb),
                disabled = onClickWrite == null,
                onClick = onClickWrite?: {  },
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
            )
        }
    }
}


@Composable
fun ScanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
) {
    VitalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = !disabled,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(4.dp)
        )
    }
}
