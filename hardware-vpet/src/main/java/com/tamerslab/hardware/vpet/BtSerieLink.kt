package com.tamerslab.hardware.vpet

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * BTCom adapter over Bluetooth classic SPP, ported from the recovered `BtSerie` class.
 *
 * Unlike the recovered code (which ran the blocking read loop on the same single thread used
 * for writes), reading and writing use two daemon executors so outgoing commands are never
 * starved by the blocking `inputStream.read`.
 *
 * `onLine` is invoked from the reader thread; `onStatus` may come from the reader or writer
 * thread (post to the main thread before touching UI).
 */
@SuppressLint("MissingPermission")
class BtSerieLink(
    context: Context,
    private val device: BluetoothDevice,
    onLine: (String) -> Unit,
    private val onStatus: (connected: Boolean, message: String) -> Unit,
) : VpetLink {

    private val context: Context = context.applicationContext
    private val lines = LineAccumulator(onLine)
    private var readExecutor: ExecutorService =
        Executors.newSingleThreadExecutor(daemonThreadFactory("vpet-spp-read"))
    private var writeExecutor: ExecutorService =
        Executors.newSingleThreadExecutor(daemonThreadFactory("vpet-spp-write"))

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var closed = false

    @Volatile
    private var connected = false

    /** Connects the RFCOMM socket and starts pumping received lines (`BtSerie.conectar`). */
    fun connect() {
        if (closed) {
            // close() shut the executors down; a reuse of this instance gets fresh ones.
            readExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("vpet-spp-read"))
            writeExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("vpet-spp-write"))
        }
        closed = false
        readExecutor.execute {
            try {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                runCatching { manager?.adapter?.cancelDiscovery() }
                val connection = device.createRfcommSocketToServiceRecord(SPP)
                connection.connect()
                socket = connection
                connected = true
                onStatus(true, "BTCom connected. Tap Read and put your Vpet in BATTLE.")
                val buffer = ByteArray(READ_BUFFER_SIZE)
                while (!closed) {
                    val read = connection.inputStream.read(buffer)
                    if (read <= 0) break
                    lines.append(String(buffer, 0, read, StandardCharsets.ISO_8859_1))
                }
                if (!closed) {
                    connected = false
                    onStatus(false, "The BTCom disconnected.")
                }
            } catch (error: Exception) {
                if (!closed) {
                    connected = false
                    onStatus(
                        false,
                        "Could not connect over classic Bluetooth. Check the adapter is on and paired.",
                    )
                }
            } finally {
                val connection = socket
                socket = null
                connected = false
                runCatching { connection?.close() }
            }
        }
    }

    override fun send(text: String) {
        if (closed) return
        val bytes = text.toByteArray(StandardCharsets.ISO_8859_1)
        try {
            writeExecutor.execute {
                val connection = socket ?: return@execute
                try {
                    connection.outputStream.write(bytes)
                    connection.outputStream.flush()
                } catch (error: IOException) {
                    onStatus(false, "Could not send to the BTCom.")
                }
            }
        } catch (_: RejectedExecutionException) {
            // The link was closed between the state check and the enqueue.
        }
    }

    override fun close() {
        closed = true
        connected = false
        val connection = socket
        socket = null
        runCatching { connection?.close() }
        readExecutor.shutdownNow()
        writeExecutor.shutdownNow()
    }

    /** True between a successful RFCOMM connect and the disconnect/close. */
    fun isConnected(): Boolean = connected

    companion object {
        /** Serial Port Profile service UUID (`BtSerie.SPP`). */
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        private const val READ_BUFFER_SIZE = 1024
    }
}
