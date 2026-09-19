package com.github.nacabaro.vbhelper.audio

import android.content.Context
import android.media.MediaPlayer
import com.github.nacabaro.vbhelper.battle.AssetAudioPlayer
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicSettings(
    val enabled: Boolean,
    val volume: Float
)

/** Owns the single looping soundtrack used by the app. */
class AppMusicController(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val audioPlayer = AssetAudioPlayer(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    private val _settings = MutableStateFlow(
        MusicSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, true),
            volume = preferences.getFloat(KEY_VOLUME, DEFAULT_VOLUME).coerceIn(0f, 1f)
        )
    )
    val settings: StateFlow<MusicSettings> = _settings.asStateFlow()

    private var requestedTrack: String? = null
    private var playbackGeneration = 0L
    private var mediaPlayer: MediaPlayer? = null
    private var appInForeground = true

    fun play(assetPath: String) {
        val restart: Pair<Long, MediaPlayer?> = synchronized(lock) {
            val isAlreadyPlaying = requestedTrack == assetPath &&
                mediaPlayer?.let { runCatching { it.isPlaying }.getOrDefault(false) } == true
            if (isAlreadyPlaying) return

            requestedTrack = assetPath
            playbackGeneration += 1
            val oldPlayer = mediaPlayer
            mediaPlayer = null
            playbackGeneration to oldPlayer
        }

        audioPlayer.stop(restart.second)
        val canPlay = synchronized(lock) { appInForeground } && _settings.value.enabled
        if (!canPlay) return

        val generation = restart.first
        scope.launch {
            val player = withContext(Dispatchers.IO) {
                audioPlayer.playLoop(assetPath, _settings.value.volume)
            }
            if (player == null) return@launch

            val shouldKeep = synchronized(lock) {
                    generation == playbackGeneration &&
                    requestedTrack == assetPath &&
                    appInForeground &&
                    _settings.value.enabled
            }
            if (!shouldKeep) {
                audioPlayer.stop(player)
                return@launch
            }

            val oldPlayer = synchronized(lock) {
                val old = mediaPlayer
                mediaPlayer = player
                old
            }
            audioPlayer.stop(oldPlayer)
        }
    }

    fun setEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(enabled = enabled)
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()

        if (enabled) {
            play(requestedTrack ?: BattleAssetPaths.HOME_MUSIC)
        } else {
            stopPlayback()
        }
    }

    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        _settings.value = _settings.value.copy(volume = clampedVolume)
        preferences.edit().putFloat(KEY_VOLUME, clampedVolume).apply()

        val player = synchronized(lock) { mediaPlayer }
        runCatching { player?.setVolume(clampedVolume, clampedVolume) }
    }

    fun setAppInForeground(isForeground: Boolean) {
        synchronized(lock) { appInForeground = isForeground }
        if (isForeground) {
            requestedTrack?.let(::play)
        } else {
            stopPlayback()
        }
    }

    fun release() {
        stopPlayback()
        scope.cancel()
    }

    private fun stopPlayback() {
        val player = synchronized(lock) {
            playbackGeneration += 1
            val old = mediaPlayer
            mediaPlayer = null
            old
        }
        audioPlayer.stop(player)
    }

    private companion object {
        const val PREFERENCES_NAME = "music_preferences"
        const val KEY_ENABLED = "enabled"
        const val KEY_VOLUME = "volume"
        const val DEFAULT_VOLUME = 0.65f
    }
}
