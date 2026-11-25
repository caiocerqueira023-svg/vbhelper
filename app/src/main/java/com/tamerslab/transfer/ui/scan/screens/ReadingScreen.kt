package com.tamerslab.transfer.ui.scan.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamerslab.ActivityLifecycleListener
import com.tamerslab.R
import com.tamerslab.di.AppServices
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.card.Card
import com.tamerslab.species.ui.card.ChooseCard
import com.tamerslab.transfer.ui.scan.SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER
import com.tamerslab.transfer.ui.scan.ScanScreenController
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.transfer.ui.scan.components.ArenaTransferSuccessVisual
import com.tamerslab.transfer.ui.scan.components.TransferActionDock
import com.tamerslab.transfer.ui.scan.components.TransferDirection
import com.tamerslab.transfer.ui.scan.components.TransferStagePhase
import com.tamerslab.transfer.ui.scan.components.ArenaTransferStage
import com.tamerslab.transfer.ui.scan.components.TransferSubject
import com.tamerslab.transfer.ui.scan.components.latestImportedSubject
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private enum class ReadPhase { INTRO, SCANNING, CHOOSING, SUCCESS }

@Composable
fun ReadingScreen(
    scanScreenController: ScanScreenController,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    showHeader: Boolean = true,
) {
    val secrets by scanScreenController.secretsFlow.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication
    val storageRepository = remember(application) { application.container.storageRepository }
    val scope = rememberCoroutineScope()
    val debugEnabled = AppServices.debugSettingsRepository.enabled

    var phase by remember { mutableStateOf(ReadPhase.INTRO) }
    var cardsRead by remember { mutableStateOf<List<Card>?>(null) }
    var readDone by remember { mutableStateOf(false) }
    var baselineMaxId by remember { mutableStateOf(0L) }
    var successSubject by remember { mutableStateOf<TransferSubject?>(null) }

    fun cancelTransfer() {
        scanScreenController.cancelRead()
        onCancel()
    }

    fun startRead() {
        if (secrets == null) {
            Toast.makeText(context, R.string.scan_secrets_not_initialized, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            baselineMaxId = withContext(Dispatchers.IO) {
                storageRepository.getAllCharacters().first().maxOfOrNull { it.id } ?: 0L
            }
            readDone = false
            phase = ReadPhase.SCANNING
        }
    }

    DisposableEffect(phase) {
        val scanning = phase == ReadPhase.SCANNING
        if (scanning) {
            scanScreenController.registerActivityLifecycleListener(
                SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER,
                object : ActivityLifecycleListener {
                    override fun onPause() {
                        scanScreenController.cancelRead()
                    }

                    override fun onResume() {
                        val currentSecrets = secrets
                        if (currentSecrets != null) {
                            scanScreenController.onClickRead(
                                secrets = currentSecrets,
                                onComplete = { readDone = true },
                                onMultipleCards = { cards ->
                                    cardsRead = cards
                                    phase = ReadPhase.CHOOSING
                                },
                            )
                        }
                    }
                },
            )
            val currentSecrets = secrets
            if (currentSecrets != null) {
                scanScreenController.onClickRead(
                    secrets = currentSecrets,
                    onComplete = { readDone = true },
                    onMultipleCards = { cards ->
                        cardsRead = cards
                        phase = ReadPhase.CHOOSING
                    },
                )
            }
        }
        onDispose {
            if (scanning) {
                scanScreenController.unregisterActivityLifecycleListener(
                    SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER,
                )
                scanScreenController.cancelRead()
            }
        }
    }

    LaunchedEffect(readDone, phase) {
        if (readDone && phase == ReadPhase.SCANNING) {
            successSubject = withContext(Dispatchers.IO) {
                latestImportedSubject(context, baselineMaxId)
            }
            phase = ReadPhase.SUCCESS
        }
    }

    when (phase) {
        ReadPhase.INTRO -> ReadIntroPage(
            showHeader = showHeader,
            onStart = ::startRead,
            onCancel = ::cancelTransfer,
        )
        ReadPhase.SCANNING -> ReadWaitingPage(
            showHeader = showHeader,
            debugEnabled = debugEnabled,
            onCancel = ::cancelTransfer,
            onSimulate = {
                scanScreenController.simulateRead(
                    onComplete = { readDone = true },
                    onError = { failure ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.debug_simulate_failed,
                                failure.message ?: failure.javaClass.simpleName,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            },
        )
        ReadPhase.CHOOSING -> ChooseCard(
            cards = cardsRead.orEmpty(),
            onCardSelected = { card ->
                scanScreenController.flushCharacter(card.id)
                cardsRead = null
                readDone = false
                phase = ReadPhase.SCANNING
            },
            showHeader = showHeader,
            onCancel = ::cancelTransfer,
        )
        ReadPhase.SUCCESS -> ReadSuccessPage(
            showHeader = showHeader,
            subject = successSubject,
            onContinue = onComplete,
        )
    }
}

@Composable
private fun ReadIntroPage(
    showHeader: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    ScanStepScaffold(
        title = stringResource(R.string.read_character_title),
        showHeader = showHeader,
        onBack = onCancel,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArenaTransferStage(
                direction = TransferDirection.RECEIVE,
                phase = TransferStagePhase.READY,
                subject = null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.transfer_read_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
            TransferActionDock(
                primaryLabel = stringResource(R.string.transfer_read_start),
                onPrimary = onStart,
                secondaryLabel = stringResource(R.string.ui_cancel),
                onSecondary = onCancel,
            )
        }
    }
}

@Composable
private fun ReadWaitingPage(
    showHeader: Boolean,
    debugEnabled: Boolean,
    onCancel: () -> Unit,
    onSimulate: () -> Unit,
) {
    ScanStepScaffold(
        title = stringResource(R.string.read_character_title),
        showHeader = showHeader,
        onBack = onCancel,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArenaTransferStage(
                direction = TransferDirection.RECEIVE,
                phase = TransferStagePhase.WAITING,
                subject = null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.transfer_wait_read_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimaryOnDark,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.transfer_wait_read_body),
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
private fun ReadSuccessPage(
    showHeader: Boolean,
    subject: TransferSubject?,
    onContinue: () -> Unit,
) {
    ScanStepScaffold(
        title = stringResource(R.string.read_character_title),
        showHeader = showHeader,
        onBack = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArenaTransferSuccessVisual(
                direction = TransferDirection.RECEIVE,
                subject = subject,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.transfer_success_receive_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimaryOnDark,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.transfer_success_receive_body),
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
