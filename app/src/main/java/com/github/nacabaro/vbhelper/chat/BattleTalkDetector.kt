package com.github.nacabaro.vbhelper.chat

/**
 * Cheap keyword gate deciding whether a chat turn is worth one follow-up LLM
 * call to recover a dropped battle intent. It never authorizes anything itself;
 * the recovered proposal still goes through [WorldDialogueCodec] validation and
 * the duel policy before any fight can start. Matching is intentionally broad:
 * a false positive only costs one extra model call that returns null.
 */
object BattleTalkDetector {
    private val substringKeywords = listOf(
        // EN
        "fight", "duel", "battle", "spar", "challenge", "brawl", "rematch", "wrestle",
        "knockout", "strength", "victory", "defeat",
        // PT
        "luta", "lutar", "duelo", "batalha", "desafio", "briga", "revanche",
        "força", "vencer", "derrota", "ganhar", "perder",
        // JA
        "戦", "闘", "バトル", "決闘", "挑戦", "勝負", "対決", "対戦", "喧嘩",
        "勝ち", "負け", "勝利"
    )
    private val wholeWordKeywords = listOf("win", "wins", "lose", "loses", "forca")
    private val wholeWordPattern =
        wholeWordKeywords.joinToString("|", prefix = "\\b(", postfix = ")\\b") { Regex.escape(it) }.toRegex()

    fun looksLikeBattleTalk(text: String): Boolean {
        val lower = text.lowercase()
        if (wholeWordPattern.containsMatchIn(lower)) return true
        return substringKeywords.any { lower.contains(it.lowercase()) }
    }
}
