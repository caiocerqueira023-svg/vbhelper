package com.tamerslab.navigation

import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.WorldDsSaveReader
import org.junit.Assert.*
import org.junit.Test

class TransferGameCatalogTest {
    @Test fun decodedTitleAlwaysWinsOverFilenameOrNickname() {
        assertEquals(TransferGame.WORLD_DS,
            TransferGameCatalog.identify(WorldDsSaveReader.GAME_ID, "Digimon World Dawn.sav"))
        assertNull(TransferGameCatalog.identify("another_adapter", "Digimon World DS.sav"))
    }

    @Test fun dawnAndDuskShareOneFamilyAndTheFileNamePicksTheRetailTitle() {
        val family = NdsStoryProfiles.DAWN_DUSK.gameId
        assertEquals(TransferGame.DAWN, TransferGameCatalog.identify(family, "Digimon World - Dawn (USA).sav"))
        assertEquals(TransferGame.DUSK, TransferGameCatalog.identify(family, "Digimon World Dusk [NDS].sav"))
        assertEquals(TransferGame.DAWN, TransferGameCatalog.identify(family, "Digimon World Sunburst (Japan).sav"))
        assertEquals(TransferGame.DUSK, TransferGameCatalog.identify(family, "Digimon Story Moonlight.sav"))
        // A generic file name still lands in the family, never in another game.
        assertEquals(TransferGame.DAWN, TransferGameCatalog.identify(family, "save.sav"))
        assertEquals(TransferGame.DAWN, TransferGameCatalog.defaultTitleFor(family))
    }

    @Test fun filenameHintsOrganizeUnsupportedGamesWithoutTreatingThemAsWorldDs() {
        assertEquals(TransferGame.DAWN, TransferGameCatalog.identify(null, "Digimon World - Dawn (USA).sav"))
        assertEquals(TransferGame.DUSK, TransferGameCatalog.identify(null, "Digimon Story Moonlight.sav"))
        assertEquals(TransferGame.LOST_EVOLUTION,
            TransferGameCatalog.identify(null, "Digimon Story - Lost Evolution (Japan) [T-En].sav"))
        assertNull(TransferGameCatalog.identify(null, "Inazuma Eleven.sav"))
        assertNull(TransferGameCatalog.identify(null, "my backup.sav"))
    }

    @Test fun decodedLostEvolutionSaveIdentifiesTheRegisteredTitle() {
        val gameId = NdsStoryProfiles.LOST_EVOLUTION.gameId
        assertEquals(TransferGame.LOST_EVOLUTION, TransferGameCatalog.identify(gameId, "save.sav"))
        assertEquals(TransferGame.LOST_EVOLUTION,
            TransferGameCatalog.identify(gameId, "Digimon Story - Lost Evolution (Japan) [T-En].sav"))
        assertEquals(TransferGame.LOST_EVOLUTION, TransferGameCatalog.defaultTitleFor(gameId))
        assertNull(TransferGameCatalog.identify("unknown_game_id", "Digimon Story - Lost Evolution.sav"))
    }
}
