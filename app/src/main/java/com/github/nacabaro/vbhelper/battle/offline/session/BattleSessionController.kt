package com.github.nacabaro.vbhelper.battle.offline.session

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSimulator
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.OrderUpdate
import com.github.nacabaro.vbhelper.battle.offline.core.TrainerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns one live training session and publishes renderable state. Create it on
 * a UI-confined scope, and add/remove pause reasons from the owning screen's
 * lifecycle and tactical menus.
 */
class BattleSessionController(
    private val simulator: BattleSimulator,
    private val scope: CoroutineScope,
    private val nanoTime: () -> Long = System::nanoTime
) : AutoCloseable {
    private val _snapshot = MutableStateFlow(simulator.snapshot())
    val snapshot: StateFlow<BattleSnapshot> = _snapshot.asStateFlow()

    private val pauseReasons = linkedSetOf<String>().apply {
        if (_snapshot.value.isPaused && _snapshot.value.result == null) add(_snapshot.value.pauseReason ?: "menu")
    }
    private var tickJob: Job? = null
    private var previousFrameNanos: Long? = null
    private var fractionalNanos = 0L
    private var closed = false

    fun start() {
        check(!closed) { "A sessão foi fechada." }
        if (tickJob?.isActive == true || _snapshot.value.result != null) return
        tickJob = scope.launch {
            previousFrameNanos = nanoTime()
            while (isActive) {
                delay(FRAME_INTERVAL_MILLIS)
                val now = nanoTime()
                val previous = previousFrameNanos
                previousFrameNanos = now
                if (previous != null) {
                    val deltaNanos = (now - previous).coerceAtLeast(0L) + fractionalNanos
                    val frameDeltaMillis = deltaNanos / NANOS_PER_MILLI
                    fractionalNanos = deltaNanos % NANOS_PER_MILLI
                    simulator.advance(frameDeltaMillis)
                    _snapshot.value = simulator.snapshot()
                    if (_snapshot.value.result != null) break
                }
            }
        }
    }

    fun setPaused(reason: String, paused: Boolean) {
        check(!closed) { "A sessão foi fechada." }
        require(reason.isNotBlank())
        if (paused) pauseReasons += reason else pauseReasons -= reason
        // blast-menu is the only pause which accepts timing taps; it must never mask
        // an external pause merely because its overlay was opened most recently.
        val effectiveReason = pauseReasons.lastOrNull { it != "blast-menu" } ?: pauseReasons.lastOrNull()
        simulator.setPaused(pauseReasons.isNotEmpty(), effectiveReason)
        previousFrameNanos = nanoTime()
        fractionalNanos = 0L
        _snapshot.value = simulator.snapshot()
    }

    fun issueOrder(
        actorId: String,
        action: TrainerAction,
        interruptCurrentAction: Boolean = false
    ): OrderUpdate {
        check(!closed) { "A sessão foi fechada." }
        val update = simulator.issueOrder(actorId, action, interruptCurrentAction)
        _snapshot.value = simulator.snapshot()
        return update
    }

    fun abandon(): BattleOutcome {
        check(!closed) { "A sessão foi fechada." }
        tickJob?.cancel()
        tickJob = null
        val result = simulator.abandon()
        _snapshot.value = simulator.snapshot()
        return result.outcome
    }

    override fun close() {
        if (closed) return
        closed = true
        tickJob?.cancel()
        tickJob = null
        previousFrameNanos = null
        fractionalNanos = 0L
        simulator.cancelFinisher()
        simulator.setPaused(true, "closed")
        _snapshot.value = simulator.snapshot()
    }

    private companion object {
        const val FRAME_INTERVAL_MILLIS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
