package com.tamerslab.hardware.vpet

/**
 * Read state machine ported from the recovered `lector.js` (`empezar`/`pedir`/`armar`/`linea`/
 * `cerrar`).
 *
 * The session only talks to an adapter through [send]: it sends the DigiROM (`V2-…`) or Color
 * block (`C1-`/`C2-`), collects the lines reported by the transport and decodes them with
 * [VpetDecoders]. It never talks to Android APIs, so it is fully unit-testable with a fake
 * scheduler and a fake send lambda.
 *
 * Behaviour highlights (all from lector.js):
 * - 30 s request timeout, re-armed to 1.5 s after every received packet;
 * - progress 10..95 % based on packets received, 60 % for Color blocks, 5 % when learning a
 *   version and re-requesting;
 * - version mismatch: learn the device version, save it via [learnedVersion], and request
 *   again (always when the revealed index is 0);
 * - at most 8 attempts, then [VpetReadFailure.NoResponse];
 * - DMC resends the Color block every 2.5 s and 300 ms after a `t` terminator; PenC sends it
 *   once (the adapter repeats it);
 * - `stop()`/a finished read sends the `"0"` stop command.
 *
 * Threading: methods synchronize on an internal lock, so [receiveLine] may be called from a
 * USB/BLE reader thread and the listener callbacks run on whichever thread triggered them
 * (the caller, or the scheduler thread for timeouts). Consumers that update UI must post to
 * the main thread themselves.
 *
 * @param initialVersion last version persisted by the caller (0 when unknown); Pendulum
 *   models fall back to version 6 exactly like lector.js `verGuardada`.
 */
class VpetReadSession(
    val model: VpetModel,
    initialVersion: Int,
    private val send: (String) -> Unit,
    private val scheduler: VpetScheduler,
    private val listener: Listener,
) {

    /** Read callbacks; only [onFinished] and [onFailed] are mandatory. */
    interface Listener {
        /** Protocol monitor line (`>` prefix for outgoing commands). */
        fun onStatus(line: String) {}

        /** Progress in percent (lector.js `progreso`). */
        fun onProgress(percent: Int) {}

        fun onFinished(result: VpetReadResult)

        fun onFailed(failure: VpetReadFailure)
    }

    private val spec = VpetModelSpec.of(model)
    private val lock = Any()
    private var reading: Reading? = null
    private var lastVersion: Int = if (initialVersion == 0) spec.defaultVersion else initialVersion

    /**
     * Clock used for the Color resend throttling (lector.js `Date.now()`). Internal so JVM
     * tests can drive it together with a fake scheduler.
     */
    internal var timeSource: () -> Long = { System.currentTimeMillis() }

    private val colorBlockPattern: Regex? = spec.colorHeader?.let { header ->
        Regex("([sr]):\\s*($header[0-9A-Fa-f]{24})", RegexOption.IGNORE_CASE)
    }

    /** lector.js `empezar`: cancels any previous read and starts requesting. */
    fun start() {
        synchronized(lock) {
            stopLocked()
            startLocked()
        }
    }

    /** lector.js `parar`: cancels timers, forgets the reading and sends the stop command. */
    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    /** lector.js `linea`: one line received from the adapter. */
    fun receiveLine(line: String) {
        synchronized(lock) { receiveLineLocked(line) }
    }

    /** Version currently used by the session (lector.js `version(tipo)`). */
    fun learnedVersion(): Int = synchronized(lock) { reading?.version ?: lastVersion }

    private fun startLocked() {
        val reading = Reading(spec, lastVersion)
        this.reading = reading
        if (spec.color) startColorTickerLocked(reading)
        requestLocked(reading)
    }

    private fun stopLocked() {
        val current = reading ?: return
        reading = null
        lastVersion = current.version
        cancelTimers(current)
        send(VpetDigiRom.STOP_COMMAND)
    }

    private fun cancelTimers(reading: Reading) {
        reading.timeout?.cancel()
        reading.resendAfterT?.cancel()
        reading.ticker?.cancel()
        reading.timeout = null
        reading.resendAfterT = null
        reading.ticker = null
    }

    /** lector.js `pedir`: new attempt, clear collected lines, send the command, arm 30 s. */
    private fun requestLocked(reading: Reading) {
        if (this.reading !== reading) return
        reading.lines.clear()
        reading.attempt += 1
        if (reading.spec.color) {
            sendColorLocked(reading)
            listener.onProgress(MIN_PROGRESS)
        } else {
            sendLocked(VpetDigiRom.commandFor(model, reading.version))
        }
        armLocked(reading, REQUEST_TIMEOUT_MS)
    }

    /** lector.js `enviar`: forwards to the adapter and reports the command in the monitor. */
    private fun sendLocked(text: String) {
        send(text)
        listener.onStatus("> $text")
    }

    /** lector.js `mandarColor`: throttled Color block send (400 ms minimum gap). */
    private fun sendColorLocked(reading: Reading) {
        if (this.reading !== reading) return
        val now = timeSource()
        if (colorSendAge(reading, now) < COLOR_MIN_SEND_GAP_MS) return
        reading.lastColorSentAt = now
        sendLocked(VpetDigiRom.commandFor(model, reading.version))
    }

    /**
     * Age of the last Color send (lector.js `Date.now() - L.ue`). A null send timestamp means
     * "never / force resend", which lector.js models with `L.ue = 0` on an epoch clock.
     */
    private fun colorSendAge(reading: Reading, now: Long): Long =
        reading.lastColorSentAt?.let { now - it } ?: Long.MAX_VALUE

    /** lector.js `armar`: (re)arms the request timeout, cancelling the previous one. */
    private fun armLocked(reading: Reading, delayMillis: Long) {
        reading.timeout?.cancel()
        reading.timeout = scheduler.schedule(delayMillis) {
            synchronized(lock) { closeLocked(reading) }
        }
    }

    /** lector.js `setInterval(..., 500)` that resends the DMC block when it is stale. */
    private fun startColorTickerLocked(reading: Reading) {
        reading.ticker?.cancel()
        reading.ticker = scheduler.schedule(COLOR_TICK_MS) {
            synchronized(lock) {
                if (this.reading === reading) {
                    if (reading.spec.resendColor && colorSendAge(reading, timeSource()) > COLOR_RESEND_MS) {
                        sendColorLocked(reading)
                    }
                    if (this.reading === reading) startColorTickerLocked(reading)
                }
            }
        }
    }

    private fun receiveLineLocked(line: String) {
        val reading = reading ?: return
        listener.onStatus(line)
        reading.lines.add(line)
        if (reading.spec.color) {
            handleColorLineLocked(reading, line)
            return
        }
        val packets = VpetDecoders.countPackets(reading.lines, reading.spec.onlyLowNibbleE)
        if (packets > 0) {
            val percent = (packets * 100 / reading.spec.packetCount).coerceIn(MIN_PROGRESS, MAX_PROGRESS)
            listener.onProgress(percent)
            armLocked(reading, PACKET_TIMEOUT_MS)
        }
        if (packets >= reading.spec.packetCount) {
            reading.timeout?.cancel()
            closeLocked(reading)
        }
    }

    /** lector.js `cerrar`: timeout/decode handler, including version learning and retries. */
    private fun closeLocked(reading: Reading) {
        if (this.reading !== reading) return
        if (reading.spec.color) {
            if (reading.attempt >= MAX_ATTEMPTS) {
                failLocked(reading, VpetReadFailure.NoResponse(reading.attempt))
            } else {
                listener.onStatus("[no read yet; sending the Color block again]")
                reading.lastColorSentAt = null
                requestLocked(reading)
            }
            return
        }
        val payload = try {
            VpetDecoders.decodePayload(model, reading.lines)
        } catch (error: RuntimeException) {
            failLocked(reading, VpetReadFailure.InvalidData(error.message ?: "Decoder error"))
            return
        }
        if (payload == null) {
            if (reading.lines.isNotEmpty()) {
                listener.onStatus("[does not look like a ${model.displayName}; listening again]")
            }
            if (reading.attempt >= MAX_ATTEMPTS) {
                failLocked(reading, VpetReadFailure.NoResponse(reading.attempt))
            } else {
                requestLocked(reading)
            }
            return
        }
        // The VPet only reveals the number of its Digimon when the version matches.
        val versionValidator = reading.spec.versionValidator
        if (payload.version != reading.version && (versionValidator == null || versionValidator(payload.version))) {
            reading.version = payload.version
            lastVersion = payload.version
            listener.onStatus("[version ${payload.version}: adjusting and requesting again]")
            if (payload.index == 0) {
                listener.onProgress(VERSION_LEARN_PROGRESS)
                requestLocked(reading)
                return
            }
        }
        finishLocked(reading, payload)
    }

    /** lector.js `lineaColor`: accumulates text, finds the first block after an `s:` marker. */
    private fun handleColorLineLocked(reading: Reading, line: String) {
        val text = "$line\n"
        if (SHORT_SIGNAL_PATTERN.containsMatchIn(text)) {
            listener.onStatus("[the VPet signal was corrupted: keep the VPet still and press B again]")
        }
        reading.colorBuffer.append(text)
        if (reading.colorBuffer.length > COLOR_BUFFER_LIMIT) {
            reading.colorBuffer.delete(0, reading.colorBuffer.length - COLOR_BUFFER_LIMIT)
        }
        val pattern = colorBlockPattern ?: return
        var consumed = 0
        var sawBlock = false
        for (match in pattern.findAll(reading.colorBuffer)) {
            consumed = match.range.last + 1
            if (match.groupValues[1].equals("s", ignoreCase = true)) {
                reading.sawColorData = false
                continue
            }
            sawBlock = true
            if (!reading.sawColorData) {
                reading.sawColorData = true
                if (handleColorBlockLocked(reading, match.groupValues[2])) return
            }
        }
        val tail = if (consumed < reading.colorBuffer.length) {
            reading.colorBuffer.substring(consumed)
        } else {
            ""
        }
        val terminator = TERMINATOR_PATTERN.find(tail)
        if (terminator != null) {
            consumed += terminator.range.first + terminator.value.length
            if (reading.spec.resendColor) {
                reading.resendAfterT?.cancel()
                reading.resendAfterT = scheduler.schedule(COLOR_T_RESEND_DELAY_MS) {
                    synchronized(lock) {
                        if (this.reading === reading &&
                            colorSendAge(reading, timeSource()) > COLOR_T_RESEND_MIN_AGE_MS
                        ) {
                            sendColorLocked(reading)
                        }
                    }
                }
            }
        }
        if (consumed > 0) reading.colorBuffer.delete(0, consumed)
        if (sawBlock) listener.onProgress(COLOR_PROGRESS)
        if (model == VpetModel.DMC) handleDmcCodeFallbackLocked(reading, text)
    }

    /** lector.js `bloque`: decode one Color block; true when the reading finished. */
    private fun handleColorBlockLocked(reading: Reading, blockHex: String): Boolean {
        if (model == VpetModel.PENC) {
            return when (val result = VpetDecoders.decodePencBlock(blockHex)) {
                null -> false
                VpetColorBlockResult.Ignored -> false
                VpetColorBlockResult.InvalidChecksum -> {
                    listener.onStatus("[Pendulum Color: read discarded, checksum mismatch. Press B again]")
                    false
                }

                is VpetColorBlockResult.Decoded -> {
                    finishLocked(reading, result.payload)
                    true
                }
            }
        }
        val payload = VpetDecoders.decodeDmcBlock(blockHex) ?: return false
        finishLocked(reading, payload)
        return true
    }

    /**
     * lector.js `lineaColor` DMC fallback: a complete `C1-…` code printed by the adapter that is
     * not one of our own commands is decoded directly.
     */
    private fun handleDmcCodeFallbackLocked(reading: Reading, text: String) {
        reading.codeBuffer.append(text)
        if (reading.codeBuffer.length > CODE_BUFFER_LIMIT) {
            reading.codeBuffer.delete(0, reading.codeBuffer.length - CODE_BUFFER_LIMIT)
        }
        val match = DMC_CODE_PATTERN.findAll(reading.codeBuffer).lastOrNull() ?: return
        reading.codeBuffer.delete(0, match.range.last + 1)
        val command = match.value
        val upper = command.uppercase()
        if (upper == VpetProtocol.DMC_COMMAND || upper == VpetProtocol.DMC_RIVAL) return
        val payload = VpetDecoders.decodeDmcCode(command) ?: return
        finishLocked(reading, payload)
    }

    /** lector.js `terminar`: stop the read, then report the result. */
    private fun finishLocked(reading: Reading, payload: VpetDecodedPayload) {
        if (this.reading !== reading) return
        stopLocked()
        listener.onFinished(payload.toReadResult(model))
    }

    /** lector.js `falla`: stop the read, then report the failure. */
    private fun failLocked(reading: Reading, failure: VpetReadFailure) {
        if (this.reading !== reading) return
        stopLocked()
        listener.onFailed(failure)
    }

    private class Reading(val spec: VpetModelSpec, var version: Int) {
        var attempt: Int = 0
        val lines: MutableList<String> = ArrayList()
        val colorBuffer: StringBuilder = StringBuilder()
        val codeBuffer: StringBuilder = StringBuilder()
        var sawColorData: Boolean = false
        var lastColorSentAt: Long? = null
        var timeout: VpetScheduled? = null
        var resendAfterT: VpetScheduled? = null
        var ticker: VpetScheduled? = null
    }

    internal companion object {
        /** lector.js `armar(30000)`. */
        const val REQUEST_TIMEOUT_MS = 30_000L

        /** lector.js `armar(1500)` after each received packet. */
        const val PACKET_TIMEOUT_MS = 1_500L

        /** lector.js `setInterval(..., 500)`. */
        const val COLOR_TICK_MS = 500L

        /** lector.js resend threshold: `Date.now() - L.ue > 2500`. */
        const val COLOR_RESEND_MS = 2_500L

        /** lector.js `mandarColor` throttle: `Date.now() - L.ue < 400`. */
        const val COLOR_MIN_SEND_GAP_MS = 400L

        /** lector.js resend 300 ms after the `t` terminator. */
        const val COLOR_T_RESEND_DELAY_MS = 300L

        /** lector.js `t` resend only when the last send is older than 120 ms. */
        const val COLOR_T_RESEND_MIN_AGE_MS = 120L

        /** lector.js Color text buffer: `slice(-1500)`. */
        const val COLOR_BUFFER_LIMIT = 1_500

        /** lector.js DMC code buffer: `slice(-300)`. */
        const val CODE_BUFFER_LIMIT = 300

        /** lector.js: give up after 8 attempts. */
        const val MAX_ATTEMPTS = 8

        const val MIN_PROGRESS = 10
        const val MAX_PROGRESS = 95
        const val COLOR_PROGRESS = 60
        const val VERSION_LEARN_PROGRESS = 5

        private val SHORT_SIGNAL_PATTERN = Regex("active too short|too short", RegexOption.IGNORE_CASE)
        private val TERMINATOR_PATTERN = Regex("(?:^|\\s)t(?=\\s|$)")
        private val DMC_CODE_PATTERN = Regex("C1-[0-9A-Fa-f]{32}-[0-9A-Fa-f]{32}")
    }
}
