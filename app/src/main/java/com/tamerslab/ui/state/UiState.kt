package com.tamerslab.ui.state

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Generic explicit screen state for request/response surfaces. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val value: T) : UiState<T>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}

/** Request/response state; keep it separate from continuous streaming snapshots. */
sealed interface RequestState<out T> {
    data object Idle : RequestState<Nothing>
    data object Loading : RequestState<Nothing>
    data class Success<T>(val value: T) : RequestState<T>
    data class Failure(val message: String, val cause: Throwable? = null) : RequestState<Nothing>
}

/** Persistent state for screens that stream continuously (battle/farm/world). */
data class StreamingUiState<T>(
    val value: T,
    val paused: Boolean = false,
    val error: String? = null,
)

/** User intent dispatched from a screen to its state holder. */
sealed interface UiEvent {
    data object Back : UiEvent
    data class Message(val text: String) : UiEvent
}

/** One-shot effect consumed exactly once (navigation, snackbar, share sheet, ...). */
sealed interface UiEffect {
    data class Navigate(val route: com.tamerslab.navigation.AppRoute) : UiEffect
    data class Feedback(val message: String, val important: Boolean = false) : UiEffect
}

/**
 * Channel-backed one-shot event queue. Unlike StateFlow, a collected value is not replayed
 * after rotation or recomposition.
 */
class OneShotEventQueue<T>(
    capacity: Int = 16,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) {
    private val channel = Channel<T>(capacity, onBufferOverflow = onBufferOverflow)
    val events: Flow<T> = channel.receiveAsFlow()

    fun emit(event: T) {
        channel.trySend(event)
    }

    fun close() = channel.close()
}
