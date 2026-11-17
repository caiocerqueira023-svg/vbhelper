package com.tamerslab.vpet

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
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
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Portes do DBT-Link v18.9 (ver analysis/vpet-rea/README.md).
 * As classes originais eram: AcomUsb, BtCom (BLE NUS), BtSerie (SPP).
 *
 * Linhas recebidas são entregues em [onLine] em qualquer thread; quem consome deve
 * postar no main para atualizar UI/WebView.
 */
abstract class VpetTransport(
    protected val context: Context,
    protected val onLine: (String) -> Unit,
    protected val onStatus: (ok: Boolean, msg: String) -> Unit,
) : VpetLink {
    private val buffer = StringBuilder()

    protected fun recibirTexto(texto: String) {
        synchronized(buffer) {
            buffer.append(texto)
            while (true) {
                val idx = buffer.indexOf("\n")
                if (idx < 0) break
                val raw = buffer.substring(0, idx)
                buffer.delete(0, idx + 1)
                val line = raw.trim(' ', '\r')
                if (line.isNotEmpty()) onLine(line)
            }
        }
    }
}

/**
 * A-Com por USB serial (CDC/ACM ou prober padrão), 9600 8N1 — valores observados em
 * AcomUsb#abrir. Requer permissão do UsbManager.
 */
class AcomUsbLink(
    private val activity: Activity,
    onLine: (String) -> Unit,
    onStatus: (ok: Boolean, msg: String) -> Unit,
) : VpetTransport(activity, onLine, onStatus) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.tamerslab.vpet.USB_PERMISSION"
        private const val BAUD = 9600
    }

    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private val hilo = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var listo = false
    private var receiverRegistered = false
    private var pendingDevice: UsbDevice? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                open(device)
            } else {
                onStatus(false, "Sin permiso para usar el A-Com.")
            }
        }
    }

    fun abrir() {
        val usb = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        var drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        if (drivers.isEmpty()) {
            // Fallback do DBT-Link: trata todo device como CDC/ACM.
            drivers = usb.deviceList.values.map { CdcAcmSerialDriver(it) }
                .filter { it.ports.isNotEmpty() }
        }
        val driver = drivers.firstOrNull()
        if (driver == null) {
            onStatus(false, "No encuentro el A-Com. Conéctalo con el cable OTG y vuelve a tocar.")
            return
        }
        if (!usb.hasPermission(driver.device)) {
            onStatus(false, "Acepta el aviso para usar el A-Com…")
            pendingDevice = driver.device
            registerPermissionReceiver()
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            usb.requestPermission(driver.device, PendingIntent.getBroadcast(
                activity, 0, Intent(ACTION_USB_PERMISSION).setPackage(activity.packageName), flags
            ))
            return
        }
        open(driver.device)
    }

    private fun registerPermissionReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(permissionReceiver, filter)
        }
    }

    @SuppressLint("MissingPermission")
    private fun open(device: UsbDevice) {
        val usb = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = usb.openDevice(device) ?: run {
            onStatus(false, "No pude abrir el A-Com.")
            return
        }
        try {
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                ?: CdcAcmSerialDriver(device)
            val p = driver.ports.first()
            p.open(connection)
            p.setParameters(BAUD, 8, 1, UsbSerialPort.PARITY_NONE)
            try {
                p.dtr = true
                p.rts = true
            } catch (_: Exception) {}
            port = p
            val manager = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    recibirTexto(String(data, StandardCharsets.ISO_8859_1))
                }
                override fun onRunError(e: Exception) {
                    if (port != null) onStatus(false, "Se desconectó el A-Com.")
                }
            })
            manager.start()
            io = manager
            onLine("[A-Com: ${driver.javaClass.simpleName.replace("SerialDriver", "")} · 9600 baudios]")
            // DBT-Link espera 350 ms para CDC e 1900 ms para os demais antes de avisar "listo".
            val delay = if (driver is CdcAcmSerialDriver) 350L else 1900L
            main.postDelayed({ avisarListo() }, delay)
        } catch (e: Exception) {
            onStatus(false, "Error al abrir el A-Com: ${e.message ?: ""}")
            cerrar()
        }
    }

    private fun avisarListo() {
        if (listo || port == null) return
        listo = true
        onStatus(true, "A-Com conectado. En tu vpet entra a BATTLE y conecta.")
    }

    override fun enviar(texto: String) {
        hilo.execute {
            try {
                port?.write(texto.toByteArray(StandardCharsets.ISO_8859_1), 1000)
            } catch (e: Exception) {
                main.post { onStatus(false, "No pude mandar al A-Com.") }
            }
        }
    }

    override fun cerrar() {
        listo = false
        try { io?.stop() } catch (_: Exception) {}
        io = null
        try { port?.close() } catch (_: Exception) {}
        port = null
        if (receiverRegistered) {
            try { activity.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        pendingDevice = null
        hilo.shutdownNow()
    }
}

/**
 * BTCom por BLE (Nordic UART Service) — mapeamento de BtCom#<clinit>/conectar/siguiente/enviar.
 * Fila de escrita em chunks com retry; igual ao original: writeType = 2 (no response).
 */
@SuppressLint("MissingPermission")
class BtComLink(
    private val context: Context,
    private val device: BluetoothDevice,
    onLine: (String) -> Unit,
    onStatus: (ok: Boolean, msg: String) -> Unit,
) : VpetTransport(context, onLine, onStatus) {

    companion object {
        val NUS: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // escrita
        val TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // notificação
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val CHUNK = 20
    }

    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private val cola = ArrayDeque<ByteArray>()
    private var escribiendo = false
    private var cerrado = false

    fun conectar() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!manager.adapter.isEnabled) {
            onStatus(false, "Prende el Bluetooth del teléfono y vuelve a tocar.")
            return
        }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        onStatus(false, "Conectando con el BTCom…")
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || cerrado) {
                onStatus(false, "Se desconectó el BTCom.")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val nus = g.getService(NUS)
            val rx = nus?.getCharacteristic(RX)
            val tx = nus?.getCharacteristic(TX)
            if (nus == null || rx == null || tx == null) {
                onStatus(false, "Ese aparato no es un BTCom (no tiene el servicio UART).")
                return
            }
            rxChar = rx
            g.setCharacteristicNotification(tx, true)
            val desc = tx.getDescriptor(CCCD)
            if (desc != null) {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(desc)
                }
            }
            onStatus(true, "BTCom conectado. Toca «Leer» y en tu vpet entra a BATTLE.")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // DBT-Link refila o chunk após 60 ms quando o write falha.
                val pendente = chunkAtual
                main.postDelayed({ if (pendente != null) { cola.addFirst(pendente); siguiente() } }, 60)
            }
            escribiendo = false
            siguiente()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            recibirTexto(String(ch.value ?: ByteArray(0), StandardCharsets.ISO_8859_1))
        }
    }

    private var chunkAtual: ByteArray? = null

    private fun enviarChunk(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = rxChar ?: return false
        chunkAtual = bytes
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.value = bytes
            @Suppress("DEPRECATION")
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    private fun siguiente() {
        if (escribiendo) return
        val bytes = cola.removeFirstOrNull() ?: return
        escribiendo = true
        if (enviarChunk(bytes)) return
        escribiendo = false
        main.postDelayed({ cola.addFirst(bytes); siguiente() }, 60)
    }

    override fun enviar(texto: String) {
        val bytes = texto.toByteArray(StandardCharsets.ISO_8859_1)
        var i = 0
        while (i < bytes.size) {
            val end = minOf(i + CHUNK, bytes.size)
            cola.addLast(bytes.copyOfRange(i, end))
            i = end
        }
        main.post { siguiente() }
    }

    override fun cerrar() {
        cerrado = true
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        rxChar = null
    }
}

/** BTCom por Bluetooth clássico (SPP) — Cf. BtSerie. */
@SuppressLint("MissingPermission")
class BtSerieLink(
    context: Context,
    private val device: BluetoothDevice,
    private val onReady: (() -> Unit)? = null,
    onLine: (String) -> Unit,
    onStatus: (ok: Boolean, msg: String) -> Unit,
) : VpetTransport(context, onLine, onStatus) {

    companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }

    private val hilo = Executors.newSingleThreadExecutor()
    private var sock: BluetoothSocket? = null
    @Volatile private var cerrado = false

    fun conectar() {
        hilo.execute {
            try {
                val s = device.createRfcommSocketToServiceRecord(SPP)
                s.connect()
                sock = s
                onStatus(true, "BTCom conectado. Toca «Leer» y en tu vpet entra a BATTLE.")
                onReady?.invoke()
                val buf = ByteArray(1024)
                while (!cerrado) {
                    val n = s.inputStream.read(buf)
                    if (n <= 0) break
                    recibirTexto(String(buf, 0, n, StandardCharsets.ISO_8859_1))
                }
            } catch (e: Exception) {
                if (!cerrado) onStatus(false, "No pude conectar por Bluetooth clásico. Revisa que esté prendido y vinculado.")
            } finally {
                try { sock?.close() } catch (_: Exception) {}
            }
        }
    }

    override fun enviar(texto: String) {
        hilo.execute {
            try {
                sock?.outputStream?.write(texto.toByteArray(StandardCharsets.ISO_8859_1))
                sock?.outputStream?.flush()
            } catch (e: Exception) {
                onStatus(false, "No pude mandar al BTCom.")
            }
        }
    }

    override fun cerrar() {
        cerrado = true
        try { sock?.close() } catch (_: Exception) {}
        sock = null
        hilo.shutdownNow()
    }
}
