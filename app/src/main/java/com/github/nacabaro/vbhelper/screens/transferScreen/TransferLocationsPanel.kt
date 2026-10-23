package com.github.nacabaro.vbhelper.screens.transferScreen

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.VitalSectionHeader
import com.github.nacabaro.vbhelper.components.showAppFeedback
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.transfer.LinkedSaveLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TransferLocationsPanel() {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    val locations by app.container.saveLocations.locations.collectAsState(initial = emptyList())
    var pairing by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<LinkedSaveLocation?>(null) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pairing = true else context.showAppFeedback(R.string.pc_local_permission)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VitalButton(onClick = {
            if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED)
                permission.launch("android.permission.ACCESS_LOCAL_NETWORK") else pairing = true
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pc_connect)) }
        if (locations.isNotEmpty()) {
            VitalSectionHeader(title = stringResource(R.string.transfer_locations))
            Text(stringResource(R.string.transfer_locations_hint), style = MaterialTheme.typography.bodySmall)
            locations.forEach { location ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(location.label, style = MaterialTheme.typography.titleSmall)
                        location.lastError?.let { Text(stringResource(saveErrorResource(it)), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall) }
                        if (location.truncated) Text(stringResource(R.string.transfer_folder_truncated), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { forgetting = location }) { Text(stringResource(R.string.transfer_stop_discovery)) }
                }
            }
        }
    }
    forgetting?.let { target ->
        AlertDialog(onDismissRequest = { forgetting = null }, title = { Text(stringResource(R.string.transfer_stop_discovery)) },
            text = { Text(stringResource(R.string.transfer_stop_discovery_confirm)) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try { app.container.saveLocations.forget(target) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { context.showAppFeedback(R.string.transfer_action_failed) }
                }
                forgetting = null
            }) { Text(stringResource(R.string.transfer_stop_discovery)) } },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text(stringResource(R.string.ui_cancel)) } })
    }
    if (pairing) PcPairingDialog(onDismiss = { pairing = false })
}

@Composable
internal fun PcPairingDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as VBHelper
    val defaultLabel = stringResource(R.string.pc_default_label)
    var label by remember { mutableStateOf(defaultLabel) }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.pc_connect)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pc_pairing_hint))
            OutlinedTextField(label, { label = it.take(160) }, label = { Text(stringResource(R.string.transfer_rename_title)) }, singleLine = true)
            OutlinedTextField(link, { link = it.take(2048) }, label = { Text(stringResource(R.string.pc_pairing_link)) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
            if (busy) CircularProgressIndicator()
            if (failed) Text(stringResource(R.string.pc_pairing_failed), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy && label.isNotBlank() && link.isNotBlank(), onClick = {
        busy = true; failed = false
        scope.launch {
            try {
                val connection = app.container.localCompanion.pair(link, label)
                app.container.saveLocations.linkPc(connection.connectionId, connection.label)
                app.container.linkedGameSaves.refreshAll()
                link = ""
                onDismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }) { Text(stringResource(R.string.pc_pair)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } })
}
