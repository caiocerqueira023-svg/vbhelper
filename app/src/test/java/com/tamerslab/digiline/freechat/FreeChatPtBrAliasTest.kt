package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.FreeChatNormalizer
import com.tamerslab.digiline.freechat.engine.FreeChatPtBrAliases
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PT-BR input support (owner bug report 2026-11-18): basic Portuguese phrases are aliased into the
 * EN authored intent phrasing for matching only, so a Brazilian player gets a relevant answer.
 */
class FreeChatPtBrAliasTest {
    private val normalizer = FreeChatNormalizer()

    private fun match(text: String): String = FreeChatPtBrAliases.alias(normalizer.normalize(text))

    @Test
    fun greetingsAndSmallTalkAreAliased() {
        assertEquals("hi", match("oi"))
        assertEquals("hello", match("olá"))
        assertEquals("hello", match("bom dia"))
        assertEquals("how are you", match("tudo bem?"))
        assertEquals("how are you", match("como você está"))
        assertEquals("what about you", match("e você?"))
    }

    @Test
    fun identityAndPolitenessAreAliased() {
        assertEquals("what is your name", match("qual é o seu nome"))
        assertEquals("who are you", match("quem é você"))
        assertEquals("thanks", match("obrigado"))
        assertEquals("thanks", match("valeu"))
    }

    @Test
    fun feelingsAndDayStatementsAreAliased() {
        assertEquals("i am sad", match("estou triste"))
        assertEquals("i am tired", match("estou cansado"))
        assertEquals("i had a good day", match("eu tive um bom dia"))
        assertEquals("i am worried", match("estou preocupado"))
    }

    @Test
    fun englishAndDigimonVocabularyIsLeftAlone() {
        assertEquals("hello digimon", match("Hello Digimon"))
        assertEquals("battle", match("batalha"))
        assertEquals("training", match("treino"))
    }
}
