package com.tamerslab.transfer.vpet

import com.tamerslab.domain.vpet.VpetPendingReason
import org.junit.Assert.assertEquals
import org.junit.Test

class VpetImportPolicyTest {

    @Test
    fun knownSpeciesWithAllowedParticipationCreatesANewIndividual() {
        val plan = VpetImportPolicy.plan(
            decodedName = "Agumon",
            canonicalId = "digimon.agumon",
            canonicalName = "Agumon",
            participationAllowed = true,
        )
        assertEquals(VpetImportPolicy.SpeciesPlan.CreateIndividual("digimon.agumon"), plan)
    }

    @Test
    fun unknownSpeciesKeepsTheDecodedNameAsPendingProvenance() {
        val plan = VpetImportPolicy.plan(
            decodedName = "Mysterymon",
            canonicalId = null,
            canonicalName = null,
            participationAllowed = true,
        )
        assertEquals(
            VpetImportPolicy.SpeciesPlan.KeepPending(VpetPendingReason.UNKNOWN_SPECIES, "Mysterymon"),
            plan,
        )
    }

    @Test
    fun readWithoutANameStaysPendingWithAnEmptyProvenanceName() {
        val plan = VpetImportPolicy.plan(
            decodedName = null,
            canonicalId = null,
            canonicalName = null,
            participationAllowed = true,
        )
        assertEquals(
            VpetImportPolicy.SpeciesPlan.KeepPending(VpetPendingReason.UNKNOWN_SPECIES, ""),
            plan,
        )
    }

    @Test
    fun blankCanonicalIdCountsAsUnknownEvenWhenANameWasDecoded() {
        val plan = VpetImportPolicy.plan(
            decodedName = "Agumon",
            canonicalId = "  ",
            canonicalName = "Agumon",
            participationAllowed = true,
        )
        assertEquals(
            VpetImportPolicy.SpeciesPlan.KeepPending(VpetPendingReason.UNKNOWN_SPECIES, "Agumon"),
            plan,
        )
    }

    @Test
    fun blockedParticipationKeepsTheResolvedNameAsThePendingReason() {
        val plan = VpetImportPolicy.plan(
            decodedName = "Greymon",
            canonicalId = "digimon.greymon",
            canonicalName = "Greymon",
            participationAllowed = false,
        )
        assertEquals(
            VpetImportPolicy.SpeciesPlan.KeepPending(VpetPendingReason.PARTICIPATION_BLOCKED, "Greymon"),
            plan,
        )
    }

    @Test
    fun blockedParticipationFallsBackToTheCanonicalIdWhenTheNameIsBlank() {
        val plan = VpetImportPolicy.plan(
            decodedName = "Greymon",
            canonicalId = "digimon.greymon",
            canonicalName = " ",
            participationAllowed = false,
        )
        assertEquals(
            VpetImportPolicy.SpeciesPlan.KeepPending(
                VpetPendingReason.PARTICIPATION_BLOCKED,
                "digimon.greymon",
            ),
            plan,
        )
    }

    @Test
    fun repeatedReadsAlwaysPlanANewIndividual() {
        // PLAN.md D4: the policy is stateless, so importing the same read again still creates a
        // brand-new individual instead of returning a previously imported one.
        val first = VpetImportPolicy.plan("Agumon", "digimon.agumon", "Agumon", participationAllowed = true)
        val second = VpetImportPolicy.plan("Agumon", "digimon.agumon", "Agumon", participationAllowed = true)
        assertEquals(VpetImportPolicy.SpeciesPlan.CreateIndividual("digimon.agumon"), first)
        assertEquals(first, second)
    }
}
