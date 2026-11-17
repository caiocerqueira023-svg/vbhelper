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
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.card.Card
import com.tamerslab.species.data.ScanRepository
import com.tamerslab.transfer.ui.scan.SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER
import com.tamerslab.transfer.ui.scan.ScanScreenController
import com.tamerslab.transfer.ui.scan.components.NfcTransferDirection
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.ImageBitmapData
import com.tamerslab.utils.getImageBitmap
import androidx.compose.ui.platform.LocalContext
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

    // The outgoing card artwork lets the hand-off animation show the actual Digimon travelling to
    // the watch instead of the generic DigiCore fallback. It reuses the same source the two write
    // steps already render, so no extra lookup is introduced for the user.
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication
    val scanRepository = remember { ScanRepository(application.container.db) }
    val cardDetails by scanRepository.getCardDetails(characterId).collectAsStateWithLifecycle(
        initialValue = Card(
            id = 0,
            cardId = 0,
            logo = byteArrayOf(),
            logoWidth = 0,
            logoHeight = 0,
            name = "",
            stageCount = 0,
            isBEm = false,
        )
    )
    val characterPreview: ImageBitmapData? = remember(
        cardDetails.logoWidth,
        cardDetails.logoHeight,
        cardDetails.logo,
    ) {
        if (cardDetails.logoWidth > 0 && cardDetails.logoHeight > 0) {
            BitmapData(
                bitmap = cardDetails.logo,
                width = cardDetails.logoWidth,
                height = cardDetails.logoHeight,
            ).getImageBitmap(context = context, multiplier = 4, obscure = false)
        } else {
            null
        }
    }

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
            direction = NfcTransferDirection.APP_TO_WATCH,
            showHeader = showHeader,
            characterPreview = characterPreview,
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
            direction = NfcTransferDirection.APP_TO_WATCH,
            showHeader = showHeader,
            characterPreview = characterPreview,
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

