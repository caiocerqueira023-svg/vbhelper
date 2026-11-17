package com.tamerslab.transfer.ui.vpet

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tamerslab.domain.vpet.VpetReadSnapshot
import com.tamerslab.hardware.vpet.AcomUsbLink
import com.tamerslab.hardware.vpet.BtComLink
import com.tamerslab.hardware.vpet.BtSerieLink
import com.tamerslab.hardware.vpet.ScriptedVpetLink
import com.tamerslab.hardware.vpet.TimerVpetScheduler
import com.tamerslab.hardware.vpet.VpetDeviceTransport
import com.tamerslab.hardware.vpet.VpetLink
import com.tamerslab.hardware.vpet.VpetModel
import com.tamerslab.hardware.vpet.VpetReadFailure
import com.tamerslab.hardware.vpet.VpetReadResult
import com.tamerslab.hardware.vpet.VpetReadSession
import com.tamerslab.hardware.vpet.VpetScheduler
import com.tamerslab.hardware.vpet.VpetSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class VpetConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

enum class VpetReadState { IDLE, READING, DONE, FAILED }

/**
 * Owns the transport link and the protocol session for the VPet screen.
 *
 * Everything the Compose surface renders is exposed as snapshot state; transport and protocol
 * callbacks arrive on their own threads and are marshalled back to the main dispatcher before an
 * observable state is touched. Links and sessions are tagged with a generation so callbacks from
 * a link or read that the user already replaced/disconnected can never leak into the new one.
 */
class VpetReadCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val preferences: VpetPreferences,
) {

    var selectedModel by mutableStateOf(preferences.lastModel())
        private set
    var connectionState by mutableStateOf(VpetConnectionState.DISCONNECTED)
        private set
    var connectionMessage by mutableStateOf<String?>(null)
        private set
    var connectedTransport by mutableStateOf<VpetDeviceTransport?>(null)
        private set
    var readState by mutableStateOf(VpetReadState.IDLE)
        private set
    var progress by mutableStateOf(0)
        private set
    var lastStatusLine by mutableStateOf<String?>(null)
        private set
    var protocolLog by mutableStateOf<List<String>>(emptyList())
        private set
    var result by mutableStateOf<VpetReadResult?>(null)
        private set
    var failure by mutableStateOf<VpetReadFailure?>(null)
        private set
    var simulatedRead by mutableStateOf(false)
        private set

    private var learnedVersions by mutableStateOf(initialLearnedVersions())

    val isConnected: Boolean get() = connectionState == VpetConnectionState.CONNECTED
    val isReading: Boolean get() = readState == VpetReadState.READING
    val canRead: Boolean get() = isConnected && !isReading

    private var link: VpetLink? = null
    private var session: VpetReadSession? = null
    private var scheduler: VpetScheduler? = null
    private var linkGeneration = 0
    private var readGeneration = 0
    private var closed = false

    fun learnedVersion(model: VpetModel): Int? = learnedVersions[model.wireKey]

    fun selectModel(model: VpetModel) {
        if (isReading || model == selectedModel) return
        selectedModel = model
        preferences.setLastModel(model)
        connectionMessage = null
    }

    /** A-Com USB. The link owns the system permission dialog and reports back through onStatus. */
    fun connectUsb() {
        if (isReading) return
        closeLink()
        resetReadOutput()
        connectionState = VpetConnectionState.CONNECTING
        connectionMessage = null
        connectedTransport = VpetDeviceTransport.USB_ACM
        simulatedRead = false
        val generation = ++linkGeneration
        val usbLink = AcomUsbLink(
            context.applicationContext,
            { line -> if (generation == linkGeneration) handleIncomingLine(line) },
            { connected, message ->
                post { if (generation == linkGeneration) handleLinkStatus(connected, message) }
            },
        )
        link = usbLink
        runCatching { usbLink.connect() }.onFailure { error ->
            connectionState = VpetConnectionState.DISCONNECTED
            connectedTransport = null
            connectionMessage = error.message
        }
    }

    /** Bluetooth transports (SPP or BLE UART). Permission is requested by the picker before this. */
    fun connectBluetooth(device: BluetoothDevice, transport: VpetDeviceTransport) {
        if (isReading || transport == VpetDeviceTransport.USB_ACM) return
        closeLink()
        resetReadOutput()
        connectionState = VpetConnectionState.CONNECTING
        connectionMessage = null
        connectedTransport = transport
        simulatedRead = false
        val generation = ++linkGeneration
        val onLine: (String) -> Unit = { line -> if (generation == linkGeneration) handleIncomingLine(line) }
        val onStatus: (Boolean, String) -> Unit = { connected, message ->
            post { if (generation == linkGeneration) handleLinkStatus(connected, message) }
        }
        runCatching {
            when (transport) {
                VpetDeviceTransport.BLUETOOTH_SPP -> {
                    val spp = BtSerieLink(context.applicationContext, device, onLine, onStatus)
                    link = spp
                    spp.connect()
                }
                VpetDeviceTransport.BLE_UART -> {
                    val ble = BtComLink(context.applicationContext, device, onLine, onStatus)
                    link = ble
                    ble.connect()
                }
                VpetDeviceTransport.USB_ACM -> Unit
            }
        }.onFailure { error ->
            connectionState = VpetConnectionState.DISCONNECTED
            connectedTransport = null
            connectionMessage = error.message
        }
    }

    /** Starts (or restarts) a real read on the current link. Returns false when not connectable. */
    fun startRead(): Boolean {
        if (!isConnected || isReading) return false
        return if (simulatedRead) startSimulatedRead() else {
            val activeLink = link ?: return false
            beginRead(activeLink, selectedModel, simulated = false, schedulerOverride = null)
        }
    }

    /** Debug-only: builds a scripted link and reads a complete simulated answer. */
    fun simulateRead(): Boolean {
        if (isReading) return false
        closeLink()
        resetReadOutput()
        simulatedRead = true
        connectionState = VpetConnectionState.CONNECTED
        connectionMessage = null
        connectedTransport = null
        return startSimulatedRead()
    }

    fun stopRead() {
        val wasReading = isReading
        stopSession()
        if (wasReading) {
            readState = VpetReadState.IDLE
            progress = 0
            appendLog("read stopped")
        }
    }

    fun disconnect() {
        stopRead()
        closeLink()
        connectionState = VpetConnectionState.DISCONNECTED
        connectionMessage = null
        connectedTransport = null
        simulatedRead = false
    }

    fun clearResult() {
        result = null
        failure = null
        progress = 0
        if (!isReading) readState = VpetReadState.IDLE
    }

    fun snapshotFor(result: VpetReadResult): VpetReadSnapshot = VpetReadSnapshot(
        modelKey = result.model.wireKey,
        modelLabel = result.model.displayName,
        version = result.version,
        index = result.index,
        name = result.name,
        stage = result.stage,
        attribute = result.attribute,
        power = result.power,
        payloadHex = result.payloadHex,
        valid = result.checksumValid,
        readAtMillis = System.currentTimeMillis(),
    )

    /** Stops the session, closes the transport and invalidates every pending callback. */
    fun close() {
        if (closed) return
        closed = true
        linkGeneration++
        readGeneration++
        session?.stop()
        session = null
        scheduler?.cancelAll()
        scheduler = null
        runCatching { link?.close() }
        link = null
    }

    private fun startSimulatedRead(): Boolean {
        closeLink()
        resetReadOutput()
        val model = selectedModel
        val version = preferences.learnedVersion(model) ?: 0
        val readScheduler = TimerVpetScheduler()
        val scripted = ScriptedVpetLink(
            VpetSimulator.responsesFor(model, version, SIMULATION_INDEX),
            { line -> handleIncomingLine(line) },
            readScheduler,
        )
        link = scripted
        return beginRead(scripted, model, simulated = true, schedulerOverride = readScheduler)
    }

    private fun beginRead(
        activeLink: VpetLink,
        model: VpetModel,
        simulated: Boolean,
        schedulerOverride: VpetScheduler?,
    ): Boolean {
        stopSession()
        resetReadOutput()
        simulatedRead = simulated
        val generation = ++readGeneration
        val readScheduler = schedulerOverride ?: TimerVpetScheduler()
        scheduler = readScheduler
        val listener = object : VpetReadSession.Listener {
            override fun onStatus(line: String) {
                post {
                    if (generation == readGeneration && !closed) {
                        appendLog(line)
                        lastStatusLine = line
                    }
                }
            }

            override fun onProgress(percent: Int) {
                post {
                    if (generation == readGeneration && !closed) {
                        progress = percent.coerceIn(0, 100)
                    }
                }
            }

            override fun onFinished(result: VpetReadResult) {
                post { if (generation == readGeneration && !closed) onReadFinished(result) }
            }

            override fun onFailed(failure: VpetReadFailure) {
                post { if (generation == readGeneration && !closed) onReadFailed(failure) }
            }
        }
        val readSession = VpetReadSession(
            model,
            preferences.learnedVersion(model) ?: 0,
            { text -> activeLink.send(text) },
            readScheduler,
            listener,
        )
        session = readSession
        readState = VpetReadState.READING
        progress = 0
        result = null
        failure = null
        appendLog("read started")
        readSession.start()
        return true
    }

    private fun onReadFinished(result: VpetReadResult) {
        readState = VpetReadState.DONE
        progress = 100
        lastStatusLine = null
        this.result = result
        learnedVersions = learnedVersions + (result.model.wireKey to result.version)
        preferences.setLearnedVersion(result.model, result.version)
        appendLog(
            "read complete: version=${result.version} index=${result.index} " +
                "checksum=${if (result.checksumValid) "valid" else "invalid"}"
        )
        releaseSessionHandles()
    }

    private fun onReadFailed(failure: VpetReadFailure) {
        readState = VpetReadState.FAILED
        progress = 0
        lastStatusLine = null
        this.failure = failure
        appendLog("read failed: ${failure.javaClass.simpleName}")
        releaseSessionHandles()
    }

    private fun releaseSessionHandles() {
        session = null
        scheduler?.cancelAll()
        scheduler = null
    }

    private fun stopSession() {
        readGeneration++
        session?.stop()
        session = null
        scheduler?.cancelAll()
        scheduler = null
    }

    private fun closeLink() {
        linkGeneration++
        stopSession()
        runCatching { link?.close() }
        link = null
    }

    private fun handleIncomingLine(line: String) {
        post { session?.receiveLine(line) }
    }

    private fun handleLinkStatus(connected: Boolean, message: String) {
        if (closed) return
        val detail = message.takeIf { it.isNotBlank() }
        if (connected) {
            connectionState = VpetConnectionState.CONNECTED
            connectionMessage = detail
            return
        }
        val wasReading = isReading
        if (wasReading) stopSession()
        if (wasReading) {
            readState = VpetReadState.IDLE
            progress = 0
            appendLog("link lost")
        }
        connectionState = VpetConnectionState.DISCONNECTED
        connectionMessage = detail
        connectedTransport = null
        simulatedRead = false
    }

    private fun resetReadOutput() {
        result = null
        failure = null
        progress = 0
        readState = VpetReadState.IDLE
        protocolLog = emptyList()
        lastStatusLine = null
    }

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        protocolLog = (protocolLog + line).takeLast(MAX_LOG_LINES)
    }

    private fun initialLearnedVersions(): Map<String, Int> = VpetModel.entries
        .mapNotNull { model -> preferences.learnedVersion(model)?.let { model.wireKey to it } }
        .toMap()

    private fun post(block: () -> Unit) {
        if (closed) return
        scope.launch(Dispatchers.Main.immediate) {
            if (!closed) block()
        }
    }

    private companion object {
        const val MAX_LOG_LINES = 240

        /**
         * Table index used by the debug simulation. Zero is the protocol's "hidden species"
         * slot, so a positive index is needed to exercise the decoded-name/import path.
         */
        const val SIMULATION_INDEX = 4
    }
}
