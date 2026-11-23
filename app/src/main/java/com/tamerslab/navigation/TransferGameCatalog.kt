package com.tamerslab.navigation

import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.TimeStrangerSaveReader
import java.util.Locale

/**
 * Origin-list identity. Dawn and Dusk persist the identical save layout, so both entries name the
 * same registered family; the file name (or a future reviewed title binding) decides which retail
 * title a linked source is shown under.
 */
enum class TransferGame(val nativeGameId: String?) {
    WORLD_DS(NdsStoryProfiles.WORLD_DS.gameId),
    DAWN(NdsStoryProfiles.DAWN_DUSK.gameId),
    DUSK(NdsStoryProfiles.DAWN_DUSK.gameId),
    LOST_EVOLUTION(NdsStoryProfiles.LOST_EVOLUTION.gameId),
    SUPER_XROS_WARS_BLUE(null), SUPER_XROS_WARS_RED(null),
    TIME_STRANGER(TimeStrangerSaveReader.GAME_ID),
}

/** Presentation grouping only. Filename hints never enable a reader, species mapping or writer. */
object TransferGameCatalog {
    fun identify(decodedGameId: String?, filename: String): TransferGame? {
        if (decodedGameId != null) {
            // A decoded title always wins: a file name never upgrades an unknown or unsupported save.
            val matches = TransferGame.entries.filter { it.nativeGameId == decodedGameId }
            if (matches.isEmpty()) return null
            if (matches.size == 1) return matches.single()
            return titleHint(filename)?.takeIf { it in matches } ?: matches.first()
        }
        val name = normalise(filename)
        if (!name.contains("digimon")) return null
        return when {
            name.contains("time stranger") -> TransferGame.TIME_STRANGER
            name.contains("lost evolution") -> TransferGame.LOST_EVOLUTION
            name.contains("dawn") || name.contains("sunburst") -> TransferGame.DAWN
            name.contains("dusk") || name.contains("moonlight") -> TransferGame.DUSK
            name.contains("xros wars") && name.contains("blue") -> TransferGame.SUPER_XROS_WARS_BLUE
            name.contains("xros wars") && name.contains("red") -> TransferGame.SUPER_XROS_WARS_RED
            name.contains("world ds") -> TransferGame.WORLD_DS
            else -> null
        }
    }

    /** The Dawn/Dusk title a file name advertises, if it does so unambiguously. */
    fun titleHint(filename: String): TransferGame? {
        val name = normalise(filename)
        return when {
            name.contains("dawn") || name.contains("sunburst") -> TransferGame.DAWN
            name.contains("dusk") || name.contains("moonlight") -> TransferGame.DUSK
            else -> null
        }
    }

    /** The retail title shown for a family id when the source itself cannot say. */
    fun defaultTitleFor(gameId: String): TransferGame? =
        TransferGame.entries.filter { it.nativeGameId == gameId }.minByOrNull { it.ordinal }

    private fun normalise(filename: String): String =
        filename.lowercase(Locale.ROOT).replace(Regex("[_-]+"), " ")
}
