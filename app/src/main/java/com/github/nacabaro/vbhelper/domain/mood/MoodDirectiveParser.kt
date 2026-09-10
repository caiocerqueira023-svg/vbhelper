package com.github.nacabaro.vbhelper.domain.mood

/**
 * Parses a hidden "[[MOOD:+N]]" / "[[MOOD:-N]]" directive that the LLM is instructed
 * to append to its reply (see PromptLocalization.moodDirectiveInstruction). The tag is
 * always stripped from the returned text, whether or not a mood delta was found, so it
 * never leaks to the Tamer.
 */
object MoodDirectiveParser {
    private val TAG_REGEX = Regex("""\[\[\s*MOOD\s*:\s*([+-]?)\s*(\d{1,3})\s*\]\]""", RegexOption.IGNORE_CASE)
    private const val MAX_DELTA = 10

    /**
     * @return the reply with the directive removed, and the parsed delta (clamped to
     * +/-MAX_DELTA) or null if the model didn't include a valid directive.
     */
    fun extract(rawReply: String): Pair<String, Int?> {
        val match = TAG_REGEX.find(rawReply)
            ?: return rawReply.trim() to null

        val sign = match.groupValues[1]
        val magnitude = match.groupValues[2].toIntOrNull()
        val cleaned = rawReply.replace(TAG_REGEX, "").trim()

        if (magnitude == null) return cleaned to null

        val delta = (if (sign == "-") -magnitude else magnitude).coerceIn(-MAX_DELTA, MAX_DELTA)
        return cleaned to delta
    }
}
