package com.tamerslab.transfer.ui.scan.screens

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.ActivityLifecycleListener
import com.tamerslab.transfer.ui.scan.SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER
import com.tamerslab.transfer.ui.scan.ScanScreenController
import androidx.compose.ui.res.stringResource
import com.tamerslab.R

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

    var writing by remember { mutableStateOf(false) }
    var writingScreen by remember { mutableStateOf(false) }
    var writingConfirmScreen by remember { mutableStateOf(false) }
    var isDoneSendingCard by remember { mutableStateOf(false) }
    var isDoneWritingCharacter by remember { mutableStateOf(false) }

    DisposableEffect(writing) {
        if (writing) {
            scanScreenController.registerActivityLifecycleListener(
                SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER,
                object : ActivityLifecycleListener {
                    override fun onPause() {
                        scanScreenController.cancelRead()
                    }

                    override fun onResume() {
                        if (!isDoneSendingCard) {
                            scanScreenController.onClickCheckCard(secrets!!, nfcCharacter) {
                                isDoneSendingCard = true
                            }
                        } else if (!isDoneWritingCharacter) {
                            scanScreenController.onClickWrite(secrets!!, nfcCharacter) {
                                isDoneWritingCharacter = true
                            }
                        }
                    }
                }
            )

            if (secrets != null) {
                if (!isDoneSendingCard) {
                    scanScreenController.onClickCheckCard(secrets!!, nfcCharacter) {
                        isDoneSendingCard = true
                    }
                } else if (!isDoneWritingCharacter) {
                    scanScreenController.onClickWrite(secrets!!, nfcCharacter) {
                        isDoneWritingCharacter = true
                    }
                }
            }
        }

        onDispose {
            if (writing) {
                scanScreenController.unregisterActivityLifecycleListener(
                    SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER
                )
                scanScreenController.cancelRead()
            }
        }
    }

    if (!writingScreen) {
        writing = false
        WriteCardScreen(
            characterId = characterId,
            onClickCancel = {
                scanScreenController.cancelRead()
                onCancel()
            },
            onClickConfirm = {
                writingScreen = true
            },
            showHeader = showHeader
        )
    } else if (!isDoneSendingCard) {
        writing = true
        ActionScreen(
            topBannerText = stringResource(R.string.sending_card_title),
            onClickCancel = {
                scanScreenController.cancelRead()
                onCancel()
            },
            showHeader = showHeader,
        )
    } else if (!writingConfirmScreen) {
        writing = false
        WriteCharacterScreen(
            characterId = characterId,
            onClickCancel = {
                scanScreenController.cancelRead()
                onCancel()
            },
            onClickConfirm = {
                writingConfirmScreen = true
            },
            showHeader = showHeader
        )
    } else if (!isDoneWritingCharacter) {
        writing = true
        ActionScreen(
            topBannerText = stringResource(R.string.writing_character_action_title),
            onClickCancel = {
                isDoneSendingCard = false
                scanScreenController.cancelRead()
                onCancel()
            },
            showHeader = showHeader,
        )
    }

    var completedWriting by remember { mutableStateOf(false) }

    LaunchedEffect(isDoneSendingCard, isDoneWritingCharacter) {
        if (isDoneSendingCard && isDoneWritingCharacter) {
            // Persistence is completed by the controller before this callback.
            completedWriting = true
        }
    }

    if (completedWriting) {
        onComplete()
    }
}

