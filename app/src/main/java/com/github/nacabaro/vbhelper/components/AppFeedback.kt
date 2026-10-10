package com.github.nacabaro.vbhelper.components

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import com.github.nacabaro.vbhelper.di.VBHelper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

enum class FeedbackDestination { SETTINGS, BATTLES }

data class AppFeedbackMessage(
    @StringRes val resource: Int? = null,
    val text: String? = null,
    val arguments: List<String> = emptyList(),
    val destination: FeedbackDestination? = null,
    val important: Boolean = false,
) {
    fun resolve(resources: Resources): String = resource?.let {
        resources.getString(it, *arguments.toTypedArray())
    } ?: text.orEmpty()
}

/** The application owns queued feedback, so navigation cannot discard a result. */
class AppFeedback {
    private val queue = Channel<AppFeedbackMessage>(64, BufferOverflow.DROP_OLDEST)
    val messages = queue.receiveAsFlow()
    private val mutableTransferStatus = MutableStateFlow<String?>(null)
    val transferStatus = mutableTransferStatus.asStateFlow()

    fun show(message: AppFeedbackMessage) { queue.trySend(message) }
    fun transfer(message: String) {
        mutableTransferStatus.value = message
        show(AppFeedbackMessage(text = message, important = true))
    }
    fun clearTransfer() { mutableTransferStatus.value = null }
}

fun Context.showAppFeedback(
    @StringRes resource: Int,
    vararg arguments: String,
    destination: FeedbackDestination? = null,
    important: Boolean = false,
) {
    (applicationContext as? VBHelper)?.feedback?.show(
        AppFeedbackMessage(resource = resource, arguments = arguments.toList(), destination = destination, important = important)
    )
}

fun Context.showAppFeedback(text: String, important: Boolean = false) {
    (applicationContext as? VBHelper)?.feedback?.show(AppFeedbackMessage(text = text, important = important))
}
