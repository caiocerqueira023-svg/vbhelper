package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherPhase
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherSnapshot
import kotlin.math.roundToInt

/** Snapshot-triggered native cues; the app's looping soundtrack retains its existing owner. */
@Composable
internal fun BattleFinisherFeedback(
    sessionId: String?,
    finisher: BattleFinisherSnapshot?,
    enabled: Boolean
) {
    val view = LocalView.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val tones = remember(appContext, audioManager, sessionId, lifecycleOwner, view) {
        BattleFinisherTonePlayer(appContext, audioManager)
    }
    var observedSequence by rememberSaveable(sessionId) { mutableStateOf<Long?>(null) }
    var observedPhase by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }

    DisposableEffect(tones, lifecycleOwner, view) {
        tones.observeSettings()
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                tones.stop()
            }
        }
        val windowObserver = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) tones.stop()
        }
        val viewTreeObserver = view.viewTreeObserver
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        viewTreeObserver.addOnWindowFocusChangeListener(windowObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            if (viewTreeObserver.isAlive) viewTreeObserver.removeOnWindowFocusChangeListener(windowObserver)
            tones.release()
        }
    }

    LaunchedEffect(sessionId, finisher?.sequenceId, finisher?.phase, enabled) {
        // Stop before the consumed-phase guard: pause must silence an in-flight cue.
        if (!enabled || finisher == null) tones.stop()
        val movie = finisher ?: return@LaunchedEffect
        if (observedSequence == movie.sequenceId && observedPhase == movie.phase.name) return@LaunchedEffect
        // Consume even suppressed beats so resume/rotation never replays a stale cue.
        observedSequence = movie.sequenceId
        observedPhase = movie.phase.name
        if (!enabled || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !view.isAttachedToWindow || !view.hasWindowFocus() || audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            tones.stop()
            return@LaunchedEffect
        }
        tones.play(movie.phase)
        val cue = when (movie.phase) {
            BattleFinisherPhase.REVEAL -> HapticFeedbackConstants.CONTEXT_CLICK
            BattleFinisherPhase.RELEASE -> HapticFeedbackConstants.VIRTUAL_KEY
            BattleFinisherPhase.IMPACT -> HapticFeedbackConstants.LONG_PRESS
            else -> return@LaunchedEffect
        }
        // No ignore-setting flags: Android's haptic preference and View settings remain authoritative.
        runCatching { view.performHapticFeedback(cue) }
    }
}

private data class FinisherToneNote(val tone: Int, val durationMillis: Int, val gapMillis: Int = 0)

/** Original short rising/falling electronic cues, synthesized from native tones, with no assets. */
private fun finisherPhaseTones(phase: BattleFinisherPhase): List<FinisherToneNote> = when (phase) {
    BattleFinisherPhase.CHARGE -> listOf(
        FinisherToneNote(ToneGenerator.TONE_DTMF_1, 60, 20),
        FinisherToneNote(ToneGenerator.TONE_DTMF_4, 70, 20),
        FinisherToneNote(ToneGenerator.TONE_DTMF_7, 90)
    )
    BattleFinisherPhase.RELEASE -> listOf(FinisherToneNote(ToneGenerator.TONE_DTMF_9, 140))
    BattleFinisherPhase.IMPACT -> listOf(
        FinisherToneNote(ToneGenerator.TONE_DTMF_0, 55, 15),
        FinisherToneNote(ToneGenerator.TONE_DTMF_S, 80)
    )
    BattleFinisherPhase.RESTORE -> listOf(
        FinisherToneNote(ToneGenerator.TONE_DTMF_7, 70, 15),
        FinisherToneNote(ToneGenerator.TONE_DTMF_4, 55, 15),
        FinisherToneNote(ToneGenerator.TONE_DTMF_1, 60)
    )
    else -> emptyList()
}

/** Owns only the current sub-300 ms cue and its transient focus; never advances the movie. */
private class BattleFinisherTonePlayer(
    private val context: Context,
    private val audioManager: AudioManager?
) {
    // Read-only mirror of AppMusicController's private persisted preference contract.
    private val preferences = context.getSharedPreferences("music_preferences", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var generator: ToneGenerator? = null
    private var notes: List<FinisherToneNote> = emptyList()
    private var noteIndex = 0
    private var hasFocus = false
    private var observing = false
    private var receiverRegistered = false
    private var released = false
    private val nextNote = Runnable { playNextNote() }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) stop()
        }, handler)
        .build()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // ToneGenerator has no live volume setter; stop rather than replay when settings change.
        if (key == "enabled" || key == "volume") stop()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY || !canPlayAudio()) stop()
        }
    }

    fun observeSettings() {
        if (observing || released) return
        observing = true
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        receiverRegistered = runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            true
        }.getOrDefault(false)
    }

    fun play(phase: BattleFinisherPhase) {
        stop()
        val cue = finisherPhaseTones(phase)
        val manager = audioManager ?: return
        if (released || cue.isEmpty() || !canPlayAudio()) return
        val granted = runCatching { manager.requestAudioFocus(focusRequest) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        hasFocus = true
        if (!canPlayAudio()) {
            stop()
            return
        }
        generator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, cueVolume()) }.getOrNull()
        if (generator == null) {
            stop()
            return
        }
        notes = cue
        noteIndex = 0
        playNextNote()
    }

    private fun playNextNote() {
        val player = generator ?: return
        if (released || !hasFocus || !canPlayAudio()) {
            stop()
            return
        }
        val note = notes.getOrNull(noteIndex++)
        if (note == null || !runCatching { player.startTone(note.tone, note.durationMillis) }.getOrDefault(false)) {
            stop()
            return
        }
        // This schedules notes/resource cleanup only; phase changes come exclusively from the snapshot.
        handler.postDelayed(nextNote, (note.durationMillis + note.gapMillis).toLong())
    }

    private fun cueVolume(): Int = runCatching {
        if (!preferences.getBoolean("enabled", true)) return@runCatching 0
        val volume = preferences.getFloat("volume", 0.65f)
        if (!volume.isFinite()) 0 else (volume.coerceIn(0f, 1f) * 30f).roundToInt()
    }.getOrDefault(0)

    private fun canPlayAudio(): Boolean = runCatching {
        val manager = audioManager ?: return@runCatching false
        !released && cueVolume() > 0 && manager.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            !manager.isStreamMute(AudioManager.STREAM_MUSIC) && manager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
    }.getOrDefault(false)

    fun stop() {
        handler.removeCallbacks(nextNote)
        notes = emptyList()
        noteIndex = 0
        generator?.let { player ->
            runCatching { player.stopTone() }
            runCatching { player.release() }
        }
        generator = null
        if (hasFocus) {
            hasFocus = false
            runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
        }
    }

    fun release() {
        released = true
        stop()
        if (observing) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        observing = false
        receiverRegistered = false
    }
}
