package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.FreeChatNormalizer
import com.tamerslab.digiline.freechat.engine.FreeChatSafetyFilter
import com.tamerslab.digiline.freechat.model.FreeChatSafetyClass
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeChatSafetyFilterTest {
    private val normalizer = FreeChatNormalizer()
    private val filter = FreeChatSafetyFilter(demoSafety())

    private fun classify(text: String): FreeChatSafetyClass = filter.classify(normalizer.normalize(text))

    @Test
    fun ordinaryChatIsSafe() {
        assertEquals(FreeChatSafetyClass.SAFE, classify("hello there friend"))
        assertEquals(FreeChatSafetyClass.SAFE, classify("how are you today?"))
        assertEquals(FreeChatSafetyClass.SAFE, classify(""))
    }

    @Test
    fun classifiesAuthoredS1Insults() {
        assertEquals(FreeChatSafetyClass.S1, classify("you are an idiot"))
        assertEquals(FreeChatSafetyClass.S1, classify("seu burro"))
        assertEquals(FreeChatSafetyClass.S1, classify("that was stupid"))
    }

    @Test
    fun classifiesAuthoredS2TermsAndPhrases() {
        assertEquals(FreeChatSafetyClass.S2, classify("send nudes"))
        assertEquals(FreeChatSafetyClass.S2, classify("i will kill you"))
    }

    @Test
    fun classifiesAuthoredS3SelfHarmTerms() {
        assertEquals(FreeChatSafetyClass.S3, classify("i want to suicide"))
        assertEquals(FreeChatSafetyClass.S3, classify("quero me matar"))
    }

    @Test
    fun classifiesPiiTermsAndLongDigitRuns() {
        assertEquals(FreeChatSafetyClass.PII, classify("my cpf is 123"))
        assertEquals(FreeChatSafetyClass.PII, classify("call 987654321"))
        assertEquals(FreeChatSafetyClass.SAFE, classify("room 2026 was fine"))
    }

    @Test
    fun classPriorityIsMostSevereFirst() {
        assertEquals(FreeChatSafetyClass.S3, classify("suicid kill you"))
        assertEquals(FreeChatSafetyClass.S2, classify("nude idiot"))
        assertEquals(FreeChatSafetyClass.PII, classify("idiot send cpf"))
    }

    @Test
    fun matchesAtWordStartOnly() {
        assertEquals(FreeChatSafetyClass.SAFE, classify("denuded"))
        assertEquals(FreeChatSafetyClass.S1, classify("idiotic"))
        assertEquals(FreeChatSafetyClass.SAFE, classify("kill me you"))
    }

    @Test
    fun multiWordTermsMustBeContiguous() {
        assertEquals(FreeChatSafetyClass.S2, classify("they will kill you"))
        assertEquals(FreeChatSafetyClass.SAFE, classify("kill me you"))
    }

    @Test
    fun authoredTermsAreAccentFolded() {
        val accented = FreeChatSafetyFilter(mapOf("S1" to listOf("estúpido")))
        assertEquals(FreeChatSafetyClass.S1, accented.classify(normalizer.normalize("voce e estupido")))
        assertEquals(FreeChatSafetyClass.S1, accented.classify(normalizer.normalize("ESTÚPIDO")))
    }

    @Test
    fun emptyOrUnknownSafetyMapIsAlwaysSafe() {
        assertEquals(FreeChatSafetyClass.SAFE, FreeChatSafetyFilter(emptyMap()).classify("idiot"))
        assertEquals(FreeChatSafetyClass.SAFE, FreeChatSafetyFilter(mapOf("S9" to listOf("idiot"))).classify("idiot"))
    }

    @Test
    fun sexStemDoesNotMisfireOnPortugueseWeekdays() {
        val sexFilter = FreeChatSafetyFilter(mapOf("S2" to listOf("sex")))
        assertEquals(FreeChatSafetyClass.SAFE, sexFilter.classify(normalizer.normalize("sexta feira")))
        assertEquals(FreeChatSafetyClass.S2, sexFilter.classify(normalizer.normalize("sexual")))
        assertEquals(FreeChatSafetyClass.S2, sexFilter.classify(normalizer.normalize("sexo")))
    }

    @Test
    fun classificationIsDeterministic() {
        val text = "hello friend"
        assertEquals(filter.classify(normalizer.normalize(text)), filter.classify(normalizer.normalize(text)))
    }
}
