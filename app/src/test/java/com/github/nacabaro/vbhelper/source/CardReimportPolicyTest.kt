package com.github.nacabaro.vbhelper.source

import org.junit.Assert.*
import org.junit.Test

class CardReimportPolicyTest {
    @Test fun namedVariantWinsEvenWhenIncomingAttacksMatchAnotherVariant() {
        assertEquals(7L, CardReimportPolicy.select(listOf(
            CardReimportCandidate(7, "one", false, false),
            CardReimportCandidate(12, "two", true, false)), "one"))
    }

    @Test fun renamedAutomaticOrManualCardCanBeFoundByUniqueContent() {
        assertEquals(7L, CardReimportPolicy.select(listOf(
            CardReimportCandidate(7, "My name", true, false),
            CardReimportCandidate(12, "Other", false, false)), "new filename"))
    }

    @Test fun ambiguousContentNeverSelectsOrUpdatesSeveralCards() {
        assertNull(CardReimportPolicy.select(listOf(
            CardReimportCandidate(7, "one", true, false),
            CardReimportCandidate(12, "two", true, false)), "new filename"))
    }

    @Test fun missingLegacyArtIsRepairableOnlyWhenIdentityIsUnambiguous() {
        assertEquals(7L, CardReimportPolicy.select(listOf(CardReimportCandidate(7, "Legacy", false, true)), "original"))
        assertNull(CardReimportPolicy.select(listOf(
            CardReimportCandidate(7, "Legacy one", false, true),
            CardReimportCandidate(12, "Legacy two", false, true)), "original"))
    }

    @Test fun duplicateNamesNeedOneUniqueContentMatch() {
        val candidates = listOf(CardReimportCandidate(7, "Same", false, false), CardReimportCandidate(12, "Same", true, false))
        assertEquals(12L, CardReimportPolicy.select(candidates, "same"))
        assertNull(CardReimportPolicy.select(candidates.map { it.copy(attackMatches = true) }, "same"))
    }
}
