package com.github.nacabaro.vbhelper.world

import kotlin.math.ceil
import kotlin.random.Random

/**
 * Fallback mood analyzer for wild Digimon encounters.
 *
 * When the LLM faithfully emits the [[MOOD:+N/-N]] marker, that delta is used as
 * the base (with a small random jitter so two similar messages don't always
 * produce the same number).
 *
 * When the LLM omits the marker — which happens frequently with smaller/free
 * models — this analyzer derives a delta from keyword-based sentiment analysis
 * of both the user's message and the Digimon's reply, covering EN, PT-BR and JA.
 *
 * The returned delta is **never zero**: every message moves the mood one way or
 * the other, even if only slightly. Two semantically similar messages will
 * generally yield different magnitudes thanks to [Random].
 */
object WildMoodAnalyzer {

    // ── Positive keywords (EN / PT-BR / JA) ──────────────────────────────
    private val positiveWords = listOf(
        // English
        "friend", "love", "care", "thanks", "thank you", "good", "nice", "happy",
        "help", "trust", "safe", "kind", "gentle", "sweet", "great", "awesome",
        "wonderful", "partner", "together", "smile", "warm", "hug", "glad",
        "please", "sure", "okay", "of course", "understand", "beautiful",
        // Portuguese
        "amigo", "amor", "cuidar", "obrigado", "obrigada", "bom", "boa", "feliz",
        "ajuda", "ajudar", "confiança", "confiar", "seguro", "gentil", "doce",
        "ótimo", "ótima", "incrível", "maravilhoso", "parceiro", "parceira",
        "juntos", "sorriso", "calor", "abraço", "por favor", "claro",
        "compreendo", "lindo", "belo",
        // Japanese
        "友達", "友", "愛", "世話", "ありがとう", "良い", "いい", "幸せ",
        "助け", "信頼", "安全", "優しい", "甘い", "素晴らしい", "相棒",
        "一緒", "笑顔", "温かい", "ハグ", "嬉しい", "もちろん", "分かる"
    )

    // ── Negative keywords (EN / PT-BR / JA) ──────────────────────────────
    private val negativeWords = listOf(
        // English
        "hate", "stupid", "bad", "awful", "go away", "leave me", "angry", "fight",
        "kill", "enemy", "shut up", "ugly", "dumb", "boring", "annoying", "fool",
        "idiot", "useless", "weak", "pathetic", "die", "shoo", "get lost",
        "worthless", "trash", "disgusting",
        // Portuguese
        "odeio", "ódio", "estúpido", "estupido", "ruim", "péssimo", "pessimo",
        "vai embora", "embora", "bravo", "raiva", "lutar", "matar", "inimigo",
        "cala", "cale", "feio", "burro", "chato", "idiota", "inútil", "inutil",
        "fraco", "patético", "patetico", "morre", "lixo", "nojento",
        // Japanese
        "嫌い", "ばか", "馬鹿", "悪い", "帰れ", "消え", "怒り", "戦い",
        "殺", "敵", "うるさい", "愚か", "退屈", "無駄", "弱い",
        "失せ", "ゴミ", "気持ち悪い"
    )

    /**
     * Resolves the mood delta for a single chat exchange.
     *
     * @param userText  what the Tamer typed.
     * @param reply     the Digimon's reply (already cleaned of the marker).
     * @param llmDelta  the delta the LLM emitted via [[MOOD:+N/-N]], or null.
     * @return a non-zero delta (negative = trust down, positive = trust up).
     */
    fun resolveDelta(userText: String, reply: String, llmDelta: Int?): Int {
        // ── Case 1: the LLM did emit a marker ────────────────────────────
        // Use it as the base but add a small random jitter so that two
        // similar messages don't always produce the exact same number.
        if (llmDelta != null && llmDelta != 0) {
            val jitter = if (llmDelta > 0) {
                Random.nextInt(0, 3)            // 0, 1, or 2 extra
            } else {
                Random.nextInt(-2, 1)            // -2, -1, or 0 extra
            }
            val result = llmDelta + jitter
            // Never let the jitter flip the sign or zero it out.
            return when {
                llmDelta > 0 && result <= 0 -> 1
                llmDelta < 0 && result >= 0 -> -1
                else -> result.coerceIn(-10, 10)
            }
        }

        // ── Case 2: the LLM did NOT emit a marker — use sentiment fallback
        val combined = (userText + " " + reply).lowercase()

        var positiveHits = 0
        var negativeHits = 0

        for (word in positiveWords) {
            if (word in combined) positiveHits++
        }
        for (word in negativeWords) {
            if (word in combined) negativeHits++
        }

        // Weight the user's text more heavily than the reply: the Tamer's
        // intent matters more than how the Digimon phrases things.
        val userLower = userText.lowercase()
        var userPositive = 0
        var userNegative = 0
        for (word in positiveWords) {
            if (word in userLower) userPositive++
        }
        for (word in negativeWords) {
            if (word in userLower) userNegative++
        }

        val positiveScore = positiveHits + userPositive  // double-count user signals
        val negativeScore = negativeHits + userNegative

        return when {
            positiveScore > negativeScore -> {
                // +2 to +8, with the magnitude influenced by how many
                // positive signals were detected.
                val base = (2 + positiveScore).coerceAtMost(7)
                Random.nextInt(2, base + 1)
            }
            negativeScore > positiveScore -> {
                // -3 to -10, bigger range than positive (asymmetric).
                val base = (3 + negativeScore).coerceAtMost(10)
                -Random.nextInt(3, base + 1)
            }
            else -> {
                // Neutral message: small random nudge so the bar still
                // moves. Never zero.
                if (Random.nextBoolean()) Random.nextInt(1, 4)   // +1..+3
                else -Random.nextInt(1, 4)                        // -1..-3
            }
        }
    }

    /**
     * Scales a raw mood delta so conversations have visible consequences.
     * A two-times multiplier lets a normal exchange move mood by several
     * points while preserving the original positive or negative direction.
     */
    fun scaleDelta(rawDelta: Int): Int {
        if (rawDelta == 0) return 0
        val magnitude = ceil(kotlin.math.abs(rawDelta) * 2f).toInt().coerceAtLeast(2)
        return if (rawDelta > 0) magnitude else -magnitude
    }
}
