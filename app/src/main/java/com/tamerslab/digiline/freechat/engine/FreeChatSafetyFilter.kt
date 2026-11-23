package com.tamerslab.digiline.freechat.engine

import com.tamerslab.digiline.freechat.model.FreeChatSafetyClass

/**
 * Offline input-safety classifier (contract §2: S1/S2/S3/PII from the pack's authored safety
 * lexicon, word-boundary, never echoes the input).
 *
 * Matching rules:
 * - terms and text are normalized through the same [FreeChatNormalizer], so `"estúpido"` and
 *   `"estupido"` are equivalent;
 * - a term matches at a **word start**, exactly or as a stem (`suicid` → `suicide`, `matar` →
 *   `matando`); a term never matches in the middle of a word;
 * - multi-word terms must match contiguous tokens (`kill you`, `me matar`);
 * - a run of seven or more digits is treated as PII even if no authored term covers it.
 *
 * Class priority is S3 > S2 > PII > S1: the most severe signal wins when a turn matches several
 * classes. The filter only returns an enum, so there is no path that could echo the player's text
 * back into a reply.
 */
class FreeChatSafetyFilter(private val safety: Map<String, List<String>>) {
    private val normalizer = FreeChatNormalizer()
    private val authoredTerms: Map<FreeChatSafetyClass, List<List<String>>>

    init {
        val byClass = LinkedHashMap<FreeChatSafetyClass, MutableList<List<String>>>()
        for ((rawClass, rawTerms) in safety) {
            val safetyClass = parseClass(rawClass) ?: continue
            val bucket = byClass.getOrPut(safetyClass) { ArrayList() }
            for (rawTerm in rawTerms) {
                val tokens = normalizer.normalize(rawTerm).split(' ').filter { it.isNotEmpty() }
                if (tokens.isNotEmpty()) bucket.add(tokens)
            }
        }
        val frozen = LinkedHashMap<FreeChatSafetyClass, List<List<String>>>(byClass.size)
        for (safetyClass in CLASS_PRIORITY) {
            val terms = byClass[safetyClass] ?: continue
            if (terms.isNotEmpty()) frozen[safetyClass] = terms
        }
        authoredTerms = frozen
    }

    /** Classifies an already normalized string. Returns [FreeChatSafetyClass.SAFE] for no match. */
    fun classify(normalizedText: String): FreeChatSafetyClass {
        val tokens = normalizedText.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return FreeChatSafetyClass.SAFE
        if (matches(authoredTerms[FreeChatSafetyClass.S3], tokens)) return FreeChatSafetyClass.S3
        if (matches(authoredTerms[FreeChatSafetyClass.S2], tokens)) return FreeChatSafetyClass.S2
        if (matches(authoredTerms[FreeChatSafetyClass.PII], tokens) || containsLongDigitRun(normalizedText)) {
            return FreeChatSafetyClass.PII
        }
        if (matches(authoredTerms[FreeChatSafetyClass.S1], tokens)) return FreeChatSafetyClass.S1
        return FreeChatSafetyClass.SAFE
    }

    private fun matches(terms: List<List<String>>?, textTokens: List<String>): Boolean {
        if (terms.isNullOrEmpty()) return false
        for (termTokens in terms) {
            if (containsTerm(textTokens, termTokens)) return true
        }
        return false
    }

    private fun containsTerm(textTokens: List<String>, termTokens: List<String>): Boolean {
        if (termTokens.size > textTokens.size) return false
        val lastStart = textTokens.size - termTokens.size
        for (start in 0..lastStart) {
            var matched = true
            for (offset in termTokens.indices) {
                if (!tokenMatches(textTokens[start + offset], termTokens[offset])) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

    /**
     * Word-start match: equality, or a stem prefix for terms of length >= 3. `"sexta"` (Friday) is
     * deliberately exempt from the `"sex"` stem so ordinary Portuguese chat is not deflected.
     */
    private fun tokenMatches(textToken: String, termToken: String): Boolean {
        if (textToken == termToken) return true
        if (termToken.length < MIN_STEM_LENGTH) return false
        if (textToken.length <= termToken.length) return false
        if (!textToken.startsWith(termToken)) return false
        if (termToken == "sex" && textToken.startsWith("sext")) return false
        return true
    }

    private fun containsLongDigitRun(value: String): Boolean = DIGIT_RUN.containsMatchIn(value)

    private fun parseClass(rawClass: String): FreeChatSafetyClass? =
        when (rawClass.trim().uppercase()) {
            "S1" -> FreeChatSafetyClass.S1
            "S2" -> FreeChatSafetyClass.S2
            "S3" -> FreeChatSafetyClass.S3
            "PII" -> FreeChatSafetyClass.PII
            else -> null
        }

    companion object {
        private const val MIN_STEM_LENGTH = 3

        /** Seven or more consecutive digits is a phone/CPF-length identifier, not chatter. */
        private val DIGIT_RUN = Regex("\\d{7,}")

        private val CLASS_PRIORITY = listOf(
            FreeChatSafetyClass.S3,
            FreeChatSafetyClass.S2,
            FreeChatSafetyClass.PII,
            FreeChatSafetyClass.S1,
        )
    }
}
