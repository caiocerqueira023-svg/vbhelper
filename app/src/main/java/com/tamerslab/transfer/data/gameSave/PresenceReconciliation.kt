package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.GameEndpointPresence
import com.tamerslab.gamesave.ObservedDigimon

/**
 * Release app partners whose written native record no longer exists in the destination save.
 *
 * A linked game can overwrite a save while VBHelper's write is pending in the same file (the game
 * session was already running with the pre-write state). In that case the game's own save simply
 * lacks the identity and no external record remains to move back; the app must stop treating the
 * individual as away so it can be sent again. The archived retained record stays untouched.
 */
internal object PresenceReconciliation {
    fun vanished(presences: List<GameEndpointPresence>, records: List<ObservedDigimon>): List<GameEndpointPresence> {
        if (presences.isEmpty()) return emptyList()
        val present = records.mapNotNull { it.nativeIdentity }.flatMap { identity -> listOf(identity, "native:$identity") }.toSet()
        return presences.filter { it.nativeIdentity !in present }
    }
}
