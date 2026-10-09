package com.github.nacabaro.vbhelper.chat

import org.junit.Assert.*
import org.junit.Test

class BattleTalkDetectorTest {
    @Test fun englishFightTalkMatches() {
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("Lets fight!"))
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("I challenge you to a duel"))
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("Do you want to spar?"))
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("You want to test your strength?"))
    }
    @Test fun ordinaryChatDoesNotMatch() {
        assertFalse(BattleTalkDetector.looksLikeBattleTalk("Lets do it!"))
        assertFalse(BattleTalkDetector.looksLikeBattleTalk("Hello! How are you?"))
        assertFalse(BattleTalkDetector.looksLikeBattleTalk("Close the door on your way out"))
        assertFalse(BattleTalkDetector.looksLikeBattleTalk("Winter is coming"))
    }
    @Test fun portugueseAndJapaneseFightTalkMatches() {
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("Vamos lutar!"))
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("Eu te desafio para um duelo"))
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("戦おう！"))
        assertTrue(BattleTalkDetector.looksLikeBattleTalk("バトルしよう"))
        assertFalse(BattleTalkDetector.looksLikeBattleTalk("こんにちは"))
        assertFalse(BattleTalkDetector.looksLikeBattleTalk("Oi, tudo bem?"))
    }
}
