package com.github.nacabaro.vbhelper.battle

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/** Plays extracted WAV files from the APK's private asset pack. */
class AssetAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.cacheDir, "vbhelper_audio")

    private fun cachedAsset(assetPath: String): File? = runCatching {
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs()
        val cachedFile = File(cacheDirectory, assetPath.substringAfterLast('/'))
        if (!cachedFile.exists() || cachedFile.length() == 0L) {
            appContext.assets.open(assetPath).use { input ->
                cachedFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        cachedFile.takeIf { it.exists() && it.length() > 0L }
    }.getOrNull()

    fun playLoop(assetPath: String, volume: Float = 1f): MediaPlayer? = runCatching {
        val file = cachedAsset(assetPath) ?: return null
        MediaPlayer().apply {
            setDataSource(file.absolutePath)
            isLooping = true
            setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            prepare()
            start()
        }
    }.getOrElse {
        println("AssetAudioPlayer: failed to play $assetPath: ${it.message}")
        null
    }

    fun playOneShot(assetPath: String): MediaPlayer? = runCatching {
        val file = cachedAsset(assetPath) ?: return null
        MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { completed -> completed.release() }
            setOnErrorListener { failed, _, _ ->
                failed.release()
                true
            }
            prepare()
            start()
        }
    }.getOrElse {
        println("AssetAudioPlayer: failed to play $assetPath: ${it.message}")
        null
    }

    fun stop(player: MediaPlayer?) {
        player ?: return
        runCatching {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }
}
