package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.FreeChatNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FreeChatNormalizerTest {
    private val normalizer = FreeChatNormalizer()

    @Test
    fun stripsAccentsAndLowercasesWithRootLocale() {
        assertEquals("ola voce esta bebado", normalizer.normalize("  Olá, VOCÊ tá BÊBADO???  "))
        assertEquals("i am here", normalizer.normalize("I AM HERE"))
    }

    @Test
    fun foldsEmojiAndEmoticonsIntoIntentTokens() {
        assertEquals("hi happy", normalizer.normalize("hi 😊!!"))
        assertEquals("i am sad sad", normalizer.normalize("I am sad 😭"))
        assertEquals("love this", normalizer.normalize("❤ this"))
    }

    @Test
    fun foldsEnglishChatAbbreviations() {
        assertEquals("i do not know to be honest", normalizer.normalize("idk tbh"))
        assertEquals("you are going to be ok", normalizer.normalize("u r gonna be ok"))
        assertEquals("you are welcome", normalizer.normalize("you're welcome"))
    }

    @Test
    fun foldsPortugueseChatAbbreviations() {
        assertEquals("voce esta muito bem laugh", normalizer.normalize("vc tá mto bem? kkkk"))
        assertEquals("por favor me ajuda hoje", normalizer.normalize("pfv me ajuda hj"))
        assertEquals("porque nao yes", normalizer.normalize("pq nao sim"))
    }

    @Test
    fun foldsLaughSpellingsButNotGreetings() {
        assertEquals("laugh", normalizer.normalize("kkk"))
        assertEquals("laugh", normalizer.normalize("hahaha"))
        assertEquals("laugh", normalizer.normalize("rsrs"))
        assertEquals("laugh", normalizer.normalize("lol"))
        assertEquals("hi hi", normalizer.normalize("hi hi"))
    }

    @Test
    fun dropsApostrophesSoContractionSlangApplies() {
        assertEquals("do not", normalizer.normalize("don't"))
        assertEquals("cannot", normalizer.normalize("can't"))
        assertEquals("i am", normalizer.normalize("I'm"))
    }

    @Test
    fun repairsLatin1Mojibake() {
        assertEquals("cafe", normalizer.normalize("cafÃ©"))
        assertEquals("lowemon", normalizer.normalize("LÃ¶wemon"))
    }

    @Test
    fun turnsPunctuationAndHyphensIntoSeparators() {
        assertEquals("digivice dim card", normalizer.normalize("digivice, DIM-card!"))
        assertEquals("hello world", normalizer.normalize("hello,world"))
        assertEquals("bom dia", normalizer.normalize("bom-dia..."))
    }

    @Test
    fun keepsDigitsForPiiDetection() {
        assertEquals("cpf 12345678901", normalizer.normalize("CPF: 12345678901"))
    }

    @Test
    fun normalizesUnicodeWidthAndLigatures() {
        assertEquals("digimon", normalizer.normalize("ＤＩＧＩＭＯＮ"))
        assertEquals("unicode", normalizer.normalize("Ünïcôdé"))
    }

    @Test
    fun blankAndPunctuationOnlyInputBecomesEmpty() {
        assertEquals("", normalizer.normalize(""))
        assertEquals("", normalizer.normalize("   "))
        assertEquals("", normalizer.normalize("!!! ??? ..."))
        assertEquals("", normalizer.normalize("👨‍👩‍👧"))
    }

    @Test
    fun normalizationIsIdempotentAndDeterministic() {
        val input = "Idk tbh, kkkk!! 😊 vc tá mto bem?"
        val once = normalizer.normalize(input)
        assertEquals(once, normalizer.normalize(input))
        assertEquals(once, normalizer.normalize(once))
        assertNotEquals("", once)
    }
}
