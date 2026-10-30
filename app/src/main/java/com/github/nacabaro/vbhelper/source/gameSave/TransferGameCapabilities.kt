package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.gamesave.NdsStoryProfiles
import com.github.nacabaro.vbhelper.gamesave.NdsStorySaveEditors
import com.github.nacabaro.vbhelper.gamesave.SaveObservation
import com.github.nacabaro.vbhelper.navigation.TransferGame

/**
 * Game-capability lookup for the transfer UI and repositories. Capabilities come from the registered
 * NDS Story profiles and editors, so a second layout never needs a new `gameId == ...` literal in
 * the app. World DS is the only profile with a verified editor today; the Dawn/Dusk profile lands in
 * the next work item.
 */
object TransferGameCapabilities {
    fun isNdsStory(observation: SaveObservation): Boolean = NdsStoryProfiles.forObservation(observation) != null

    fun isNdsStoryBinding(gameId: String, formatProfile: String): Boolean =
        NdsStoryProfiles.forGameAndProfile(gameId, formatProfile) != null

    /** A game entry that can display a roster because a registered reader/profile exists. */
    fun readsNdsStory(game: TransferGame): Boolean =
        game.nativeGameId?.let { NdsStoryProfiles.forGameId(it) } != null

    /** A game entry with a native writer/editor profile (send/move destination). */
    fun hasNativeWriter(game: TransferGame?): Boolean =
        game?.nativeGameId?.let { gameId -> NdsStorySaveEditors.all.any { it.profile.gameId == gameId } } == true

    fun writerProfile(game: TransferGame?) =
        game?.nativeGameId?.let { gameId -> NdsStorySaveEditors.all.firstOrNull { it.profile.gameId == gameId } }
}
