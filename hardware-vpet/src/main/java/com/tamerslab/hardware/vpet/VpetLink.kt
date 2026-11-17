package com.tamerslab.hardware.vpet

import android.bluetooth.BluetoothDevice
import java.util.concurrent.ThreadFactory

/** Adapter kind a VPet can be read through. */
enum class VpetDeviceTransport {
    /** A-Com USB serial adapter (CDC/ACM), 9600 8N1. */
    USB_ACM,

    /** BTCom-style BLE adapter exposing the Nordic UART Service. */
    BLE_UART,

    /** Bluetooth classic SPP adapter. */
    BLUETOOTH_SPP,
}

/**
 * One discoverable adapter.
 *
 * @property bluetoothDevice underlying Bluetooth device, null for USB entries (USB devices are
 *   located directly by [AcomUsbLink.connect]).
 */
data class VpetDeviceInfo(
    val name: String,
    val address: String,
    val transport: VpetDeviceTransport,
    val bluetoothDevice: BluetoothDevice?,
)

/**
 * Text pipe to an adapter. Implementations move ASCII lines in both directions; they must
 * deliver received lines through the `onLine` callback passed to their constructor.
 */
interface VpetLink {
    /** Sends one command line to the adapter (DigiROM, Color block or "0"). */
    fun send(text: String)

    /** Releases the underlying connection. */
    fun close()
}

/**
 * Splits a byte stream into lines exactly like the recovered VpetActivity/AcomUsb/BtCom code:
 * accumulate text, split on `\n`, trim `\r` and spaces, drop empty lines.
 */
internal class LineAccumulator(private val onLine: (String) -> Unit) {

    private val buffer = StringBuilder()

    fun append(text: String) {
        synchronized(buffer) {
            buffer.append(text)
            while (true) {
                val newline = buffer.indexOf("\n")
                if (newline < 0) break
                val raw = buffer.substring(0, newline)
                buffer.delete(0, newline + 1)
                val line = raw.trim(' ', '\r')
                if (line.isNotEmpty()) onLine(line)
            }
        }
    }

    fun reset() {
        synchronized(buffer) { buffer.clear() }
    }
}

/** Creates daemon threads so idle transports never keep a JVM/host process alive. */
internal fun daemonThreadFactory(name: String): ThreadFactory = ThreadFactory { runnable ->
    Thread(runnable, name).apply { isDaemon = true }
}
