package com.tamerslab.digiline.freechat.engine

import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic surface normalization shared by every Free Talk matcher (contract §2; plan §8.2).
 *
 * Pipeline, in order:
 * 1. mojibake repair (UTF-8 read as Latin-1) for catalog names with broken accents;
 * 2. Unicode **NFKD** decomposition;
 * 3. combining-mark (accent) removal;
 * 4. lowercase with [Locale.ROOT] (never the device locale: a Turkish device must not change `i`);
 * 5. emoji/emoticon folding to intent tokens (`😢` → `sad`), apostrophes dropped (`don't` →
 *    `dont`), every other punctuation/symbol replaced by a space;
 * 6. EN + PT-BR chat/slang map (`vc` → `voce`, `pq` → `porque`, `kkk` → `laugh`, `dont` →
 *    `do not`, …), including laugh-token folding;
 * 7. whitespace collapse.
 *
 * The output is the only string the engine matches on, so authored phrases and player input go
 * through exactly the same path. The class is stateless and allocation-only, hence thread-safe.
 */
class FreeChatNormalizer {
    /** Normalizes [text] for matching; blank input yields `""`. Never throws. */
    fun normalize(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val repaired = repairMojibake(trimmed)
        val decomposed = Normalizer.normalize(repaired, Normalizer.Form.NFKD)
        val unmarked = stripCombiningMarks(decomposed)
        val lowered = unmarked.lowercase(Locale.ROOT)
        val spaced = foldSymbols(lowered)
        val mapped = applySlang(spaced)
        return collapseWhitespace(mapped)
    }

    private fun repairMojibake(value: String): String {
        // "cafÃ©" style damage only: a Latin-1 string whose UTF-8 decoding is valid.
        if (value.none { it == '\u00C3' || it == '\u00C2' }) return value
        if (value.any { it.code > 0xFF }) return value
        val decoded = value.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
        return if (decoded.contains('\uFFFD')) value else decoded
    }

    private fun stripCombiningMarks(value: String): String {
        if (value.isEmpty()) return value
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            index += Character.charCount(codePoint)
            val type = Character.getType(codePoint)
            val isMark = type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            if (!isMark) builder.appendCodePoint(codePoint)
        }
        return builder.toString()
    }

    /** Emoji → token, apostrophes dropped, letters/digits kept, everything else → space. */
    private fun foldSymbols(value: String): String {
        if (value.isEmpty()) return value
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            index += Character.charCount(codePoint)
            val symbol = String(Character.toChars(codePoint))
            val emoji = EMOJI_TOKENS[symbol]
            when {
                emoji != null -> builder.append(' ').append(emoji).append(' ')
                Character.isLetterOrDigit(codePoint) -> builder.append(symbol)
                isApostrophe(codePoint) -> Unit
                else -> builder.append(' ')
            }
        }
        return builder.toString()
    }

    private fun isApostrophe(codePoint: Int): Boolean =
        codePoint == '\''.code || codePoint == '\u2019'.code || codePoint == '\u02BC'.code

    private fun applySlang(value: String): String {
        if (value.isEmpty()) return ""
        val builder = StringBuilder(value.length)
        for (token in value.split(' ')) {
            if (token.isEmpty()) continue
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(slangFor(token))
        }
        return builder.toString()
    }

    private fun slangFor(token: String): String {
        if (LAUGH_PATTERNS.any { it.matches(token) }) return LAUGH_TOKEN
        return SLANG[token] ?: token
    }

    private fun collapseWhitespace(value: String): String =
        value.replace(WHITESPACE, " ").trim()

    companion object {
        /** Replacement token used for every laugh spelling (`kkk`, `rsrs`, `haha`, `lol`, …). */
        const val LAUGH_TOKEN = "laugh"

        private val WHITESPACE = Regex("\\s+")

        private val LAUGH_PATTERNS: List<Regex> = listOf(
            Regex("k{2,}"),
            Regex("(rs)+"),
            Regex("rs+"),
            Regex("(ha){2,}h?"),
            Regex("(he){2,}h?"),
            Regex("(hi){2,}h?"),
            Regex("(hu){2,}h?"),
            Regex("(hue)+h?"),
            Regex("(huah)+"),
            Regex("lol+"),
            Regex("lmao+"),
            Regex("rofl+"),
        )

        /**
         * Single emoji/emoticon folding. Keys are post-NFKD single code points; a folded emoji
         * becomes an ordinary query token, so the authored phrase bank can match it.
         */
        private val EMOJI_TOKENS: Map<String, String> = mapOf(
            "😀" to "happy", "😃" to "happy", "😄" to "happy", "😁" to "happy",
            "😊" to "happy", "🙂" to "happy", "😆" to "happy",
            "😢" to "sad", "😭" to "sad", "😞" to "sad", "🙁" to "sad", "☹" to "sad",
            "❤" to "love", "♥" to "love", "💕" to "love", "🥰" to "love", "😍" to "love",
            "😡" to "angry", "😠" to "angry", "🤬" to "angry",
            "🤔" to "think", "😮" to "surprised", "😲" to "surprised",
            "😉" to "playful", "😏" to "playful", "😜" to "playful",
            "😂" to "laugh", "🤣" to "laugh",
            "👍" to "ok", "👎" to "no", "🙏" to "please", "👋" to "hello",
            "😎" to "cool", "😐" to "neutral", "😑" to "neutral",
            "🔥" to "fire", "💀" to "dead", "⭐" to "star", "🌟" to "star",
            "✨" to "star", "💧" to "water", "⚡" to "lightning",
        )

        /**
         * EN + PT-BR chat abbreviation map. Keys are already accent-free/lowercase/apostrophe-free
         * (the map runs after steps 1-5), values may expand to several tokens.
         *
         * Deliberate omission: `to` is not folded to PT `estou`, because English "to" is far more
         * common in the authored EN pack than the Portuguese spelling of "I am".
         */
        private val SLANG: Map<String, String> = mapOf(
            // English abbreviations and dropped-apostrophe contractions.
            "u" to "you",
            "ur" to "your",
            "urs" to "yours",
            "r" to "are",
            "pls" to "please",
            "plz" to "please",
            "thx" to "thanks",
            "ty" to "thank you",
            "idk" to "i do not know",
            "dunno" to "do not know",
            "gonna" to "going to",
            "wanna" to "want to",
            "gotta" to "got to",
            "dont" to "do not",
            "doesnt" to "does not",
            "didnt" to "did not",
            "cant" to "cannot",
            "wont" to "will not",
            "isnt" to "is not",
            "arent" to "are not",
            "wasnt" to "was not",
            "werent" to "were not",
            "havent" to "have not",
            "hasnt" to "has not",
            "couldnt" to "could not",
            "shouldnt" to "should not",
            "wouldnt" to "would not",
            "im" to "i am",
            "ive" to "i have",
            "youre" to "you are",
            "theyre" to "they are",
            "weve" to "we have",
            "wheres" to "where is",
            "whats" to "what is",
            "thats" to "that is",
            "lets" to "let us",
            "hes" to "he is",
            "shes" to "she is",
            "its" to "it is",
            "cuz" to "because",
            "cause" to "because",
            "bc" to "because",
            "kinda" to "kind of",
            "sorta" to "sort of",
            "yall" to "you all",
            "idc" to "i do not care",
            "tbh" to "to be honest",
            "imo" to "in my opinion",
            "ngl" to "not going to lie",
            "omg" to "oh my",
            "brb" to "be right back",
            "asap" to "as soon as possible",
            "fyi" to "for your information",
            "rn" to "right now",
            "afaik" to "as far as i know",
            "smh" to "shaking my head",
            // Portuguese (pt-BR) chat abbreviations.
            "vc" to "voce",
            "vcs" to "voces",
            "ce" to "voce",
            "pq" to "porque",
            "tb" to "tambem",
            "tbm" to "tambem",
            "qnd" to "quando",
            "dps" to "depois",
            "hj" to "hoje",
            "vlw" to "valeu",
            "flw" to "falou",
            "blz" to "beleza",
            "pfv" to "por favor",
            "pf" to "por favor",
            "cmg" to "comigo",
            "ctg" to "contigo",
            "mds" to "meu deus",
            "msg" to "mensagem",
            "msm" to "mesmo",
            "mt" to "muito",
            "mto" to "muito",
            "dms" to "demais",
            "ta" to "esta",
            "tava" to "estava",
            "tamo" to "estamos",
            "ne" to "nao e",
            "sim" to "yes",
            "bora" to "vamos",
            "partiu" to "vamos",
            "tmj" to "tamo junto",
            "sqn" to "so que nao",
            "pdc" to "pode crer",
            "mlr" to "melhor",
            "oq" to "o que",
            "q" to "que",
            "vdd" to "verdade",
            "td" to "tudo",
            "tdo" to "tudo",
            "nd" to "nada",
            "ngm" to "ninguem",
        )
    }
}
