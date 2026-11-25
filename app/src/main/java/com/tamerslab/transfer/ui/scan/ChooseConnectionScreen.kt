package com.tamerslab.transfer.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.TopBanner
import com.tamerslab.transfer.ui.scan.components.TransferDirection
import com.tamerslab.transfer.ui.scan.components.TransferDirectionOption
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * One transfer instrument, two directions. The old screen stacked an NFC explainer panel and two
 * text buttons; this is a single route choice with the stage and the reason the action is available.
 */
@Composable
fun ChooseConnectOption(
    onClickRead: (() -> Unit)? = null,
    onClickWrite: (() -> Unit)? = null,
    feedbackMessage: String? = null,
    onFeedbackAction: (() -> Unit)? = null,
    onDismissFeedback: (() -> Unit)? = null,
    navController: NavController,
    feedbackActionLabel: Int = R.string.ui_settings,
    showHeader: Boolean = true,
) {
    Scaffold(
        contentWindowInsets = if (showHeader) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showHeader) TopBanner(
                text = stringResource(R.string.scan_title),
                onBackClick = { navController.popBackStack() },
            )
        },
    ) { contentPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.transfer_choose_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryOnDark,
                )
                Text(
                    text = stringResource(R.string.transfer_choose_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark,
                )

                feedbackMessage?.let { message ->
                    CyberPanel(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        active = true,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimaryOnDark,
                        )
                        if (onFeedbackAction != null || onDismissFeedback != null) {
                            Spacer(Modifier.height(2.dp))
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                onFeedbackAction?.let { action ->
                                    TextButton(onClick = action) {
                                        Text(stringResource(feedbackActionLabel))
                                    }
                                }
                                onDismissFeedback?.let { dismiss ->
                                    TextButton(onClick = dismiss) {
                                        Text(stringResource(R.string.ui_ok))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                TransferDirectionOption(
                    direction = TransferDirection.RECEIVE,
                    title = stringResource(R.string.scan_vb_to_app),
                    description = stringResource(
                        if (onClickRead != null) {
                            R.string.transfer_receive_description
                        } else {
                            R.string.transfer_unavailable
                        },
                    ),
                    enabled = onClickRead != null,
                    onClick = { onClickRead?.invoke() },
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    color = com.tamerslab.ui.theme.SurfaceStroke.copy(alpha = 0.5f),
                )
                TransferDirectionOption(
                    direction = TransferDirection.SEND,
                    title = stringResource(R.string.scan_app_to_vb),
                    description = stringResource(
                        if (onClickWrite != null) {
                            R.string.transfer_send_description
                        } else {
                            R.string.transfer_send_unavailable
                        },
                    ),
                    enabled = onClickWrite != null,
                    onClick = { onClickWrite?.invoke() },
                )

                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.scan_supported_devices),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedOnDark,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}
