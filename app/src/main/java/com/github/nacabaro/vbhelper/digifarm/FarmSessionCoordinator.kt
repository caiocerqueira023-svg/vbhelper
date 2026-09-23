package com.github.nacabaro.vbhelper.digifarm

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-scoped coordinator for the single detailed Digifarm session (§13).
 *
 * The map and the group observe the same farm; recomposition must not start two
 * engines. Surfaces call [acquire]/[release]; only the most recently acquired
 * farm advances in 900 ms simulation steps with per-frame interpolation.
 * Leaving both surfaces cancels new generations and checkpoints via [onSuspend].
 */
class FarmSessionCoordinator(
    private val repository: DigifarmRepository,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val mutex = Mutex()
    private val refCounts = mutableMapOf<String, Int>()
    private var activeFarmId: String? = null
    private var loopJob: Job? = null
    var onSuspend: (suspend (String) -> Unit)? = null

    suspend fun acquire(farmId: String) {
        mutex.withLock {
            refCounts[farmId] = (refCounts[farmId] ?: 0) + 1
            if (activeFarmId != farmId) {
                val previous = activeFarmId
                activeFarmId = farmId
                restartLoopLocked(farmId)
                if (previous != null && (refCounts[previous] ?: 0) <= 0) {
                    previous.let { onSuspend?.let { cb -> externalScope.launch { cb(it) } } }
                }
            } else if (loopJob?.isActive != true) {
                restartLoopLocked(farmId)
            }
        }
    }

    suspend fun release(farmId: String) {
        mutex.withLock {
            val next = ((refCounts[farmId] ?: 1) - 1).coerceAtLeast(0)
            if (next == 0) refCounts.remove(farmId) else refCounts[farmId] = next
            if (activeFarmId == farmId && next == 0) {
                // Prefer another observed farm; otherwise suspend the loop entirely.
                val other = refCounts.keys.firstOrNull()
                if (other != null) {
                    activeFarmId = other
                    restartLoopLocked(other)
                } else {
                    loopJob?.cancel()
                    loopJob = null
                    activeFarmId = null
                }
                onSuspend?.let { cb -> externalScope.launch { cb(farmId) } }
            }
        }
    }

    fun activeFarm(): String? = activeFarmId

    private fun restartLoopLocked(farmId: String) {
        loopJob?.cancel()
        loopJob = externalScope.launch {
            while (isActive) {
                try {
                    repository.simulateStep(farmId)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    Log.w("FarmSession", "Simulation tick failed for $farmId", failure)
                }
                delay(900L)
            }
        }
    }
}
