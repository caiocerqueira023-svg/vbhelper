package com.tamerslab.battle.offline.tamers

import com.tamerslab.battle.offline.data.requireBattleParticipants
import com.tamerslab.species.PartnerParticipationSnapshot

/** Exhibition fallback applies only to the NPC opponent; owned partners and every cup remain gated. */
fun PartnerParticipationSnapshot.requireArenaMatch(spec: ArenaMatchSpec) {
    TamerBattleFactory.validate(spec)
    requireBattleParticipants(spec.left.members)
    if (spec.tournamentId != null) {
        requireBattleParticipants(spec.right.members)
    } else {
        require(spec.right.tamerId != null && spec.right.members.all {
            it.character == null && it.sourceCharacterId == null
        }) { "Artwork fallback is limited to exhibition NPCs" }
    }
}
