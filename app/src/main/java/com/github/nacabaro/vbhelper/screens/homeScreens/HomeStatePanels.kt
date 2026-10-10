package com.github.nacabaro.vbhelper.screens.homeScreens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.VitalButtonStyle

@Composable
fun HomeLoadingPanel(partner: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text(stringResource(if (partner) R.string.app_loading_partner else R.string.app_loading_collection),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

/** The checklist describes real persisted readiness, not a synthetic tutorial flag. */
@Composable
fun HomeSetupPanel(
    cardsReady: Boolean,
    connectionReady: Boolean,
    onImportCards: () -> Unit,
    onImportConnection: () -> Unit,
    onRead: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    importingConnection: Boolean = false,
) {
    Column(modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_setup_title), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.app_setup_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.app_setup_progress, listOf(cardsReady, connectionReady).count { it }, 3),
                style = MaterialTheme.typography.labelLarge)
            HorizontalDivider()
            SetupStep(stringResource(R.string.app_setup_cards), stringResource(R.string.app_setup_cards_hint), cardsReady)
            SetupStep(stringResource(R.string.app_setup_connection), stringResource(R.string.app_setup_connection_hint), connectionReady)
            SetupStep(stringResource(R.string.app_setup_partner), stringResource(R.string.app_setup_partner_hint), false)
        }
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val action = when {
                !cardsReady -> onImportCards
                !connectionReady -> onImportConnection
                else -> onRead
            }
            val label = when {
                !cardsReady -> R.string.app_setup_cards
                !connectionReady -> R.string.app_import_connection
                else -> R.string.app_scan_first
            }
            VitalButton(onClick = action, enabled = !importingConnection, style = VitalButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth().testTag("home-setup-primary")) {
                Text(stringResource(if (importingConnection) R.string.app_connection_importing else label))
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().testTag("home-setup-skip")) { Text(stringResource(R.string.app_setup_skip)) }
        }
    }
}

@Composable
private fun SetupStep(title: String, explanation: String, ready: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(if (ready) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if (ready) R.string.app_setup_ready else R.string.app_setup_pending),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
