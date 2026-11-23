package com.tamerslab.transfer.data.gameSave

import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.NdsStorySaveEditors
import com.tamerslab.gamesave.SaveObservation
import com.tamerslab.navigation.TransferGame

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

    /**
     * Game ids whose writer editor is wired into the app-level move/send repositories. Dawn/Dusk
     * send the exact retained record only: record synthesis for that layout does not exist yet, and
     * a cross-layout send (World DS record into Dawn) is refused rather than converted here.
     */
    private val wiredWriterGameIds: Set<String> = setOf(NdsStoryProfiles.WORLD_DS.gameId,
        NdsStoryProfiles.DAWN_DUSK.gameId, NdsStoryProfiles.LOST_EVOLUTION.gameId)

    /**
     * Games whose native removal/admission writer is enabled. Lost Evolution was enabled after an
     * in-game send confirmed that the writer's two-copy body, identity windows and player-state XOR
     * checksum are accepted by the game.
     */
    private val wiredMoverGameIds: Set<String> = setOf(NdsStoryProfiles.WORLD_DS.gameId,
        NdsStoryProfiles.DAWN_DUSK.gameId, NdsStoryProfiles.LOST_EVOLUTION.gameId)

    /**
     * Native containers a send can target for [game]. World DS and Lost Evolution know their farm
     * tables; Dawn/Dusk's save layout does not serialize farms, so it only offers party and bank.
     */
    fun sendDestinations(game: TransferGame?): List<com.tamerslab.gamesave.SaveContainer> =
        when (game?.nativeGameId) {
            NdsStoryProfiles.WORLD_DS.gameId -> listOf(
                com.tamerslab.gamesave.SaveContainer.PARTY,
                com.tamerslab.gamesave.SaveContainer.BANK)
            NdsStoryProfiles.LOST_EVOLUTION.gameId -> listOf(
                com.tamerslab.gamesave.SaveContainer.FARM,
                com.tamerslab.gamesave.SaveContainer.PARTY,
                com.tamerslab.gamesave.SaveContainer.BANK)
            NdsStoryProfiles.DAWN_DUSK.gameId -> listOf(
                com.tamerslab.gamesave.SaveContainer.PARTY,
                com.tamerslab.gamesave.SaveContainer.BANK)
            else -> emptyList()
        }

    /** Default destination used when the player does not pick one. */
    fun defaultSendDestination(game: TransferGame?): com.tamerslab.gamesave.SaveContainer? =
        sendDestinations(game).firstOrNull()

    fun hasWiredWriter(game: TransferGame?): Boolean =
        game?.nativeGameId?.let { it in wiredWriterGameIds } == true && hasNativeWriter(game)

    /** Whether native removal/admission is enabled for [game] (send may still be enabled). */
    fun hasWiredMover(game: TransferGame?): Boolean =
        game?.nativeGameId?.let { it in wiredMoverGameIds } == true && hasNativeWriter(game)
}
