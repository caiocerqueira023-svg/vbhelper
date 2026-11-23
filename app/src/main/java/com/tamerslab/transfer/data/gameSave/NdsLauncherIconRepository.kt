package com.tamerslab.transfer.data.gameSave

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import com.tamerslab.gamesave.NdsLauncherBanner
import com.tamerslab.navigation.TransferGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Stores only verified-title banner pixels, never a ROM. No save/collection state is changed. */
class NdsLauncherIconRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, "transfer-launcher-icons")

    fun hasIcon(game: TransferGame): Boolean = AtomicFile(File(root, "${game.name}.argb")).baseFile.exists()

    /** Titles whose banner/title code association is verified and can supply an icon. */
    fun supports(game: TransferGame): Boolean = supportsTitle(game)

    suspend fun icon(game: TransferGame): Bitmap? = withContext(Dispatchers.IO) {
        val file = AtomicFile(File(root, "${game.name}.argb"))
        if (!file.baseFile.exists()) return@withContext null
        runCatching { file.openRead().use { stream ->
            val input = DataInputStream(stream)
            require(accepts(game, input.readUTF()))
            val pixels = IntArray(1024) { input.readInt() }
            require(input.read() == -1)
            Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        } }.getOrNull()
    }

    suspend fun import(game: TransferGame, banner: NdsLauncherBanner) = withContext(Dispatchers.IO) {
        require(banner.argb.size == 1024)
        require(accepts(game, banner.gameCode)) { "The ROM belongs to another game" }
        check(root.isDirectory || root.mkdirs())
        val file = AtomicFile(File(root, "${game.name}.argb"))
        val output = file.startWrite()
        try {
            val data = DataOutputStream(output)
            data.writeUTF(banner.gameCode)
            banner.argb.forEach(data::writeInt)
            data.flush()
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }

    private fun accepts(game: TransferGame, code: String): Boolean = supports(game) && acceptsCode(game, code)

    companion object {
        /**
         * Titles whose banner/title code association is verified. Lost Evolution is the Japanese
         * `BLEJ` development code, matched by its `BLE` prefix like the other verified titles.
         */
        fun supportsTitle(game: TransferGame): Boolean = when (game) {
            TransferGame.WORLD_DS, TransferGame.DAWN, TransferGame.DUSK, TransferGame.LOST_EVOLUTION -> true
            else -> false
        }

        fun acceptsCode(game: TransferGame, code: String): Boolean = supportsTitle(game) && when (game) {
            TransferGame.WORLD_DS -> code.take(3) == "ADN"
            TransferGame.DAWN -> code.take(3) == "A3V"
            TransferGame.DUSK -> code.take(3) == "A6R"
            TransferGame.LOST_EVOLUTION -> code.take(3) == "BLE"
            else -> false // Other titles need verified banner/title metadata before accepting an association.
        }
    }
}
