package com.github.nacabaro.vbhelper.screens.transferScreen

import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.RecordIssue
import com.github.nacabaro.vbhelper.gamesave.RecordMembership
import com.github.nacabaro.vbhelper.gamesave.SaveContainer
import com.github.nacabaro.vbhelper.gamesave.SaveObservation
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader

/**
 * A DS card carries the same save twice, so both copies list the same Digimon. Lists show one
 * entry per native identity and container: the chosen copy's active record wins, then a record
 * that only needs review, then the first copy. Records without a card copy or native
 * identity stay untouched.
 *
 * World DS identities are save-local and therefore unique across containers. Keys that include
 * the container would still show a Digimon twice when the two copies disagree about whether it
 * is in the party, farm or storage. For readers with that single-copy authority, collapse across
 * containers as well.
 */
internal fun collapseSaveCopies(
    records: List<ObservedDigimon>,
    uniqueIdentityAcrossContainers: Boolean = false,
): List<ObservedDigimon> {
    val shown = mutableListOf<ObservedDigimon>()
    val shownAt = mutableMapOf<String, Int>()
    for (record in records) {
        val identity = record.nativeIdentity
        val block = record.nativeParameters?.saveBlock
        if (identity.isNullOrBlank() || block == null) { shown += record; continue }
        val key = if (uniqueIdentityAcrossContainers) identity else "${record.container}:$identity"
        val existing = shownAt[key]
        if (existing == null) {
            shownAt[key] = shown.size
            shown += record
        } else if (rank(record) < rank(shown[existing])) {
            shown[existing] = record
        }
    }
    return shown
}

/** The records a workspace tab shows after collapsing card copies. */
internal fun shownRecords(observation: SaveObservation, container: SaveContainer? = null): List<ObservedDigimon> =
    collapseSaveCopies(observation.records, observation.gameId == WorldDsSaveReader.GAME_ID)
        .filter { container == null || it.container == container }

private fun rank(record: ObservedDigimon): Int = when {
    RecordIssue.AMBIGUOUS_SAVE_BLOCK in record.issues -> 3
    record.membership == RecordMembership.ACTIVE_RECORD -> 1
    else -> 2
}

/** Every record key a list actually shows; selections outside it can never reach a preview. */
internal fun shownRecordKeys(observation: SaveObservation): Set<String> =
    shownRecords(observation).map { it.recordKey }.toSet()
