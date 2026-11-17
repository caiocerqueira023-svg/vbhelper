package com.tamerslab.transfer.ui.vpet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.VitalSectionHeader
import com.tamerslab.hardware.vpet.VpetBluetoothDiscovery
import com.tamerslab.hardware.vpet.VpetDeviceInfo
import com.tamerslab.hardware.vpet.VpetDeviceTransport
import com.tamerslab.ui.theme.StatusGreen
import com.tamerslab.ui.theme.StatusRed
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark

/**
 * Transport selection. USB is the first-class transport (D6): the A-Com adapter owns the system
 * permission dialog, so the button only has to call connect. Bluetooth stays available and asks
 * for its runtime permissions here before listing paired SPP adapters or scanning for BLE UARTs.
 */
@Composable
internal fun VpetConnectionPanel(
    coordinator: VpetReadCoordinator,
    modifier: Modifier = Modifier,
) {
    CyberPanel(modifier = modifier.fillMaxWidth(), active = coordinator.isConnected) {
        VitalSectionHeader(
            title = stringResource(R.string.vpet_connection_title),
            detail = when (coordinator.connectionState) {
                VpetConnectionState.CONNECTED -> coordinator.connectedTransport
                    ?.let { vpetTransportLabel(it) }
                    ?: stringResource(R.string.vpet_connected)
                VpetConnectionState.CONNECTING -> stringResource(R.string.vpet_connecting)
                VpetConnectionState.DISCONNECTED -> stringResource(R.string.vpet_disconnected)
            },
        )
        Text(
            text = stringResource(R.string.vpet_connect_usb_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
        when (coordinator.connectionState) {
            VpetConnectionState.CONNECTING -> LinearProgressIndicator(Modifier.fillMaxWidth())
            VpetConnectionState.CONNECTED -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.vpet_connected),
                    style = MaterialTheme.typography.titleSmall,
                    color = StatusGreen,
                    modifier = Modifier.weight(1f),
                )
                VitalButton(
                    onClick = coordinator::disconnect,
                    style = VitalButtonStyle.DESTRUCTIVE,
                ) {
                    Text(stringResource(R.string.vpet_disconnect))
                }
            }
            VpetConnectionState.DISCONNECTED -> VitalButton(
                onClick = coordinator::connectUsb,
                enabled = !coordinator.isReading,
                style = VitalButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vpet_connect_usb))
            }
        }
        coordinator.connectionMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (coordinator.isConnected) StatusGreen else StatusRed,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        VpetBluetoothPicker(
            enabled = !coordinator.isReading,
            onConnect = { device ->
                val target = device.bluetoothDevice
                if (target != null && device.transport != VpetDeviceTransport.USB_ACM) {
                    coordinator.connectBluetooth(target, device.transport)
                }
            },
        )
    }
}

@Composable
internal fun VpetBluetoothPicker(
    enabled: Boolean,
    onConnect: (VpetDeviceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val discovery = remember(context) { VpetBluetoothDiscovery(context.applicationContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var pairedDevices by remember { mutableStateOf<List<VpetDeviceInfo>>(emptyList()) }
    var foundDevices by remember { mutableStateOf<List<VpetDeviceInfo>>(emptyList()) }
    var pairedLoaded by remember { mutableStateOf(false) }
    var scanAttempted by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var scanFailed by remember { mutableStateOf(false) }

    fun loadPairedDevices() {
        permissionDenied = false
        pairedDevices = runCatching { discovery.pairedSppDevices() }.getOrElse { emptyList() }
        pairedLoaded = true
    }

    fun beginScan() {
        if (scanning) return
        permissionDenied = false
        scanning = true
        scanAttempted = true
        scanFailed = false
        foundDevices = emptyList()
        runCatching {
            discovery.startBleScan(
                onFound = { device ->
                    mainHandler.post {
                        foundDevices = (foundDevices + device).distinctBy { it.address }
                    }
                },
                onFinished = { mainHandler.post { scanning = false } },
            )
        }.onFailure {
            scanning = false
            scanFailed = true
        }
    }

    fun stopScan() {
        runCatching { discovery.stopScan() }
        scanning = false
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { discovery.stopScan() } }
    }

    val pairedPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { granted -> granted }) loadPairedDevices() else permissionDenied = true
    }
    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { granted -> granted }) beginScan() else permissionDenied = true
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.vpet_bluetooth_title),
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimaryOnDark,
        )
        Text(
            text = stringResource(R.string.vpet_bluetooth_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VitalButton(
                onClick = {
                    if (hasBluetoothPermissions(context)) {
                        loadPairedDevices()
                    } else {
                        pairedPermissionLauncher.launch(bluetoothPermissions())
                    }
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.vpet_bluetooth_show_paired))
            }
            VitalButton(
                onClick = {
                    if (scanning) {
                        stopScan()
                    } else if (hasBluetoothPermissions(context)) {
                        beginScan()
                    } else {
                        scanPermissionLauncher.launch(bluetoothPermissions())
                    }
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (scanning) R.string.vpet_bluetooth_stop_scan
                        else R.string.vpet_bluetooth_scan
                    )
                )
            }
        }
        when {
            permissionDenied -> Text(
                text = stringResource(R.string.vpet_bluetooth_permission_denied),
                style = MaterialTheme.typography.bodySmall,
                color = StatusRed,
            )
            scanFailed -> Text(
                text = stringResource(R.string.vpet_bluetooth_scan_failed),
                style = MaterialTheme.typography.bodySmall,
                color = StatusRed,
            )
        }
        if (scanning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.vpet_bluetooth_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark,
                )
            }
        }
        if (pairedDevices.isNotEmpty()) {
            Text(
                text = stringResource(R.string.vpet_bluetooth_paired_title),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryOnDark,
            )
            pairedDevices.forEach { device ->
                VpetBluetoothDeviceRow(device, enabled = enabled, onConnect = onConnect)
            }
        }
        if (foundDevices.isNotEmpty()) {
            Text(
                text = stringResource(R.string.vpet_bluetooth_found_title),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryOnDark,
            )
            foundDevices.forEach { device ->
                VpetBluetoothDeviceRow(device, enabled = enabled, onConnect = onConnect)
            }
        }
        if ((pairedLoaded || scanAttempted) && pairedDevices.isEmpty() && foundDevices.isEmpty() && !scanning) {
            Text(
                text = stringResource(R.string.vpet_bluetooth_no_devices),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
            )
        }
    }
}

@Composable
private fun VpetBluetoothDeviceRow(
    device: VpetDeviceInfo,
    enabled: Boolean,
    onConnect: (VpetDeviceInfo) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = device.name.takeIf { it.isNotBlank() } ?: device.address,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimaryOnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${device.address} · ${vpetTransportLabel(device.transport)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
            )
        }
        TextButton(onClick = { onConnect(device) }, enabled = enabled) {
            Text(stringResource(R.string.vpet_connect))
        }
    }
}

@Composable
internal fun vpetTransportLabel(transport: VpetDeviceTransport?): String = when (transport) {
    VpetDeviceTransport.USB_ACM -> stringResource(R.string.vpet_transport_usb)
    VpetDeviceTransport.BLE_UART -> stringResource(R.string.vpet_transport_ble)
    VpetDeviceTransport.BLUETOOTH_SPP -> stringResource(R.string.vpet_transport_spp)
    null -> stringResource(R.string.vpet_disconnected)
}

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasBluetoothPermissions(context: Context): Boolean = bluetoothPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
