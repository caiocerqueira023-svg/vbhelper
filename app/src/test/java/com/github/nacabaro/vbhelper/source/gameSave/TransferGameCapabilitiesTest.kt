package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.gamesave.NdsStoryProfiles
import com.github.nacabaro.vbhelper.gamesave.SaveObservation
import com.github.nacabaro.vbhelper.navigation.TransferGame
import org.junit.Assert.*
import org.junit.Test

class TransferGameCapabilitiesTest {
    private fun observation(gameId: String, profile: String, codec: Int = 1) =
        SaveObservation(gameId, profile, codec, "revision", emptyList(), emptyList())

    @Test fun worldDsIsTheRegisteredNdsStoryOriginAndWriter() {
        assertTrue(TransferGameCapabilities.readsNdsStory(TransferGame.WORLD_DS))
        assertTrue(TransferGameCapabilities.hasNativeWriter(TransferGame.WORLD_DS))
        assertEquals(NdsStoryProfiles.WORLD_DS, TransferGameCapabilities.writerProfile(TransferGame.WORLD_DS)?.profile)
    }

    @Test fun unregisteredTitleEntriesStayShells() {
        assertFalse(TransferGameCapabilities.readsNdsStory(TransferGame.DAWN))
        assertFalse(TransferGameCapabilities.readsNdsStory(TransferGame.DUSK))
        assertFalse(TransferGameCapabilities.hasNativeWriter(TransferGame.DAWN))
        assertFalse(TransferGameCapabilities.hasNativeWriter(TransferGame.TIME_STRANGER))
        assertNull(TransferGameCapabilities.writerProfile(TransferGame.DUSK))
    }

    @Test fun observationsAreClassifiedByTheirFullProfileTriple() {
        val worldDs = NdsStoryProfiles.WORLD_DS
        assertTrue(TransferGameCapabilities.isNdsStory(observation(worldDs.gameId, worldDs.formatProfile)))
        assertTrue(TransferGameCapabilities.isNdsStoryBinding(worldDs.gameId, worldDs.formatProfile))
        assertFalse(TransferGameCapabilities.isNdsStory(observation(worldDs.gameId, "not-the-world-ds-profile")))
        assertFalse(TransferGameCapabilities.isNdsStory(observation(worldDs.gameId, worldDs.formatProfile, codec = 2)))
        assertFalse(TransferGameCapabilities.isNdsStoryBinding(worldDs.gameId, "not-the-world-ds-profile"))
    }
}
