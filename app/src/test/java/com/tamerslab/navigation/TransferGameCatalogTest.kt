package com.tamerslab.navigation

import com.tamerslab.gamesave.WorldDsSaveReader
import org.junit.Assert.*
import org.junit.Test

class TransferGameCatalogTest {
    @Test fun decodedTitleAlwaysWinsOverFilenameOrNickname() {
        assertEquals(TransferGame.WORLD_DS,
            TransferGameCatalog.identify(WorldDsSaveReader.GAME_ID, "Digimon World Dawn.sav"))
        assertNull(TransferGameCatalog.identify("another_adapter", "Digimon World DS.sav"))
    }

    @Test fun filenameHintsOrganizeUnsupportedGamesWithoutTreatingThemAsWorldDs() {
        assertEquals(TransferGame.DAWN, TransferGameCatalog.identify(null, "Digimon World - Dawn (USA).sav"))
        assertEquals(TransferGame.DUSK, TransferGameCatalog.identify(null, "Digimon Story Moonlight.sav"))
        assertEquals(TransferGame.LOST_EVOLUTION,
            TransferGameCatalog.identify(null, "Digimon Story - Lost Evolution (Japan) [T-En].sav"))
        assertNull(TransferGameCatalog.identify(null, "Inazuma Eleven.sav"))
        assertNull(TransferGameCatalog.identify(null, "my backup.sav"))
    }
}
