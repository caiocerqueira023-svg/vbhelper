package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.EntityLexicon
import com.tamerslab.digiline.freechat.engine.FreeChatNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityLexiconTest {
    private val normalizer = FreeChatNormalizer()
    private val lexicon = EntityLexicon(
        listOf("Agumon", "Metal Greymon", "Greymon", "Agumon (Black)", "Bun"),
    )

    private fun extract(text: String) = lexicon.extract(normalizer.normalize(text))

    @Test
    fun longestSpeciesMatchWinsAndIsConsumedOnce() {
        val mentions = extract("metal greymon is here")
        assertEquals(setOf("metalgreymon"), mentions.speciesKeys)
    }

    @Test
    fun matchesCompactAndSpacedAuthoredNames() {
        assertEquals(setOf("metalgreymon"), extract("metalgreymon").speciesKeys)
        assertEquals(setOf("metalgreymon"), extract("metal greymon").speciesKeys)
        assertEquals(setOf("agumonblack"), extract("agumon black").speciesKeys)
        assertEquals(setOf("agumon"), extract("agumon").speciesKeys)
    }

    @Test
    fun findsMultipleSpeciesOnlyWhenAllAreNamed() {
        assertEquals(setOf("agumonblack", "agumon"), extract("agumon black and agumon").speciesKeys)
        assertEquals(setOf("agumon"), extract("agumon").speciesKeys)
    }

    @Test
    fun ordinaryWordNamesAreNotHardExtracted() {
        assertTrue(extract("i ate a bun").speciesKeys.isEmpty())
        assertTrue(extract("bun").speciesKeys.isEmpty())
    }

    @Test
    fun extractsWorldTermsLongestFirst() {
        val mentions = extract("tell me about the digital world and the digivice")
        assertEquals(setOf("digital world", "digivice"), mentions.worldTerms)
        assertEquals(setOf("digital world", "digivice"), mentions.worldTerms)
    }

    @Test
    fun extractsLevelsWithCanonicalSpelling() {
        assertEquals(setOf("adult"), extract("adult level").levels)
        assertEquals(setOf("baby ii"), extract("baby ii").levels)
        assertEquals(setOf("baby ii"), extract("baby 2").levels)
        assertEquals(setOf("baby i"), extract("baby 1").levels)
        assertEquals(setOf("in training"), extract("in training form").levels)
    }

    @Test
    fun attributesFlowsThroughWorldTerms() {
        val mentions = extract("vaccine data virus")
        assertEquals(setOf("vaccine", "data", "virus"), mentions.worldTerms)
        assertTrue(mentions.levels.isEmpty())
    }

    @Test
    fun emptySpeciesListKeepsWorldLexiconWorking() {
        val empty = EntityLexicon(emptyList())
        assertTrue(empty.extract(normalizer.normalize("agumon")).speciesKeys.isEmpty())
        assertEquals(setOf("digimon"), empty.extract(normalizer.normalize("digimon")).worldTerms)
    }

    @Test
    fun extractionIsDeterministic() {
        val first = extract("metal greymon fights in the digital world as an adult")
        val second = extract("metal greymon fights in the digital world as an adult")
        assertEquals(first, second)
        assertEquals(setOf("metalgreymon"), first.speciesKeys)
        assertEquals(setOf("digital world"), first.worldTerms)
        assertEquals(setOf("adult"), first.levels)
    }
}
