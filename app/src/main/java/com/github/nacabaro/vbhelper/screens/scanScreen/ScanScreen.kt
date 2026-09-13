package com.github.nacabaro.vbhelper.screens.scanScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.ActivityLifecycleListener
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.scanScreen.screens.ReadingScreen
import com.github.nacabaro.vbhelper.screens.scanScreen.screens.WritingScreen
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.source.isMissingSecrets
import com.github.nacabaro.vbhelper.source.proto.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import com.github.nacabaro.vbhelper.R

const val SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER = "SCAN_SCREEN_ACTIVITY_LIFECYCLE_LISTENER"

@Composable
fun ScanScreen(
    navController: NavController,
    characterId: Long?,
    scanScreenController: ScanScreenController,
    launchedFromHomeScreen: Boolean
) {
    val secrets by scanScreenController.secretsFlow.collectAsState(null)

    val application = LocalContext.current.applicationContext as VBHelper
    val storageRepository = StorageRepository(application.container.db)
    var nfcCharacter by remember { mutableStateOf<NfcCharacter?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackRequiresSettings by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(characterId) {
        withContext(Dispatchers.IO) {
            if (characterId != null && nfcCharacter == null) {
                try {
                    nfcCharacter = scanScreenController.characterToNfc(characterId)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    withContext(Dispatchers.Main) {
                        feedbackMessage = failure.message ?: "Transfer preparation failed"
                        feedbackRequiresSettings = false
                    }
                }
            }
        }
    }

    var writingScreen by remember { mutableStateOf(false) }
    var readingScreen by remember { mutableStateOf(false) }

    if (writingScreen && nfcCharacter != null && characterId != null) {
        WritingScreen(
            scanScreenController = scanScreenController,
            nfcCharacter = nfcCharacter!!,
            characterId = characterId,
            onComplete = {
                writingScreen = false
                navController.navigate(NavigationItems.Home.route)
            },
            onCancel = {
                writingScreen = false
                navController.navigate(NavigationItems.Home.route)
            }
        )
    } else if (readingScreen) {
        ReadingScreen(
            scanScreenController = scanScreenController,
            onCancel = {
                readingScreen = false
                navController.navigate(NavigationItems.Home.route)
            },
            onComplete = {
                readingScreen = false
                navController.navigate(NavigationItems.Home.route)
            }
        )
    } else {
        ChooseConnectOption(
            onClickRead = when {
                !launchedFromHomeScreen -> null
                else -> {
                    {
                        if(secrets == null) {
                            feedbackMessage = context.getString(R.string.scan_secrets_not_initialized)
                            feedbackRequiresSettings = true
                        } else if(secrets?.isMissingSecrets() == true) {
                            feedbackMessage = context.getString(R.string.scan_secrets_not_imported)
                            feedbackRequiresSettings = true
                        } else {
                            feedbackMessage = null
                            feedbackRequiresSettings = false
                            readingScreen = true // kicks off nfc adapter in DisposableEffect
                        }
                    }
                }
            },
            onClickWrite = when {
                nfcCharacter == null -> null
                else -> {
                    {
                        if(secrets == null) {
                            feedbackMessage = context.getString(R.string.scan_secrets_not_initialized)
                            feedbackRequiresSettings = true
                        } else if(secrets?.isMissingSecrets() == true) {
                            feedbackMessage = context.getString(R.string.scan_secrets_not_imported)
                            feedbackRequiresSettings = true
                        } else {
                            feedbackMessage = null
                            feedbackRequiresSettings = false
                            writingScreen = true // kicks off nfc adapter in DisposableEffect
                        }
                    }
                }
            },
            navController = navController,
            feedbackMessage = feedbackMessage,
            onFeedbackAction = feedbackMessage?.takeIf { feedbackRequiresSettings }?.let {
                { navController.navigate(NavigationItems.Settings.route) }
            },
            onDismissFeedback = { feedbackMessage = null }
        )
    }
}



@Preview(showBackground = true)
@Composable
fun ScanScreenPreview() {
    ScanScreen(
        navController = rememberNavController(),
        scanScreenController = object: ScanScreenController {
            override val secretsFlow = MutableStateFlow<Secrets>(Secrets.getDefaultInstance())
            override fun unregisterActivityLifecycleListener(key: String) { }
            override fun registerActivityLifecycleListener(
                key: String,
                activityLifecycleListener: ActivityLifecycleListener
            ) {

            }
            override fun flushCharacter(cardId: Long) {}
            override fun onClickRead(secrets: Secrets, onComplete: ()->Unit, onMultipleCards: (List<Card>) -> Unit) {}
            override fun onClickCheckCard(secrets: Secrets, nfcCharacter: NfcCharacter, onComplete: () -> Unit) {}
            override fun onClickWrite(secrets: Secrets, nfcCharacter: NfcCharacter, onComplete: () -> Unit) {}
            override fun cancelRead() {}
            override fun characterFromNfc(nfcCharacter: NfcCharacter, onMultipleCards: (List<Card>, NfcCharacter) -> Unit): String { return "" }
            override suspend fun characterToNfc(characterId: Long): NfcCharacter? { return null }
        },
        characterId = null,
        launchedFromHomeScreen = false
    )
}
