package com.tamerslab.hardware.vpet

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * A-Com adapter over USB serial (`usb-serial-for-android`), ported from the recovered DBT-Link
 * `AcomUsb` class.
 *
 * Uses the default prober and falls back to treating every attached USB device as a CDC/ACM
 * adapter (same fallback as the original). Serial parameters are 9600 8N1 with DTR/RTS raised;
 * reads are decoded as ISO-8859-1 and split into `\n` lines. USB permission is requested with a
 * [PendingIntent] broadcast receiver using the application context, so no Activity is needed.
 *
 * Callbacks are delivered on the main thread except `onLine`, which comes from the serial I/O
 * thread (post to the main thread before touching UI).
 */
class AcomUsbLink(
    context: Context,
    onLine: (String) -> Unit,
    private val onStatus: (connected: Boolean, message: String) -> Unit,
) : VpetLink {

    private val context: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lines = LineAccumulator(onLine)
    private val lock = Any()

    private var writeExecutor: ExecutorService =
        Executors.newSingleThreadExecutor(daemonThreadFactory("vpet-acom-write"))
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var ready = false
    private var closed = false
    private var receiverRegistered = false
    private var permissionPending = false
    private var pendingDevice: UsbDevice? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device = intent.usbDeviceExtra()
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            synchronized(lock) {
                unregisterPermissionReceiverLocked()
                permissionPending = false
                val target = device ?: pendingDevice
                pendingDevice = null
                if (granted && target != null) {
                    openDevice(target)
                } else {
                    onStatus(false, "USB permission was not granted; the A-Com cannot be used.")
                }
            }
        }
    }

    /** Finds the adapter and requests USB permission when needed (`AcomUsb.abrir`). */
    fun connect() {
        synchronized(lock) {
            if (closed) {
                writeExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("vpet-acom-write"))
                closed = false
            }
            if (serialPort != null) {
                onStatus(true, "The A-Com is already connected.")
                return
            }
            if (permissionPending) {
                onStatus(false, "Waiting for the USB permission prompt...")
                return
            }
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            if (usbManager == null) {
                onStatus(false, "USB is not available on this device.")
                return
            }
            val driver = findDriver(usbManager)
            if (driver == null) {
                onStatus(false, "No A-Com found. Plug the OTG cable and tap Connect again.")
                return
            }
            if (!usbManager.hasPermission(driver.device)) {
                onStatus(false, "Accept the system prompt to allow the A-Com.")
                pendingDevice = driver.device
                permissionPending = true
                registerPermissionReceiverLocked()
                val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
                usbManager.requestPermission(
                    driver.device,
                    PendingIntent.getBroadcast(context, 0, intent, flags),
                )
                return
            }
            openDevice(driver.device)
        }
    }

    override fun send(text: String) {
        val executor = synchronized(lock) { if (closed) null else writeExecutor }
        if (executor == null) return
        try {
            executor.execute {
                val port = synchronized(lock) { serialPort } ?: return@execute
                try {
                    port.write(text.toByteArray(StandardCharsets.ISO_8859_1), WRITE_TIMEOUT_MS)
                } catch (error: Exception) {
                    mainHandler.post { onStatus(false, "Could not send to the A-Com.") }
                }
            }
        } catch (_: RejectedExecutionException) {
            // The link was closed between the state check and the enqueue; nothing to send.
        }
    }

    override fun close() {
        var port: UsbSerialPort? = null
        var manager: SerialInputOutputManager? = null
        synchronized(lock) {
            closed = true
            ready = false
            port = serialPort
            manager = ioManager
            serialPort = null
            ioManager = null
            permissionPending = false
            pendingDevice = null
            unregisterPermissionReceiverLocked()
            writeExecutor.shutdownNow()
        }
        runCatching { manager?.stop() }
        runCatching { port?.close() }
    }

    /** True once the adapter was opened and the driver settle delay elapsed. */
    fun isConnected(): Boolean = synchronized(lock) {
        ready && serialPort != null && ioManager != null
    }

    private fun findDriver(usbManager: UsbManager): UsbSerialDriver? {
        val detected = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (detected.isNotEmpty()) return detected.first()
        // Fallback from DBT-Link: treat every attached device as a CDC/ACM adapter.
        return usbManager.deviceList.values
            .map { CdcAcmSerialDriver(it) }
            .firstOrNull { it.ports.isNotEmpty() }
    }

    @SuppressLint("MissingPermission")
    private fun openDevice(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            onStatus(false, "USB is not available on this device.")
            return
        }
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            onStatus(false, "Could not open the A-Com (is another app using it?).")
            return
        }
        try {
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: CdcAcmSerialDriver(device)
            val port = driver.ports.firstOrNull()
            if (port == null) {
                connection.close()
                onStatus(false, "The A-Com exposes no serial port.")
                return
            }
            port.open(connection)
            port.setParameters(BAUD_RATE, DATA_BITS, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching {
                port.setDTR(true)
                port.setRTS(true)
            }
            val manager = SerialInputOutputManager(
                port,
                object : SerialInputOutputManager.Listener {
                    override fun onNewData(data: ByteArray) {
                        lines.append(String(data, StandardCharsets.ISO_8859_1))
                    }

                    override fun onRunError(e: Exception) {
                        mainHandler.post {
                            synchronized(lock) {
                                if (serialPort != null) {
                                    ready = false
                                    onStatus(false, "The A-Com disconnected.")
                                }
                            }
                        }
                    }
                },
            )
            manager.start()
            synchronized(lock) {
                serialPort = port
                ioManager = manager
                ready = false
            }
            val driverName = driver.javaClass.simpleName.replace("SerialDriver", "")
            lines.append("[A-Com: $driverName - 9600 baud]\n")
            // DBT-Link waits 350 ms for CDC and 1900 ms for the other drivers before "ready".
            val settleMillis = if (driver is CdcAcmSerialDriver) CDC_SETTLE_MS else SERIAL_SETTLE_MS
            val readyAction = Runnable {
                synchronized(lock) {
                    if (serialPort != null && !closed) {
                        ready = true
                        onStatus(true, "A-Com connected. Put your Vpet in BATTLE and connect.")
                    }
                }
            }
            mainHandler.postDelayed(readyAction, settleMillis)
        } catch (error: Exception) {
            runCatching { connection.close() }
            onStatus(false, "Could not open the A-Com: ${error.message ?: "unknown error"}")
            close()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerPermissionReceiverLocked() {
        if (receiverRegistered) return
        receiverRegistered = true
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    private fun unregisterPermissionReceiverLocked() {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching { context.unregisterReceiver(permissionReceiver) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private companion object {
        const val ACTION_USB_PERMISSION = "com.tamerslab.hardware.vpet.action.USB_PERMISSION"
        const val BAUD_RATE = 9600
        const val DATA_BITS = 8
        const val WRITE_TIMEOUT_MS = 1_000
        const val CDC_SETTLE_MS = 350L
        const val SERIAL_SETTLE_MS = 1_900L
    }
}
