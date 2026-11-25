package com.tamerslab.transfer.ui.scan.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamerslab.ActivityLifecycleListener
import com.tamerslab.R
import com.tamerslab.di.AppServices
import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.transfer.ui.scan.SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER
import com.tamerslab.transfer.ui.scan.ScanScreenController
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.transfer.ui.scan.components.ArenaTransferSuccessVisual
import com.tamerslab.transfer.ui.scan.components.TransferActionDock
import com.tamerslab.transfer.ui.scan.components.TransferDirection
import com.tamerslab.transfer.ui.scan.components.TransferStagePhase
import com.tamerslab.transfer.ui.scan.components.ArenaTransferStage
import com.tamerslab.transfer.ui.scan.components.TransferSubject
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.DimLogo
import com.tamerslab.ui.theme.DeepPurpleBgAlt
import com.tamerslab.transfer.ui.scan.components.loadTransferSubject
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class WritePhase { REVIEW, WAITING_CARD, READY, WAITING_WRITE, SUCCESS }

private enum class WriteScan { CARD, WRITE }

@Composable
fun WritingScreen(
    scanScreenController: ScanScreenController,
    characterId: Long,
    nfcCharacter: NfcCharacter,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    showHeader: Boolean = true,
) {
    val secrets by scanScreenController.secretsFlow.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val debugEnabled = AppServices.debugSettingsRepository.enabled

    var phase by remember { mutableStateOf(WritePhase.REVIEW) }
    var scanMode by remember { mutableStateOf<WriteScan?>(null) }
    var subject by remember { mutableStateOf<TransferSubject?>(null) }
    var subjectLoading by remember { mutableStateOf(true) }

    LaunchedEffect(characterId) {
        subject = withContext(Dispatchers.IO) { loadTransferSubject(context, characterId) }
        subjectLoading = false
    }

    fun reportSimulationFailure(failure: Throwable) {
        Toast.makeText(
            context,
            context.getString(
                R.string.debug_simulate_failed,
                failure.message ?: failure.javaClass.simpleName,
            ),
            Toast.LENGTH_LONG,
        ).show()
    }

    fun cancelTransfer() {
        scanMode = null
        scanScreenController.cancelRead()
        onCancel()
    }

    fun runStep(mode: WriteScan) {
        val currentSecrets = secrets ?: return
        when (mode) {
            WriteScan.CARD -> scanScreenController.onClickCheckCard(currentSecrets, nfcCharacter) {
                scanMode = null
                phase = WritePhase.READY
            }
            WriteScan.WRITE -> scanScreenController.onClickWrite(currentSecrets, nfcCharacter) {
                scanMode = null
                phase = WritePhase.SUCCESS
            }
        }
    }

    DisposableEffect(scanMode) {
        val mode = scanMode
        if (mode != null) {
            scanScreenController.registerActivityLifecycleListener(
                SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER,
                object : ActivityLifecycleListener {
                    override fun onPause() {
                        scanScreenController.cancelRead()
                    }

                    override fun onResume() {
                        runStep(mode)
                    }
                },
            )
            runStep(mode)
        }
        onDispose {
            if (mode != null) {
                scanScreenController.unregisterActivityLifecycleListener(
                    SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER,
                )
                scanScreenController.cancelRead()
            }
        }
    }

    fun beginCardCheck() {
        phase = WritePhase.WAITING_CARD
        scanMode = WriteScan.CARD
    }

    fun beginWrite() {
        phase = WritePhase.WAITING_WRITE
        scanMode = WriteScan.WRITE
    }

    when (phase) {
        WritePhase.REVIEW -> WriteReviewPage(
            showHeader = showHeader,
            subject = subject,
            loading = subjectLoading,
            onVerify = ::beginCardCheck,
            onCancel = ::cancelTransfer,
        )
        WritePhase.WAITING_CARD -> WriteWaitingPage(
            showHeader = showHeader,
            screenTitle = stringResource(R.string.sending_card_title),
            step = 1,
            title = stringResource(R.string.transfer_wait_card_title),
            body = stringResource(R.string.transfer_wait_card_body),
            subject = subject,
            onCancel = ::cancelTransfer,
            debugEnabled = debugEnabled,
            onSimulate = {
                scanMode = null
                scanScreenController.simulateCheckCard(
                    nfcCharacter = nfcCharacter,
                    onComplete = { phase = WritePhase.READY },
                    onError = ::reportSimulationFailure,
                )
            },
        )
        WritePhase.READY -> WriteReadyPage(
            showHeader = showHeader,
            subject = subject,
            onSend = ::beginWrite,
            onCancel = ::cancelTransfer,
        )
        WritePhase.WAITING_WRITE -> WriteWaitingPage(
            showHeader = showHeader,
            screenTitle = stringResource(R.string.writing_character_action_title),
            step = 2,
            title = stringResource(R.string.transfer_wait_write_title),
            body = stringResource(R.string.transfer_wait_write_body),
            subject = subject,
            onCancel = ::cancelTransfer,
            debugEnabled = debugEnabled,
            onSimulate = {
                scanMode = null
                scanScreenController.simulateWrite(
                    nfcCharacter = nfcCharacter,
                    onComplete = { phase = WritePhase.SUCCESS },
                    onError = ::reportSimulationFailure,
                )
            },
        )
        WritePhase.SUCCESS -> WriteSuccessPage(
            showHeader = showHeader,
            subject = subject,
            onContinue = onComplete,
        )
    }
}

@Composable
private fun DimCardPanel(
    subject: TransferSubject?,
    title: String,
    body: String,
    status: String,
) {
    CyberPanel(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(top = 12.dp),
        active = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            subject?.cardBitmap?.let { cardBitmap ->
                Surface(
                    color = DeepPurpleBgAlt,
                    shape = RectangleShape,
                    modifier = Modifier.size(96.dp),
                ) {
                    DimLogo(
                        bitmap = cardBitmap,
                        contentDescription = stringResource(R.string.write_card_icon_description),
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryOnDark,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = VitalCyan,
                )
            }
        }
    }
}

@Composable
private fun WriteReviewPage(
    showHeader: Boolean,
    subject: TransferSubject?,
    loading: Boolean,
    onVerify: () -> Unit,
    onCancel: () -> Unit,
) {
    ScanStepScaffold(
        title = stringResource(R.string.write_card_title),
        showHeader = showHeader,
        onBack = onCancel,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArenaTransferStage(
                direction = TransferDirection.SEND,
                phase = TransferStagePhase.READY,
                subject = subject,
                modifier = Modifier.fillMaxWidth(),
            )
            DimCardPanel(
                subject = subject,
                title = stringResource(R.string.write_card_device_ready),
                body = stringResource(
                    R.string.write_card_required_card,
                    subject?.cardName ?: stringResource(R.string.transfer_card_unknown),
                ),
                status = stringResource(R.string.scan_card_link_ready),
            )
            Spacer(Modifier.height(14.dp))
            TransferActionDock(
                primaryLabel = stringResource(R.string.write_card_confirm),
                onPrimary = onVerify,
                primaryEnabled = !loading,
                secondaryLabel = stringResource(R.string.ui_cancel),
                onSecondary = onCancel,
            )
        }
    }
}

@Composable
private fun WriteReadyPage(
    showHeader: Boolean,
    subject: TransferSubject?,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    ScanStepScaffold(
        title = stringResource(R.string.write_character_title),
        showHeader = showHeader,
        onBack = onCancel,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArenaTransferStage(
                direction = TransferDirection.SEND,
                phase = TransferStagePhase.READY,
                subject = subject,
                modifier = Modifier.fillMaxWidth(),
            )
            DimCardPanel(
                subject = subject,
                title = stringResource(R.string.write_character_success),
                body = stringResource(R.string.write_character_wait_ready),
                status = stringResource(R.string.scan_nfc_link_ready),
            )
            Spacer(Modifier.height(14.dp))
            TransferActionDock(
                primaryLabel = stringResource(R.string.write_character_confirm),
                onPrimary = onSend,
                secondaryLabel = stringResource(R.string.ui_cancel),
                onSecondary = onCancel,
            )
        }
    }
}

@Composable
private fun WriteWaitingPage(
    showHeader: Boolean,
    screenTitle: String,
    step: Int,
    title: String,
    body: String,
    subject: TransferSubject?,
    onCancel: () -> Unit,
    debugEnabled: Boolean,
    onSimulate: () -> Unit,
) {
    ScanStepScaffold(
        title = screenTitle,
        showHeader = showHeader,
        onBack = onCancel,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArenaTransferStage(
                direction = TransferDirection.SEND,
                phase = TransferStagePhase.WAITING,
                subject = subject,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimaryOnDark,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Spacer(Modifier.height(14.dp))
            TransferActionDock(
                primaryLabel = stringResource(R.string.ui_cancel),
                onPrimary = onCancel,
                primaryStyle = VitalButtonStyle.SECONDARY,
                debugLabel = if (debugEnabled) {
                    stringResource(R.string.debug_simulate_watch_action)
                } else {
                    null
                },
                onDebug = if (debugEnabled) onSimulate else null,
            )
        }
    }
}

@Composable
private fun WriteSuccessPage(
    showHeader: Boolean,
    subject: TransferSubject?,
    onContinue: () -> Unit,
) {
    ScanStepScaffold(
        title = stringResource(R.string.write_character_title),
        showHeader = showHeader,
        onBack = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArenaTransferSuccessVisual(
                direction = TransferDirection.SEND,
                subject = subject,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.transfer_success_send_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimaryOnDark,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.transfer_success_send_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Spacer(Modifier.height(12.dp))
            TransferActionDock(
                primaryLabel = stringResource(R.string.transfer_continue),
                onPrimary = onContinue,
            )
        }
    }
}
