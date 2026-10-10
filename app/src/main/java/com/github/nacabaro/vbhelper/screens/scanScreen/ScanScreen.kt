package com.github.nacabaro.vbhelper.screens.scanScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.components.ChatContextPanel
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
    val storageRepository = remember { StorageRepository(application.container.db) }
    var nfcCharacter by remember { mutableStateOf<NfcCharacter?>(null) }
    var feedbackMessage by rememberSaveable(characterId) { mutableStateOf<String?>(null) }
    var feedbackRequiresSettings by rememberSaveable(characterId) { mutableStateOf(false) }
    var prepareRetry by rememberSaveable(characterId) { mutableIntStateOf(0) }
    val transferStatus by scanScreenController.transferStatus.collectAsState(initial = null)

    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(characterId, prepareRetry) {
        withContext(Dispatchers.IO) {
            if (characterId != null && nfcCharacter == null) {
                try {
                    nfcCharacter = scanScreenController.characterToNfc(characterId)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    withContext(Dispatchers.Main) {
                        feedbackMessage = resources.getString(R.string.app_transfer_prepare_failed)
                        feedbackRequiresSettings = false
                    }
                }
            }
        }
    }

    var writingScreen by remember { mutableStateOf(false) }
    var readingScreen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
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
                            feedbackMessage = resources.getString(R.string.scan_secrets_not_initialized)
                            feedbackRequiresSettings = true
                        } else if(secrets?.isMissingSecrets() == true) {
                            feedbackMessage = resources.getString(R.string.scan_secrets_not_imported)
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
                            feedbackMessage = resources.getString(R.string.scan_secrets_not_initialized)
                            feedbackRequiresSettings = true
                        } else if(secrets?.isMissingSecrets() == true) {
                            feedbackMessage = resources.getString(R.string.scan_secrets_not_imported)
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
            onFeedbackAction = feedbackMessage?.let {
                if (feedbackRequiresSettings) ({ navController.navigate(NavigationItems.Settings.route) })
                else ({ feedbackMessage = null; prepareRetry++ })
            },
            feedbackActionLabel = if (feedbackRequiresSettings) R.string.ui_settings else R.string.app_retry,
            onDismissFeedback = { feedbackMessage = null }
        )
    }
    }
    transferStatus?.let { message ->
        ChatContextPanel(Modifier.padding(12.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.app_transfer_status), style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
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
