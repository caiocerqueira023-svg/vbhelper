package com.tamerslab.digiline.content

import com.tamerslab.digiline.model.ContentManifest
import com.tamerslab.digiline.model.EventLineDto
import com.tamerslab.digiline.model.LoreFactDto
import com.tamerslab.digiline.model.NegotiationFlowDto
import com.tamerslab.digiline.model.NegotiationNodeDto
import com.tamerslab.digiline.model.NegotiationOptionDto
import com.tamerslab.digiline.model.QuestionCardDto
import com.tamerslab.digiline.model.QuizCardDto
import java.util.Locale

/** Outcome of validating one content pack: hard [errors] block shipping, [warnings] are advisory. */
data class DigilineValidationReport(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()

    fun isValid(strict: Boolean): Boolean = errors.isEmpty() && (!strict || warnings.isEmpty())

    fun summary(): String = buildString {
        append(if (isValid) "DigiLine content is valid" else "DigiLine content has ${errors.size} error(s)")
        if (warnings.isNotEmpty()) append(" and ${warnings.size} warning(s)")
    }
}

/**
 * Pure DigiLine content validator (works on the in-memory pack, no Android, no I/O).
 *
 * Hard errors mirror `DIGILINE_IMPLEMENTATION_CONTRACT.md` §1/§3: unique ids, option counts,
 * personality shift keys 1..16, valid quiz answer keys, no dangling negotiation/persuasion
 * `next` references, reachable terminal outcomes, and full personality/tier coverage.
 * Warnings flag contract targets that a partial pack may still ship with (quiz volume, authored
 * option counts, lore citations, plan-level generic volume, unreachable bonus nodes).
 */
class DigilineContentValidator {

    /** Full report with errors and warnings separated; never throws on malformed-ish packs. */
    fun report(pack: DigilineContentPack): DigilineValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        validateManifest(pack.manifest, errors, warnings)
        validateQuestionIds(pack.questions, pack.genericQuestions, errors, warnings)
        validateQuestionCards(pack.questions, LABEL_SPECIES, isGeneric = false, errors, warnings)
        validateQuestionCards(pack.genericQuestions, LABEL_GENERIC, isGeneric = true, errors, warnings)
        validateGenericCoverage(pack.genericQuestions, errors, warnings)
        validateQuizzes(pack.quizzes, pack.lore, errors, warnings)
        validateFlows(
            personalities = pack.negotiation.personalities,
            label = LABEL_NEGOTIATION,
            slotWord = "tier",
            requiredSlots = TIER_SLOTS,
            outcomes = NEGOTIATION_OUTCOMES,
            tags = NEGOTIATION_TAGS,
            minChoiceNodes = MIN_NEGOTIATION_CHOICE_NODES,
            requireAuthoredDialogue = true,
            errors = errors,
            warnings = warnings,
        )
        validateFlows(
            personalities = pack.persuasion.personalities,
            label = LABEL_PERSUASION,
            slotWord = "stage",
            requiredSlots = STAGE_SLOTS,
            outcomes = PERSUASION_OUTCOMES,
            tags = null,
            minChoiceNodes = MIN_PERSUASION_CHOICE_NODES,
            requireAuthoredDialogue = false,
            errors = errors,
            warnings = warnings,
        )
        validateEventLines(pack.eventLines, errors, warnings)
        validateLore(pack.lore, errors, warnings)
        return DigilineValidationReport(errors.toList(), warnings.toList())
    }

    /**
     * Flat issue list, one line per issue, prefixed `ERROR:` / `WARNING:`.
     * In [strict] mode warnings are reported as errors so CI can gate on a clean pack.
     */
    fun validate(pack: DigilineContentPack, strict: Boolean = false): List<String> {
        val report = report(pack)
        return buildList {
            report.errors.forEach { add("ERROR: $it") }
            report.warnings.forEach { warning ->
                add(if (strict) "ERROR (strict warning): $warning" else "WARNING: $warning")
            }
        }
    }

    /** Throws [IllegalStateException] when the pack cannot be used; [strict] also fails on warnings. */
    fun requireValid(pack: DigilineContentPack, strict: Boolean = false) {
        val report = report(pack)
        val failures = if (strict) report.errors + report.warnings else report.errors
        if (failures.isNotEmpty()) {
            val preview = failures.take(MAX_REPORTED_FAILURES).joinToString("; ")
            throw IllegalStateException(
                "DigiLine content validation failed: ${failures.size} issue(s): $preview"
            )
        }
    }

    // ------------------------------------------------------------------ manifest

    private fun validateManifest(
        manifest: ContentManifest,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (manifest.schemaVersion != DigilineContentLoader.SUPPORTED_SCHEMA_VERSION) {
            errors += "manifest.schemaVersion ${manifest.schemaVersion} is unsupported " +
                "(expected ${DigilineContentLoader.SUPPORTED_SCHEMA_VERSION})"
        }
        if (manifest.contentVersion < 1) {
            warnings += "manifest.contentVersion ${manifest.contentVersion} should be >= 1"
        }
        if (manifest.locale.isBlank()) {
            errors += "manifest.locale is blank"
        }
        val missing = DigilineContentFiles.REQUIRED_KEYS.filterNot { manifest.files.containsKey(it) }
        if (missing.isNotEmpty()) {
            errors += "manifest.json is missing required file keys: ${missing.joinToString()}"
        }
        manifest.files.forEach { (key, file) ->
            if (file.isBlank()) errors += "manifest.files['$key'] is blank"
        }
    }

    // ----------------------------------------------------------------- questions

    private fun validateQuestionIds(
        speciesQuestions: List<QuestionCardDto>,
        genericQuestions: List<QuestionCardDto>,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val counts = LinkedHashMap<String, Int>()
        (speciesQuestions + genericQuestions).forEach { card ->
            if (card.id.isNotBlank()) counts[card.id] = (counts[card.id] ?: 0) + 1
        }
        counts.forEach { (id, count) ->
            if (count > 1) {
                errors += "question id '$id' is duplicated ($count occurrences across species+generic)"
            }
        }
    }

    private fun validateQuestionCards(
        cards: List<QuestionCardDto>,
        label: String,
        isGeneric: Boolean,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        cards.forEachIndexed { index, card ->
            val path = "$label question '${card.id.ifBlank { "#$index" }}'"
            if (card.id.isBlank()) errors += "$label question #$index has a blank id"
            if (card.prompt.isBlank()) errors += "$path has a blank prompt"
            if (card.stageTier !in STAGE_TIER_RANGE) {
                errors += "$path stageTier ${card.stageTier} is outside 1..4"
            }
            if (card.options.size !in QUESTION_OPTIONS) {
                errors += "$path has ${card.options.size} option(s); expected $QUESTION_OPTIONS"
            } else if (card.options.size !in AUTHORED_OPTION_TARGET) {
                warnings += "$path has ${card.options.size} options; content target is $AUTHORED_OPTION_TARGET"
            }
            val optionIds = mutableSetOf<String>()
            card.options.forEachIndexed { optionIndex, option ->
                val optionPath = "$path option '${option.id.ifBlank { "#$optionIndex" }}'"
                if (option.id.isBlank()) {
                    errors += "$path option #$optionIndex has a blank id"
                } else if (!optionIds.add(option.id)) {
                    errors += "$path has a duplicate option id '${option.id}'"
                }
                if (option.text.isBlank()) errors += "$optionPath has blank text"
                if (option.moodDelta !in QUESTION_MOOD_RANGE) {
                    warnings += "$optionPath moodDelta ${option.moodDelta} is outside the authored " +
                        "range ${QUESTION_MOOD_RANGE.first}..${QUESTION_MOOD_RANGE.last}"
                }
                if (option.shift.isEmpty()) warnings += "$optionPath has no personality shift"
                option.shift.forEach { (key, points) ->
                    val type = key.toIntOrNull()
                    if (type == null || type !in PERSONALITY_TYPES) {
                        errors += "$optionPath shift key '$key' is not a personality type 1..16"
                    }
                    if (points == 0) warnings += "$optionPath shift for type '$key' is 0"
                }
                if (option.tone?.isBlank() == true) {
                    warnings += "$optionPath has a blank tone"
                }
            }
            if (isGeneric) {
                if (card.askerTypes.isEmpty()) errors += "$path has no askerTypes"
                card.askerTypes.forEach { type ->
                    if (type !in PERSONALITY_TYPES) {
                        errors += "$path askerTypes contains $type outside 1..16"
                    }
                }
                if (card.askerTypes.size > GENERIC_ASKER_TARGET) {
                    warnings += "$path lists ${card.askerTypes.size} askerTypes; contract target is " +
                        "$GENERIC_ASKER_TARGET"
                }
                val core = card.core
                if (core != null && core !in CORES) {
                    errors += "$path core '$core' is not one of ${CORES.joinToString()}"
                }
            } else {
                val speciesKey = card.speciesKey
                if (speciesKey.isNullOrBlank()) {
                    errors += "$path has no speciesKey"
                } else {
                    if (speciesKey != normalizeSpeciesKey(speciesKey)) {
                        warnings += "$path speciesKey '$speciesKey' is not normalized " +
                            "(lowercase letters/digits only)"
                    }
                    val speciesName = card.speciesName
                    if (speciesName.isNullOrBlank()) {
                        warnings += "$path has no speciesName"
                    } else if (speciesKey != normalizeSpeciesKey(speciesName)) {
                        warnings += "$path speciesKey '$speciesKey' does not match speciesName " +
                            "'$speciesName'"
                    }
                }
            }
        }
    }

    private fun validateGenericCoverage(
        genericQuestions: List<QuestionCardDto>,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val counts = IntArray(maxOf(PERSONALITY_TYPES.last, 0) + 1)
        genericQuestions.forEach { question ->
            question.askerTypes.filter { it in PERSONALITY_TYPES }.forEach { counts[it]++ }
        }
        PERSONALITY_TYPES.forEach { type ->
            val count = counts[type]
            if (count < MIN_GENERIC_PER_TYPE) {
                errors += "generic coverage: personality type $type has $count question(s); " +
                    "contract requires at least $MIN_GENERIC_PER_TYPE"
            } else if (count < TARGET_GENERIC_PER_TYPE) {
                warnings += "generic coverage: personality type $type has $count question(s); " +
                    "plan target is $TARGET_GENERIC_PER_TYPE"
            }
        }
    }

    // ---------------------------------------------------------------------- quiz

    private fun validateQuizzes(
        quizzes: List<QuizCardDto>,
        lore: List<LoreFactDto>,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (quizzes.size < MIN_QUIZ_TARGET) {
            warnings += "quiz bank has ${quizzes.size} question(s); contract target is at least " +
                "$MIN_QUIZ_TARGET"
        }
        val loreKeys = lore.map { it.speciesKey }.filter { it.isNotBlank() }.toSet()
        val ids = mutableSetOf<String>()
        val prompts = mutableMapOf<String, Int>()
        quizzes.forEachIndexed { index, quiz ->
            val path = "quiz '${quiz.id.ifBlank { "#$index" }}'"
            if (quiz.id.isBlank()) {
                errors += "quiz question #$index has a blank id"
            } else if (!ids.add(quiz.id)) {
                errors += "quiz id '${quiz.id}' is duplicated"
            }
            if (quiz.prompt.isBlank()) {
                errors += "$path has a blank prompt"
            } else {
                val normalized = quiz.prompt.trim().lowercase(Locale.ROOT)
                prompts[normalized] = (prompts[normalized] ?: 0) + 1
            }
            if (quiz.stageTier !in STAGE_TIER_RANGE) {
                errors += "$path stageTier ${quiz.stageTier} is outside 1..4"
            }
            if (quiz.options.size !in QUESTION_OPTIONS) {
                errors += "$path has ${quiz.options.size} option(s); expected ${QUIZ_OPTIONS.first}.." +
                    "${QUIZ_OPTIONS.last}"
            } else if (quiz.options.size !in QUIZ_OPTIONS) {
                warnings += "$path has ${quiz.options.size} options; contract specifies 3 or 4"
            }
            if (quiz.options.any { it.isBlank() }) errors += "$path has a blank option"
            if (quiz.options.isNotEmpty() && quiz.correctIndex !in quiz.options.indices) {
                errors += "$path correctIndex ${quiz.correctIndex} is outside 0..${quiz.options.lastIndex}"
            }
            if (quiz.explanation.isBlank()) errors += "$path has a blank explanation"
            if (quiz.loreRefs.isEmpty()) {
                warnings += "$path has no loreRefs"
            } else {
                quiz.loreRefs.forEach { ref ->
                    if (!ref.startsWith(LORE_REF_PREFIX)) {
                        warnings += "$path loreRef '$ref' should use the " +
                            "'$LORE_REF_PREFIX<speciesKey>' form"
                    } else if (loreKeys.isNotEmpty()) {
                        val key = ref.removePrefix(LORE_REF_PREFIX)
                        if (key !in loreKeys) {
                            warnings += "$path loreRef '$ref' has no matching lore fact"
                        }
                    }
                }
            }
        }
        prompts.forEach { (prompt, count) ->
            if (count > 1) {
                warnings += "quiz prompt appears $count times: '${prompt.take(MAX_PROMPT_PREVIEW)}'"
            }
        }
    }

    // ------------------------------------------------------ negotiation/persuasion

    private fun validateFlows(
        personalities: Map<String, Map<String, NegotiationFlowDto>>,
        label: String,
        slotWord: String,
        requiredSlots: IntRange,
        outcomes: Set<String>,
        tags: Set<String>?,
        minChoiceNodes: Int?,
        requireAuthoredDialogue: Boolean,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        PERSONALITY_TYPES.forEach { type ->
            if (!personalities.containsKey(type.toString())) {
                errors += "$label is missing personality type $type"
            }
        }
        personalities.forEach { (typeKey, flows) ->
            val typeNumber = typeKey.toIntOrNull()
            if (typeNumber == null || typeNumber !in PERSONALITY_TYPES) {
                errors += "$label personality key '$typeKey' is not a personality type 1..16"
            }
            if (flows.isEmpty()) {
                errors += "$label type '$typeKey' has no flows"
                return@forEach
            }
            requiredSlots.forEach { slot ->
                val flow = flows[slot.toString()]
                if (flow == null) {
                    errors += "$label type '$typeKey' is missing required $slotWord $slot"
                } else {
                    validateFlow(
                        flow = flow,
                        path = "$label type '$typeKey' $slotWord $slot",
                        outcomes = outcomes,
                        tags = tags,
                        minChoiceNodes = minChoiceNodes,
                        requireAuthoredDialogue = requireAuthoredDialogue,
                        errors = errors,
                        warnings = warnings,
                    )
                }
            }
            flows.keys.forEach { slotKey ->
                val slot = slotKey.toIntOrNull()
                if (slot == null || slot !in requiredSlots) {
                    warnings += "$label type '$typeKey' has unexpected flow key '$slotKey' " +
                        "(expected ${requiredSlots.first}..${requiredSlots.last})"
                }
            }
        }
    }

    private fun validateFlow(
        flow: NegotiationFlowDto,
        path: String,
        outcomes: Set<String>,
        tags: Set<String>?,
        minChoiceNodes: Int?,
        requireAuthoredDialogue: Boolean,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val nodes = flow.nodes
        if (nodes.isEmpty()) {
            errors += "$path has no nodes"
            return
        }
        if (flow.start.isBlank()) {
            errors += "$path has a blank start node id"
        } else if (!nodes.containsKey(flow.start)) {
            errors += "$path start node '${flow.start}' does not exist"
        }

        val terminalNodeIds = mutableSetOf<String>()
        val allowedTags = tags
        nodes.forEach { (nodeKey, node) ->
            if (node.id.isNotBlank() && node.id != nodeKey) {
                warnings += "$path node map key '$nodeKey' does not match node.id '${node.id}'"
            }
            if (node.text.isBlank()) errors += "$path node '$nodeKey' has blank text"
            if (node.speaker.isBlank()) warnings += "$path node '$nodeKey' has a blank speaker"
            if (requireAuthoredDialogue && node.topic.isNullOrBlank()) {
                errors += "$path node '$nodeKey' has no authored dialogue topic"
            }
            val tag = node.tag
            if (tag != null) {
                if (tag.isBlank()) {
                    warnings += "$path node '$nodeKey' has a blank tag"
                } else if (allowedTags != null && tag !in allowedTags) {
                    warnings += "$path node '$nodeKey' tag '$tag' is not one of " +
                        allowedTags.joinToString()
                }
            }
            validateDemand(node, path, nodeKey, errors, warnings)

            val outcome = node.outcome
            if (outcome != null) {
                if (outcome !in outcomes) {
                    errors += "$path node '$nodeKey' has outcome '$outcome' outside " +
                        outcomes.sorted().joinToString()
                } else {
                    terminalNodeIds += nodeKey
                }
                if (node.options.isNotEmpty()) {
                    warnings += "$path node '$nodeKey' declares outcome '$outcome' but also has " +
                        "${node.options.size} option(s)"
                }
            }
            if (node.options.isEmpty()) {
                if (outcome == null) {
                    errors += "$path node '$nodeKey' is a dead end: no options and no outcome"
                }
            } else {
                if (node.options.size !in FLOW_OPTIONS) {
                    errors += "$path node '$nodeKey' has ${node.options.size} option(s); " +
                        "expected $FLOW_OPTIONS"
                }
                if (requireAuthoredDialogue && node.options.size == 1) {
                    errors += "$path node '$nodeKey' has a single answer; authored negotiation " +
                        "questions need at least 2 real answers"
                }
                val optionIds = mutableSetOf<String>()
                node.options.forEachIndexed { index, option ->
                    val optionPath = "$path node '$nodeKey' option " +
                        "'${option.id.ifBlank { "#$index" }}'"
                    if (option.id.isBlank()) {
                        errors += "$path node '$nodeKey' option #$index has a blank id"
                    } else if (!optionIds.add(option.id)) {
                        errors += "$path node '$nodeKey' has a duplicate option id '${option.id}'"
                    }
                    if (option.text.isBlank()) errors += "$optionPath has blank text"
                    val next = option.next
                    if (next != null) {
                        if (next.isBlank()) {
                            errors += "$optionPath has a blank next id"
                        } else if (!nodes.containsKey(next)) {
                            errors += "$optionPath points to missing node '$next'"
                        }
                    } else if (outcome == null) {
                        errors += "$optionPath has no next node and its node has no outcome"
                    }
                    val requires = option.requires
                    if (requires != null && requires.isBlank()) {
                        warnings += "$optionPath has a blank requires flag"
                    }
                    if (requireAuthoredDialogue) {
                        if (option.topic.isNullOrBlank()) {
                            errors += "$optionPath has no authored dialogue topic"
                        }
                        val target = next?.let { nodes[it] }
                        val targetTerminal = target?.isTerminal == true
                        if (!targetTerminal && option.reaction.isNullOrBlank()) {
                            errors += "$optionPath has no authored reaction: every answer must be " +
                                "acknowledged by the Digimon before the follow-up"
                        }
                        // The follow-up must continue the same authored thread. A demand node is the
                        // deliberate end of a topic; outcome nodes carry their last topic explicitly.
                        if (!targetTerminal && target != null && target.tag != DEMAND_TAG &&
                            option.topic != target.topic
                        ) {
                            errors += "$optionPath topic '${option.topic}' does not continue the " +
                                "follow-up topic '${target.topic}'"
                        }
                    }
                    validateEffects(option, optionPath, errors, warnings)
                }
            }
        }

        if (flow.start.isNotBlank() && nodes.containsKey(flow.start)) {
            val reachable = collectReachableNodes(flow)
            val unreachable = nodes.keys.filterNot { it in reachable }
            if (unreachable.isNotEmpty()) {
                warnings += "$path has ${unreachable.size} node(s) unreachable from start " +
                    "'${flow.start}': ${unreachable.sorted().joinToString()}"
            }
            if (terminalNodeIds.none { it in reachable }) {
                errors += "$path has no terminal outcome reachable from start '${flow.start}'"
            }
        }
        if (minChoiceNodes != null) {
            val choiceNodes = nodes.values.count { it.options.isNotEmpty() }
            if (choiceNodes < minChoiceNodes) {
                errors += "$path has $choiceNodes authored choice node(s); requires at least " +
                    "$minChoiceNodes"
            }
        }
    }

    private fun validateDemand(
        node: NegotiationNodeDto,
        path: String,
        nodeKey: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val demand = node.demand ?: return
        if (demand.kind !in DEMAND_KINDS) {
            errors += "$path node '$nodeKey' demand kind '${demand.kind}' is not one of " +
                DEMAND_KINDS.joinToString()
        }
        when (demand.kind) {
            DEMAND_BITS -> if (demand.amount < 0) {
                errors += "$path node '$nodeKey' bits demand amount ${demand.amount} must be >= 0"
            }
            DEMAND_ITEM -> {
                if (demand.itemId.isNullOrBlank()) {
                    errors += "$path node '$nodeKey' item demand has no itemId"
                }
                if (demand.amount !in MIN_ITEM_DEMAND..MAX_ITEM_DEMAND) {
                    warnings += "$path node '$nodeKey' item demand amount ${demand.amount} " +
                        "should be $MIN_ITEM_DEMAND..$MAX_ITEM_DEMAND"
                }
            }
        }
    }

    private fun validateEffects(
        option: NegotiationOptionDto,
        optionPath: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val effects = option.effects
        if (effects.itemQuantity < 0) {
            errors += "$optionPath itemQuantity ${effects.itemQuantity} must be >= 0"
        }
        if (effects.itemQuantity > 0 && effects.itemId.isNullOrBlank()) {
            errors += "$optionPath has itemQuantity ${effects.itemQuantity} but no itemId"
        }
        if (!effects.itemId.isNullOrBlank() && effects.itemQuantity <= 0) {
            warnings += "$optionPath has itemId '${effects.itemId}' but itemQuantity " +
                effects.itemQuantity
        }
        if (effects.persuasionFlag?.isBlank() == true) {
            warnings += "$optionPath has a blank persuasionFlag"
        }
    }

    private fun collectReachableNodes(flow: NegotiationFlowDto): Set<String> {
        val nodes = flow.nodes
        if (!nodes.containsKey(flow.start)) return emptySet()
        val reachable = mutableSetOf(flow.start)
        val queue = ArrayDeque<String>()
        queue.addLast(flow.start)
        while (queue.isNotEmpty()) {
            val node = nodes[queue.removeFirst()] ?: continue
            node.options.forEach { option ->
                val next = option.next ?: return@forEach
                if (nodes.containsKey(next) && reachable.add(next)) queue.addLast(next)
            }
        }
        return reachable
    }

    // --------------------------------------------------------------- event lines

    private fun validateEventLines(
        eventLines: List<EventLineDto>,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val ids = mutableSetOf<String>()
        val perEvent = mutableMapOf<String, Int>()
        eventLines.forEachIndexed { index, line ->
            val path = "event line '${line.id.ifBlank { "#$index" }}'"
            if (line.id.isBlank()) {
                errors += "event line #$index has a blank id"
            } else if (!ids.add(line.id)) {
                errors += "event line id '${line.id}' is duplicated"
            }
            if (line.event.isBlank()) {
                errors += "$path has a blank event key"
            } else {
                perEvent[line.event] = (perEvent[line.event] ?: 0) + 1
            }
            if (line.text.isBlank()) errors += "$path has blank text"
            if (line.moodDelta !in EVENT_MOOD_RANGE) {
                warnings += "$path moodDelta ${line.moodDelta} is outside " +
                    "${EVENT_MOOD_RANGE.first}..${EVENT_MOOD_RANGE.last}"
            }
            line.types.forEach { type ->
                if (type !in PERSONALITY_TYPES) {
                    errors += "$path types contains $type outside 1..16"
                }
            }
        }
        REQUIRED_EVENTS.forEach { event ->
            val count = perEvent[event] ?: 0
            if (count < MIN_EVENT_LINES) {
                errors += "event_lines is missing coverage for '$event' " +
                    "($count line(s); requires at least $MIN_EVENT_LINES)"
            }
        }
    }

    // ---------------------------------------------------------------------- lore

    private fun validateLore(
        lore: List<LoreFactDto>,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val keys = mutableSetOf<String>()
        lore.forEachIndexed { index, fact ->
            val path = "lore fact '${fact.speciesKey.ifBlank { "#$index" }}'"
            if (fact.speciesKey.isBlank()) {
                errors += "lore fact #$index has a blank speciesKey"
                return@forEachIndexed
            }
            if (!keys.add(fact.speciesKey)) {
                errors += "lore speciesKey '${fact.speciesKey}' is duplicated"
            }
            if (fact.speciesKey != normalizeSpeciesKey(fact.speciesKey)) {
                warnings += "$path speciesKey is not normalized (lowercase letters/digits only)"
            }
            if (fact.speciesName.isBlank()) warnings += "$path has a blank speciesName"
            if (fact.source.isBlank()) warnings += "$path has a blank source"
        }
    }

    companion object {
        /**
         * Contract §1.1: `speciesKey` is the species name lowercased and stripped to letters/digits.
         * Kept in sync with `com.tamerslab.digiline.model.DigilineContentPack.normalizeSpeciesKey`.
         */
        fun normalizeSpeciesKey(name: String): String =
            name.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        private const val LABEL_SPECIES = "species"
        private const val LABEL_GENERIC = "generic"
        private const val LABEL_NEGOTIATION = "negotiation"
        private const val LABEL_PERSUASION = "persuasion"

        private const val MAX_REPORTED_FAILURES = 20
        private const val MAX_PROMPT_PREVIEW = 80

        private val PERSONALITY_TYPES = 1..16
        private val STAGE_TIER_RANGE = 1..4
        private val TIER_SLOTS = 1..4
        private val STAGE_SLOTS = 0..3

        /** Contract §1.1: species 3–4 authored options; schema range is 2–6. */
        private val QUESTION_OPTIONS = 2..6
        private val AUTHORED_OPTION_TARGET = 3..4
        private val QUESTION_MOOD_RANGE = -1..2

        private const val GENERIC_ASKER_TARGET = 2
        private const val MIN_GENERIC_PER_TYPE = 2
        private const val TARGET_GENERIC_PER_TYPE = 8

        private val QUIZ_OPTIONS = 3..4
        private const val MIN_QUIZ_TARGET = 100
        private const val LORE_REF_PREFIX = "species_catalog:"

        private val FLOW_OPTIONS = 1..6
        private const val MIN_NEGOTIATION_CHOICE_NODES = 2
        private const val MIN_PERSUASION_CHOICE_NODES = 2
        private const val DEMAND_TAG = "demand"

        private val NEGOTIATION_OUTCOMES =
            setOf("JOIN", "REFUSE", "LEAVE", "FIGHT", "PAY_AND_LEAVE", "DEPART")
        private val PERSUASION_OUTCOMES = setOf("NEXT_STAGE", "QUEST_OFFER", "REFUSE", "DEPART")
        private val NEGOTIATION_TAGS = setOf(
            "greeting", "probe", "demand", "trick", "insight", "resolve", "join", "refuse", "depart"
        )
        private val DEMAND_KINDS = setOf("bits", "item")
        private const val DEMAND_BITS = "bits"
        private const val DEMAND_ITEM = "item"
        private const val MIN_ITEM_DEMAND = 1
        private const val MAX_ITEM_DEMAND = 2

        private val REQUIRED_EVENTS = setOf(
            "battle_win", "battle_loss", "injury", "heal", "evolution", "degeneration",
            "milestone", "quest_offer", "quest_complete", "item_found", "diary", "wild_depart",
            "recruit_join",
        )
        private val EVENT_MOOD_RANGE = -2..2
        private const val MIN_EVENT_LINES = 2

        private val CORES = setOf("VALOR", "PHILANTHROPY", "WISDOM", "AMICABILITY")
    }
}
