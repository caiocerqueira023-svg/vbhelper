package com.tamerslab.hardware.vpet

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/**
 * Discovers VPet adapters, ported from the recovered `VpetActivity.listaBt`/`BtCom.startScan`
 * flow.
 *
 * - [pairedSppDevices] lists bonded devices that expose the SPP service (classic BTCom);
 * - [startBleScan] scans for BLE adapters advertising the Nordic UART Service (BLE BTCom) and
 *   reports them until [stopScan] or [BLE_SCAN_DURATION_MS].
 *
 * Permissions are the caller's responsibility: `BLUETOOTH_CONNECT` for the paired list and
 * `BLUETOOTH_SCAN` (API 31+) or `BLUETOOTH` + location (legacy) for the scan. Security
 * exceptions are swallowed and reported as an empty list / finished scan.
 */
@SuppressLint("MissingPermission")
class VpetBluetoothDiscovery(context: Context) {

    private val context: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var onFinished: (() -> Unit)? = null

    private val finishAction = Runnable { finishScan(notify = true) }

    /** Bonded devices that advertise the classic SPP service, sorted by display name. */
    fun pairedSppDevices(): List<VpetDeviceInfo> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty()
                .filter { supportsSpp(it) }
                .map { device ->
                    VpetDeviceInfo(
                        name = device.displayNameOrAddress(),
                        address = device.address,
                        transport = VpetDeviceTransport.BLUETOOTH_SPP,
                        bluetoothDevice = device,
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    /**
     * Scans for BLE UART adapters. [onFound] is invoked on the main thread for every new device;
     * [onFinished] is invoked once when the scan window elapses, the scanner fails or the
     * Bluetooth adapter is unavailable. Calling [startBleScan] again restarts the scan.
     */
    fun startBleScan(onFound: (VpetDeviceInfo) -> Unit, onFinished: () -> Unit) {
        stopScanInternal()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            mainHandler.post(onFinished)
            return
        }
        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            mainHandler.post(onFinished)
            return
        }
        val seenAddresses = HashSet<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                report(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) report(result)
            }

            override fun onScanFailed(errorCode: Int) {
                mainHandler.post { finishScan(notify = true) }
            }

            private fun report(result: ScanResult) {
                val device = result.device ?: return
                val address = device.addressOrNull() ?: return
                if (!seenAddresses.add(address)) return
                val name = result.scanRecord?.deviceName
                    ?: device.nameOrNull()
                    ?: address
                val info = VpetDeviceInfo(
                    name = name,
                    address = address,
                    transport = VpetDeviceTransport.BLE_UART,
                    bluetoothDevice = device,
                )
                mainHandler.post { onFound(info) }
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BtComLink.NUS)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            leScanner.startScan(listOf(filter), settings, callback)
        } catch (error: SecurityException) {
            mainHandler.post(onFinished)
            return
        }
        scanner = leScanner
        scanCallback = callback
        this.onFinished = onFinished
        mainHandler.postDelayed(finishAction, BLE_SCAN_DURATION_MS)
    }

    /** Stops an in-flight scan without invoking the finish callback. */
    fun stopScan() {
        stopScanInternal()
    }

    private fun finishScan(notify: Boolean) {
        val finished = onFinished
        stopScanInternal()
        if (notify) finished?.invoke()
    }

    private fun stopScanInternal() {
        mainHandler.removeCallbacks(finishAction)
        val activeScanner = scanner
        val activeCallback = scanCallback
        scanner = null
        scanCallback = null
        onFinished = null
        if (activeScanner != null && activeCallback != null) {
            runCatching { activeScanner.stopScan(activeCallback) }
        }
    }

    private fun supportsSpp(device: android.bluetooth.BluetoothDevice): Boolean = try {
        device.uuids.orEmpty().any { it.uuid == BtSerieLink.SPP }
    } catch (error: SecurityException) {
        false
    }

    private fun android.bluetooth.BluetoothDevice.displayNameOrAddress(): String =
        nameOrNull() ?: addressOrNull() ?: "Unknown device"

    private fun android.bluetooth.BluetoothDevice.nameOrNull(): String? =
        runCatching { name }.getOrNull()

    private fun android.bluetooth.BluetoothDevice.addressOrNull(): String? =
        runCatching { address }.getOrNull()

    companion object {
        /** How long [startBleScan] keeps the BLE scan running. */
        const val BLE_SCAN_DURATION_MS = 10_000L
    }
}
