package com.github.nacabaro.vbhelper.species

import org.junit.Assert.*
import org.junit.Test

class ResearchTextTest {
    @Test fun citationArtifactsAreRemovedWithoutChangingActualNamesOrModeNotation() {
        assertEquals(listOf("Shiratori-maru", "Toy Cannon [Mode]"), ResearchText.moves(
            listOf("Shiratori-maru[N 2]", "[N 1]", "Toy Cannon [Mode]")))
    }

    @Test fun productionTriviaIsSeparateFromBiologyAndNegativeCapabilitiesRemainIntact() {
        val profile = "Its bells only ring when it attacks. It moves quietly. This Digimon won the Digimon design contest."
        assertEquals("Its bells only ring when it attacks. It moves quietly.", ResearchText.description(profile))
        assertEquals(listOf("This Digimon won the Digimon design contest."), ResearchText.productionNotes(profile))
    }

    @Test fun LegitimateToyAndGameOriginsAreResearchFactsRatherThanProductionTrivia() {
        val profile = "Toy Agumon is made of toy blocks. It arose from game data."
        assertEquals(profile, ResearchText.description(profile))
    }
}
