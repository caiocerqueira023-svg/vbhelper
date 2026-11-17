package com.tamerslab.hardware.vpet

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.UUID

/**
 * BTCom adapter over BLE, using the Nordic UART Service, ported from the recovered `BtCom`
 * class:
 * - service `6e400001-b5a3-f393-e0a9-e50e24dcca9e`;
 * - write characteristic (phone -> adapter) `6e400002-…`;
 * - notify characteristic (adapter -> phone) `6e400003-…`, enabled through the standard CCCD;
 * - 20-byte write chunks queued with `WRITE_TYPE_NO_RESPONSE`, retried after 60 ms.
 *
 * `onLine` is invoked from the Bluetooth callback thread; `onStatus` is posted to the main
 * thread.
 */
@SuppressLint("MissingPermission")
class BtComLink(
    context: Context,
    private val device: BluetoothDevice,
    onLine: (String) -> Unit,
    private val onStatus: (connected: Boolean, message: String) -> Unit,
) : VpetLink {

    private val context: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lines = LineAccumulator(onLine)
    private val writeQueue = ArrayDeque<ByteArray>()

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var writing = false
    private var inFlight: ByteArray? = null
    private var ready = false
    private var closed = false

    /**
     * Safety net for `WRITE_TYPE_NO_RESPONSE`: if the stack does not deliver
     * onCharacteristicWrite, release the slot after the same 60 ms retry window instead of
     * stalling the whole command.
     */
    private val writeWatchdog = Runnable {
        if (inFlight != null) {
            inFlight = null
            writing = false
            sendNextChunk()
        }
    }

    /** Starts the GATT connection; the session may only send after onStatus(true, …). */
    @Suppress("DEPRECATION")
    fun connect() {
        if (closed) closed = false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onStatus(false, "Turn on the phone Bluetooth and try again.")
            return
        }
        releaseGatt()
        writeQueue.clear()
        inFlight = null
        writing = false
        ready = false
        mainHandler.post { onStatus(false, "Connecting to the BTCom...") }
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    override fun send(text: String) {
        if (closed) return
        val bytes = text.toByteArray(StandardCharsets.ISO_8859_1)
        mainHandler.post {
            if (closed) return@post
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + CHUNK_SIZE, bytes.size)
                writeQueue.addLast(bytes.copyOfRange(offset, end))
                offset = end
            }
            sendNextChunk()
        }
    }

    override fun close() {
        closed = true
        ready = false
        mainHandler.removeCallbacks(writeWatchdog)
        writeQueue.clear()
        inFlight = null
        writing = false
        releaseGatt()
    }

    /** True once the UART service was found and notifications were enabled. */
    fun isConnected(): Boolean = ready

    private fun releaseGatt() {
        val connection = gatt ?: return
        gatt = null
        rxCharacteristic = null
        runCatching { connection.disconnect() }
        runCatching { connection.close() }
    }

    private fun sendNextChunk() {
        if (closed || writing) return
        val connection = gatt ?: return
        val characteristic = rxCharacteristic ?: return
        val chunk = writeQueue.pollFirst() ?: return
        writing = true
        inFlight = chunk
        mainHandler.removeCallbacks(writeWatchdog)
        mainHandler.postDelayed(writeWatchdog, RETRY_DELAY_MS)
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                connection.writeCharacteristic(
                    characteristic,
                    chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = chunk
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                connection.writeCharacteristic(characteristic)
            }
        } catch (error: RuntimeException) {
            false
        }
        if (!started) {
            mainHandler.removeCallbacks(writeWatchdog)
            inFlight = null
            writing = false
            mainHandler.postDelayed({
                if (!closed) {
                    writeQueue.addFirst(chunk)
                    sendNextChunk()
                }
            }, RETRY_DELAY_MS)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post {
                if (closed) return@post
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> connection.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        ready = false
                        onStatus(false, "The BTCom disconnected.")
                        releaseGatt()
                    }
                }
            }
        }

        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
            mainHandler.post {
                if (closed) return@post
                val service = connection.getService(NUS)
                val rx = service?.getCharacteristic(RX)
                val tx = service?.getCharacteristic(TX)
                if (service == null || rx == null || tx == null) {
                    onStatus(false, "That device is not a BTCom (no UART service).")
                    connection.disconnect()
                    return@post
                }
                rxCharacteristic = rx
                connection.setCharacteristicNotification(tx, true)
                val descriptor = tx.getDescriptor(CCCD)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        connection.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        connection.writeDescriptor(descriptor)
                    }
                }
                ready = true
                onStatus(true, "BTCom connected. Tap Read and put your Vpet in BATTLE.")
            }
        }

        override fun onCharacteristicWrite(
            connection: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            mainHandler.post {
                mainHandler.removeCallbacks(writeWatchdog)
                val chunk = inFlight
                inFlight = null
                writing = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    // DBT-Link requeues the failed chunk after 60 ms before sending the next one.
                    mainHandler.postDelayed({
                        if (!closed && chunk != null) writeQueue.addFirst(chunk)
                        if (!closed) sendNextChunk()
                    }, RETRY_DELAY_MS)
                } else if (!closed) {
                    sendNextChunk()
                }
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            connection: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != TX) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            lines.append(String(value, StandardCharsets.ISO_8859_1))
        }

        override fun onCharacteristicChanged(
            connection: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != TX) return
            lines.append(String(value, StandardCharsets.ISO_8859_1))
        }
    }

    companion object {
        /** Nordic UART Service. */
        val NUS: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

        /** Phone-to-adapter write characteristic. */
        val RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

        /** Adapter-to-phone notify characteristic. */
        val TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        /** Standard Client Characteristic Configuration Descriptor. */
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val CHUNK_SIZE = 20
        private const val RETRY_DELAY_MS = 60L
    }
}
