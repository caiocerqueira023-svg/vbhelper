package com.tamerslab.components

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val LocalMotionEnabled = staticCompositionLocalOf { ValueAnimator.areAnimatorsEnabled() }

/** One subscription per theme, shared by every sprite/control; background loops also stop. */
@Composable
fun rememberMotionPreference(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(context, lifecycle) {
        fun refresh() { enabled = ValueAnimator.areAnimatorsEnabled() }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val events = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) { foreground = true; refresh() }
            if (event == Lifecycle.Event.ON_STOP) foreground = false
        }
        lifecycle.addObserver(events)
        onDispose { lifecycle.removeObserver(events); context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled && foreground
}
