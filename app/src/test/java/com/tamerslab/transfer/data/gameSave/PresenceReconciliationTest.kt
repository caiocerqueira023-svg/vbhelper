package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.GameEndpointPresence
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceReconciliationTest {
    private fun record(identity: String?) = ObservedDigimon("copy:2:record:0", SaveContainer.PARTY, 1, 77,
        "Agumon", 4, null, 0, RecordMembership.ACTIVE_RECORD, nativeIdentity = identity)

    private fun presence(id: String, identity: String) = GameEndpointPresence(id, "source", "document",
        "digimon_story_lost_evolution_nds", identity, "operation", 0)

    @Test fun keepsPartnersThatAreStillInsideTheSave() {
        val presences = listOf(presence("a", "native:save-local:30405f000000005"))
        assertTrue(PresenceReconciliation.vanished(presences, listOf(record("save-local:30405f000000005"))).isEmpty())
        assertTrue(PresenceReconciliation.vanished(presences, listOf(record("native:save-local:30405f000000005"))).isEmpty())
    }

    @Test fun releasesPartnersTheGameSaveNoLongerContains() {
        val presences = listOf(presence("a", "native:save-local:30405f000000005"), presence("b", "native:save-local:30405f000000002"))
        val vanished = PresenceReconciliation.vanished(presences, listOf(record("save-local:30405f000000002")))
        assertEquals(listOf("a"), vanished.map { it.individualId })
    }

    @Test fun aSaveWithoutAnyMatchingIdentityReleasesEveryPresence() {
        val presences = listOf(presence("a", "native:save-local:1"), presence("b", "native:save-local:2"))
        assertEquals(listOf("a", "b"), PresenceReconciliation.vanished(presences, emptyList()).map { it.individualId })
        assertTrue(PresenceReconciliation.vanished(emptyList(), emptyList()).isEmpty())
    }
}
