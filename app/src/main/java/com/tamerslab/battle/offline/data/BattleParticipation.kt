package com.tamerslab.battle.offline.data

import com.tamerslab.battle.ui.OfflineBattleParticipant
import com.tamerslab.species.PartnerParticipationSnapshot

/** Revalidates frozen teams as well as fresh selections; a source ID alone never authorizes a stale form. */
fun PartnerParticipationSnapshot.requireBattleParticipants(participants: List<OfflineBattleParticipant>) {
    participants.forEach { participant ->
        val owned = participant.character?.id ?: participant.sourceCharacterId
        val card = participant.character?.charId ?: participant.cardCharacterId
        when {
            owned != null -> requireOwned(owned, participant.individualId, card)
            card != null -> requireCard(card)
            else -> requireSpecies(participant.speciesName ?: participant.displayName)
        }
        participant.blastTargetSpecies?.let(::requireSpecies)
        participant.jogressResultSpecies?.let(::requireSpecies)
    }
}
