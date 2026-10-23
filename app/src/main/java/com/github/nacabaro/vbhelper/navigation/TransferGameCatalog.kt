package com.github.nacabaro.vbhelper.navigation

import com.github.nacabaro.vbhelper.gamesave.TimeStrangerSaveReader
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader
import java.util.Locale

enum class TransferGame(val nativeGameId: String?) {
    WORLD_DS(WorldDsSaveReader.GAME_ID), DAWN(null), DUSK(null), LOST_EVOLUTION(null),
    SUPER_XROS_WARS_BLUE(null), SUPER_XROS_WARS_RED(null), TIME_STRANGER(TimeStrangerSaveReader.GAME_ID),
}

/** Presentation grouping only. Filename hints never enable a reader, species mapping or writer. */
object TransferGameCatalog {
    fun identify(decodedGameId: String?, filename: String): TransferGame? {
        if (decodedGameId != null) return TransferGame.entries.firstOrNull { it.nativeGameId == decodedGameId }
        val name = filename.lowercase(Locale.ROOT).replace(Regex("[_-]+"), " ")
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
}
