package com.tamerslab.transfer.data.gameSave

import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.SaveObservation
import com.tamerslab.navigation.TransferGame
import org.junit.Assert.*
import org.junit.Test

class TransferGameCapabilitiesTest {
    private fun observation(gameId: String, profile: String, codec: Int = 1) =
        SaveObservation(gameId, profile, codec, "revision", emptyList(), emptyList())

    @Test fun worldDsIsTheRegisteredNdsStoryOriginAndWiredWriter() {
        assertTrue(TransferGameCapabilities.readsNdsStory(TransferGame.WORLD_DS))
        assertTrue(TransferGameCapabilities.hasNativeWriter(TransferGame.WORLD_DS))
        assertTrue(TransferGameCapabilities.hasWiredWriter(TransferGame.WORLD_DS))
        assertEquals(NdsStoryProfiles.WORLD_DS, TransferGameCapabilities.writerProfile(TransferGame.WORLD_DS)?.profile)
    }

    @Test fun dawnAndDuskAreReadableAndHaveAVerifiedCoreEditor() {
        for (game in listOf(TransferGame.DAWN, TransferGame.DUSK)) {
            assertTrue(TransferGameCapabilities.readsNdsStory(game))
            assertTrue(TransferGameCapabilities.hasNativeWriter(game))
            assertEquals(NdsStoryProfiles.DAWN_DUSK, TransferGameCapabilities.writerProfile(game)?.profile)
            assertTrue(TransferGameCapabilities.hasWiredWriter(game))
        }
    }

    @Test fun lostEvolutionIsReadableAndHasAWiredWriter() {
        assertTrue(TransferGameCapabilities.readsNdsStory(TransferGame.LOST_EVOLUTION))
        assertTrue(TransferGameCapabilities.hasNativeWriter(TransferGame.LOST_EVOLUTION))
        assertTrue(TransferGameCapabilities.hasWiredWriter(TransferGame.LOST_EVOLUTION))
        assertEquals(NdsStoryProfiles.LOST_EVOLUTION,
            TransferGameCapabilities.writerProfile(TransferGame.LOST_EVOLUTION)?.profile)
    }

    @Test fun unregisteredTitleEntriesStayShells() {
        assertFalse(TransferGameCapabilities.readsNdsStory(TransferGame.SUPER_XROS_WARS_BLUE))
        assertFalse(TransferGameCapabilities.hasNativeWriter(TransferGame.TIME_STRANGER))
        assertNull(TransferGameCapabilities.writerProfile(TransferGame.TIME_STRANGER))
    }

    @Test fun observationsAreClassifiedByTheirFullProfileTriple() {
        val worldDs = NdsStoryProfiles.WORLD_DS
        assertTrue(TransferGameCapabilities.isNdsStory(observation(worldDs.gameId, worldDs.formatProfile)))
        assertTrue(TransferGameCapabilities.isNdsStoryBinding(worldDs.gameId, worldDs.formatProfile))
        assertFalse(TransferGameCapabilities.isNdsStory(observation(worldDs.gameId, "not-the-world-ds-profile")))
        assertFalse(TransferGameCapabilities.isNdsStory(observation(worldDs.gameId, worldDs.formatProfile, codec = 2)))
        assertFalse(TransferGameCapabilities.isNdsStoryBinding(worldDs.gameId, "not-the-world-ds-profile"))
        val ds2 = NdsStoryProfiles.DAWN_DUSK
        assertTrue(TransferGameCapabilities.isNdsStory(observation(ds2.gameId, ds2.formatProfile)))
        assertTrue(TransferGameCapabilities.isNdsStoryBinding(ds2.gameId, ds2.formatProfile))
        val le = NdsStoryProfiles.LOST_EVOLUTION
        assertTrue(TransferGameCapabilities.isNdsStory(observation(le.gameId, le.formatProfile)))
        assertTrue(TransferGameCapabilities.isNdsStoryBinding(le.gameId, le.formatProfile))
    }
}
