package com.tamerslab.digiline.freechat.content

import com.tamerslab.digiline.freechat.model.FreeChatContent
import com.tamerslab.digiline.freechat.model.FreeChatChipDto
import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import com.tamerslab.digiline.freechat.model.FreeChatReplyDto

/**
 * Pure Free Talk content validator (contract §3).
 *
 * Works on the in-memory [FreeChatContent] only: no Android, no I/O, no network. It returns the
 * hard errors that must block shipping a pack; the debug loader and the JVM content tests call it.
 * Checks: intent ids unique, reply ids unique, intents have replies, reply text non-empty and
 * <=160 chars, fallback levels 1..4 present, safety class keys known, lore refs resolving to a
 * generated species key, chip/fallback intent references resolving, and `contentVersion > 0`.
 *
 * The validator is intentionally lenient about content volume (workstream A's Python validator
 * owns the volume targets): a partial pack should still be usable in development.
 */
class FreeChatContentValidator {

    fun validate(content: FreeChatContent): List<String> {
        val errors = mutableListOf<String>()
        val pack = content.pack

        if (content.contentVersion <= 0) {
            errors += "contentVersion ${content.contentVersion} must be > 0"
        }
        if (content.locale.isBlank()) {
            errors += "locale is blank"
        }
        if (content.manifest.schemaVersion !in 1..SUPPORTED_SCHEMA_VERSION) {
            errors += "manifest.schemaVersion ${content.manifest.schemaVersion} is unsupported " +
                "(expected 1..$SUPPORTED_SCHEMA_VERSION)"
        }
        if (pack.schemaVersion !in 1..SUPPORTED_SCHEMA_VERSION) {
            errors += "pack.schemaVersion ${pack.schemaVersion} is unsupported " +
                "(expected 1..$SUPPORTED_SCHEMA_VERSION)"
        }

        val intentIds = LinkedHashSet<String>()
        val replyIds = LinkedHashSet<String>()
        val fallbackLevels = LinkedHashSet<Int>()
        pack.fallbacks.forEach { fallback ->
            if (!fallbackLevels.add(fallback.level)) {
                errors += "fallback level ${fallback.level} is duplicated"
            }
            if (fallback.level !in FALLBACK_LEVELS) {
                errors += "fallback level ${fallback.level} is outside 1..4"
            }
        }
        FALLBACK_LEVELS.forEach { level ->
            if (level !in fallbackLevels) {
                errors += "fallbacks are missing level $level"
            }
        }

        pack.intents.forEachIndexed { index, intent ->
            val path = "intent '${intent.id.ifBlank { "#$index" }}'"
            if (intent.id.isBlank()) {
                errors += "intent #$index has a blank id"
            } else if (!intentIds.add(intent.id)) {
                errors += "intent id '${intent.id}' is duplicated"
            }
            if (intent.topic.isBlank()) errors += "$path has a blank topic"
            if (intent.replies.isEmpty()) {
                errors += "$path has no replies"
            }
            validateReplies(path, intent.replies, replyIds, content, errors)
        }

        pack.fallbacks.forEach { fallback ->
            validateReplies(
                owner = "fallback level ${fallback.level}",
                replies = fallback.replies,
                replyIds = replyIds,
                content = content,
                errors = errors,
            )
        }

        // `fallbackId` is resolved leniently, exactly like `FreeChatEngine.resolveFallbackReplies`:
        // `fallback.<level>` (or the bare level) is the authored form, but a direct fallback reply id
        // or another intent id is also a valid reference.
        pack.intents.forEachIndexed { index, intent ->
            val fallbackId = intent.fallbackId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val path = "intent '${intent.id.ifBlank { "#$index" }}'"
            val level = fallbackLevelOf(fallbackId)
            val resolves = (level != null && level in fallbackLevels) ||
                fallbackId in replyIds ||
                fallbackId in intentIds
            if (!resolves) {
                errors += "$path fallbackId '$fallbackId' does not resolve to a fallback level"
            }
        }

        // Chips may legitimately be listed both on their intent and in the global bank (contract §1
        // sample repeats `chip.what_are_you`), so only blank fields and dangling intent refs are
        // hard errors here.
        val chips = pack.intents.flatMap { it.chips } + pack.chips
        chips.forEachIndexed { index, chip ->
            validateChip(chip, index, intentIds, errors)
        }

        if (pack.safety.isEmpty()) {
            errors += "safety terms are empty"
        }
        pack.safety.keys.forEach { key ->
            if (key !in SAFETY_KEYS) {
                errors += "safety class key '$key' is not one of ${SAFETY_KEYS.sorted().joinToString()}"
            }
        }
        validateDatabaseFallbacks(content, errors)
        return errors
    }

    /**
     * Optional licensed database fallback layer (owner requirement). Its lines are dataset text, not
     * authored reply lines, so it has its own limits: structure + provenance must be valid, while
     * the volume/safety review lives in the Python content gate.
     */
    private fun validateDatabaseFallbacks(content: FreeChatContent, errors: MutableList<String>) {
        val seen = HashSet<Pair<String, String>>(content.databaseFallbacks.size)
        content.databaseFallbacks.forEachIndexed { index, pair ->
            val path = "database fallback pair #$index"
            if (pair.user.isBlank()) errors += "$path has blank user text"
            if (pair.response.isBlank()) errors += "$path has blank response"
            if (pair.response.length > MAX_DATABASE_RESPONSE_CHARS) {
                errors += "$path response is ${pair.response.length} chars; max is $MAX_DATABASE_RESPONSE_CHARS"
            }
            if (pair.source.isBlank()) errors += "$path has blank source"
            if (pair.license.isBlank()) errors += "$path has blank license"
            val key = pair.user.trim().lowercase() to pair.response.trim().lowercase()
            if (!seen.add(key)) errors += "$path duplicates an earlier pair"
        }
    }

    private fun validateReplies(
        owner: String,
        replies: List<FreeChatReplyDto>,
        replyIds: MutableSet<String>,
        content: FreeChatContent,
        errors: MutableList<String>,
    ) {
        replies.forEachIndexed { index, reply ->
            val path = "$owner reply '${reply.id.ifBlank { "#$index" }}'"
            if (reply.id.isBlank()) {
                errors += "$owner reply #$index has a blank id"
            } else if (!replyIds.add(reply.id)) {
                errors += "$owner reply id '${reply.id}' is duplicated"
            }
            if (reply.text.isBlank()) {
                errors += "$path has blank text"
            } else if (reply.text.length > MAX_REPLY_CHARS) {
                errors += "$path text is ${reply.text.length} chars; max is $MAX_REPLY_CHARS"
            }
            if (reply.tone != null && reply.tone.isBlank()) {
                errors += "$path has a blank tone"
            }
            if (reply.authoringStatus.isBlank()) {
                errors += "$path has a blank authoringStatus"
            }
            reply.loreRefs.forEach { ref ->
                if (ref.isBlank()) {
                    errors += "$path has a blank loreRef"
                } else if (content.speciesVoices.isNotEmpty() && !isKnownSpeciesKey(content, ref)) {
                    errors += "$path loreRef '$ref' does not resolve to a known species key"
                }
            }
        }
    }

    private fun validateChip(
        chip: FreeChatChipDto,
        index: Int,
        intentIds: Set<String>,
        errors: MutableList<String>,
    ) {
        val path = "chip '${chip.id.ifBlank { "#$index" }}'"
        if (chip.id.isBlank()) {
            errors += "chip #$index has a blank id"
        }
        if (chip.text.isBlank()) errors += "$path has blank text"
        val intentId = chip.intentId
        if (!intentId.isNullOrBlank() && intentId !in intentIds) {
            errors += "$path references unknown intentId '$intentId'"
        }
    }

    /** Fallback references use the authored `fallback.<level>` form (contract §1 sample). */
    private fun fallbackLevelOf(fallbackId: String): Int? {
        val trimmed = fallbackId.trim()
        return trimmed.removePrefix(FALLBACK_PREFIX).toIntOrNull() ?: trimmed.toIntOrNull()
    }

    private fun isKnownSpeciesKey(content: FreeChatContent, ref: String): Boolean {
        val key = ref.trim().removePrefix(LORE_REF_PREFIX)
        if (content.speciesVoices.containsKey(key)) return true
        return content.speciesVoices.keys.any { it.equals(key, ignoreCase = true) }
    }

    companion object {
        /** Highest `schemaVersion` this build understands (contract §1). */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /** Contract §1: authored reply lines are 1-2 sentences and never longer than 160 chars. */
        const val MAX_REPLY_CHARS: Int = 160

        /** Database fallback lines are raw dataset replies, allowed to be a little longer. */
        const val MAX_DATABASE_RESPONSE_CHARS: Int = 200

        private val FALLBACK_LEVELS = 1..4
        private const val FALLBACK_PREFIX = "fallback."
        private const val LORE_REF_PREFIX = "species_catalog:"
        private val SAFETY_KEYS = setOf("S1", "S2", "S3", "PII")
    }
}
