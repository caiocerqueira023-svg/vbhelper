package com.github.nacabaro.vbhelper.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OperationState<out T> {
    data object Idle : OperationState<Nothing>
    data class Running(val key: Long) : OperationState<Nothing>
    data class Ready<T>(val key: Long, val result: T) : OperationState<T>
    data class Failed(val key: Long) : OperationState<Nothing>
}

/** Retain preparation through recreation without repeating work or a share launch. */
class SingleFlightOperation<T : Any>(
    private val scope: CoroutineScope,
    private val prepare: suspend (Long) -> T,
) {
    private val mutableState = MutableStateFlow<OperationState<T>>(OperationState.Idle)
    val state = mutableState.asStateFlow()

    fun start(key: Long): Boolean {
        val previous = mutableState.value
        if (previous is OperationState.Running || previous is OperationState.Ready) return false
        val running = OperationState.Running(key)
        if (!mutableState.compareAndSet(previous, running)) return false
        val job = scope.launch {
            try {
                mutableState.compareAndSet(running, OperationState.Ready(key, prepare(key)))
            } catch (cancelled: CancellationException) {
                mutableState.compareAndSet(running, OperationState.Idle)
                throw cancelled
            } catch (_: Exception) {
                mutableState.compareAndSet(running, OperationState.Failed(key))
            }
        }
        // A cancelled owner may prevent the body from starting at all.
        job.invokeOnCompletion { if (it is CancellationException) mutableState.compareAndSet(running, OperationState.Idle) }
        return true
    }

    fun takeResult(): T? {
        val ready = mutableState.value as? OperationState.Ready<T> ?: return null
        return if (mutableState.compareAndSet(ready, OperationState.Idle)) ready.result else null
    }

    fun dismissFailure(): Boolean {
        val failed = mutableState.value as? OperationState.Failed ?: return false
        return mutableState.compareAndSet(failed, OperationState.Idle)
    }
}
