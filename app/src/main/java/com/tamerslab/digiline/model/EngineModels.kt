package com.tamerslab.digiline.model

import kotlin.random.Random

/**
 * Deterministic seed helpers shared by every DigiLine engine.
 *
 * The rules are deliberately simple and JVM-independent: FNV-1a over length-prefixed UTF-8
 * bytes, finalized with the SplitMix64 finalizer. `String.hashCode()` and iteration order are
 * never used, so the same inputs produce the same seed on every device and every run.
 * Persist the seed *before* showing content (anti-reroll requirement, plan F10).
 */
object DigilineSeed {
    val DAY_MILLIS: Long = 86_400_000L

    private val FNV_OFFSET_BASIS: Long = 0xcbf29ce484222325UL.toLong()
    private val FNV_PRIME: Long = 0x100000001b3UL.toLong()
    private val SPLIT_MIX_1: Long = 0xbf58476d1ce4e5b9UL.toLong()
    private val SPLIT_MIX_2: Long = 0x94d049bb133111ebUL.toLong()

    fun random(seed: Long): Random = Random(seed)

    /** Logical day index used by the cadence rules; floors negative timestamps instead of truncating. */
    fun dayIndex(now: Long, dayMillis: Long = DAY_MILLIS): Long {
        if (dayMillis <= 0L) return 0L
        val quotient = now / dayMillis
        return if (now < 0L && now % dayMillis != 0L) quotient - 1L else quotient
    }

    /** FNV-1a hash of a UTF-8 string, finalized. Stable across JVM/Android versions. */
    fun stableHash(value: String): Long {
        var hash = FNV_OFFSET_BASIS
        val bytes = value.toByteArray(Charsets.UTF_8)
        hash = foldLong(hash, bytes.size.toLong())
        for (byte in bytes) hash = foldByte(hash, byte.toInt())
        return finish(hash)
    }

    /** Mixes an existing seed with additional 64-bit values. */
    fun combine(seed: Long, vararg parts: Long): Long {
        var hash = foldLong(seed, parts.size.toLong())
        for (part in parts) hash = foldLong(hash, part)
        return finish(hash)
    }

    /** Mixes an existing seed with text. */
    fun combineText(seed: Long, value: String): Long {
        var hash = seed
        val bytes = value.toByteArray(Charsets.UTF_8)
        hash = foldLong(hash, bytes.size.toLong())
        for (byte in bytes) hash = foldByte(hash, byte.toInt())
        return finish(hash)
    }

    /** Seed for one owned-Digimon scheduling decision: `hash(individualId, dayIndex, session, pack)`. */
    fun sessionSeed(individualId: String, dayIndex: Long, sessionCounter: Long, contentVersion: Int): Long {
        var hash = FNV_OFFSET_BASIS
        val bytes = individualId.toByteArray(Charsets.UTF_8)
        hash = foldLong(hash, bytes.size.toLong())
        for (byte in bytes) hash = foldByte(hash, byte.toInt())
        hash = foldLong(hash, dayIndex)
        hash = foldLong(hash, sessionCounter)
        hash = foldLong(hash, contentVersion.toLong())
        return finish(hash)
    }

    /** Named sub-seed (e.g. `"quiz"`, `"negotiation.demand"`) so streams never share a sequence. */
    fun selectionSeed(seed: Long, purpose: String, index: Int = 0): Long =
        combineText(combine(seed, index.toLong()), purpose)

    private fun foldByte(hash: Long, value: Int): Long = (hash xor (value and 0xff).toLong()) * FNV_PRIME

    private fun foldLong(hash: Long, value: Long): Long {
        var result = hash
        for (shift in 0..56 step 8) result = foldByte(result, (value ushr shift).toInt())
        return result
    }

    private fun finish(value: Long): Long {
        var z = value
        z = (z xor (z ushr 30)) * SPLIT_MIX_1
        z = (z xor (z ushr 27)) * SPLIT_MIX_2
        return z xor (z ushr 31)
    }
}

/** Which owned-Digimon question pool a card came from. */
enum class QuestionKind { SPECIES, GENERIC }

/**
 * Result of [com.tamerslab.digiline.engine.PersonalityShiftEngine.apply].
 *
 * [points] is normalized: only types `1..16` with a positive value after decay/caps, in ascending
 * type order. [answeredSinceShift] is the *next* counter value (0 after a shift). [lastShiftAt] is
 * the timestamp the cooldown is measured from (unchanged when no shift happened).
 */
data class ShiftOutcome(
    val points: Map<Int, Int>,
    val previousType: Int,
    val newType: Int,
    val shifted: Boolean,
    val lastShiftAt: Long?,
    val answeredSinceShift: Int,
    val decayApplied: Boolean = false,
    val leadingType: Int? = null,
    val leadingPoints: Int = 0,
) {
    val typeChanged: Boolean get() = shifted && previousType != newType

    fun pointsOf(type: Int): Int = points[type] ?: 0
}

/** Contract §3.1 name for the shift result; kept as an alias of [ShiftOutcome]. */
typealias ShiftResult = ShiftOutcome

/** A selected question card with a persisted, deterministic option order. */
data class QuestionSelection(
    val card: QuestionCardDto,
    val optionOrder: List<String>,
    val seed: Long,
) {
    /** Options in display order; unknown ids are ignored and missing options are appended. */
    val orderedOptions: List<AnswerOptionDto>
        get() {
            if (optionOrder.isEmpty()) return card.options
            val byId = card.options.associateBy { it.id }
            val ordered = optionOrder.mapNotNull { byId[it] }
            if (ordered.isEmpty()) return card.options
            val seen = ordered.mapTo(mutableSetOf()) { it.id }
            return ordered + card.options.filter { it.id !in seen }
        }

    fun option(id: String): AnswerOptionDto? = card.options.firstOrNull { it.id == id }
}

/** Result of [com.tamerslab.digiline.engine.QuizEngine.resolve]. Bond (`mood`) delta only; the repository floors at 0. */
data class QuizOutcome(
    val quizId: String,
    val correct: Boolean,
    val moodDelta: Int,
    val chosenIndex: Int,
    val correctIndex: Int,
    val explanation: String = "",
) {
    companion object {
        const val CORRECT_MOOD_DELTA = 3
        const val WRONG_MOOD_DELTA = -5
    }
}

/** Terminal negotiation results; strings match the authored `outcome` values in `negotiation.json`. */
enum class NegotiationOutcome {
    JOIN,
    REFUSE,
    LEAVE,
    FIGHT,
    PAY_AND_LEAVE,
    DEPART;

    companion object {
        fun fromWire(value: String?): NegotiationOutcome? {
            val normalized = value?.trim()?.uppercase() ?: return null
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/** Coarse negotiation phase shown in the scene header (plan §7.1); derived from the node tag. */
enum class NegotiationPhase(val wireTag: String) {
    APPROACH("greeting"),
    PROBE("probe"),
    BARGAIN("demand"),
    TEST("trick"),
    RESOLVE("resolve");

    companion object {
        fun fromTag(tag: String?): NegotiationPhase {
            val normalized = tag?.trim()?.lowercase().orEmpty()
            return when (normalized) {
                "greeting", "approach", "" -> APPROACH
                "probe" -> PROBE
                "demand", "bargain" -> BARGAIN
                "trick", "insight", "test" -> TEST
                "resolve", "join", "refuse", "depart", "outcome" -> RESOLVE
                else -> APPROACH
            }
        }
    }
}

/** One accepted move of a negotiation, used for the persisted audit log. */
data class NegotiationMove(
    val nodeId: String,
    val optionId: String,
    val bitsDelta: Long = 0,
    val itemId: String? = null,
    val itemQuantity: Int = 0,
    val persuasionFlag: String? = null,
    val outcome: NegotiationOutcome? = null,
)

/**
 * Immutable wild-negotiation state. The effects/ledger are already applied in this state (the
 * engine performs no I/O); the repository persists [nodeId]/[seed]/[departUsed] and transacts
 * [NegotiationStep.bitsDelta]/[NegotiationStep.itemDelta] afterwards.
 */
data class NegotiationState(
    val flow: NegotiationFlowDto,
    val nodeId: String,
    val tier: Int = 2,
    val seed: Long = 0,
    val personalityType: Int = 0,
    val bitsPaid: Long = 0,
    val itemsPaid: Map<String, Int> = emptyMap(),
    val flags: Set<String> = emptySet(),
    val departUsed: Boolean = false,
    val firstConversation: Boolean = false,
    val finished: Boolean = false,
    val outcome: NegotiationOutcome? = null,
    val history: List<NegotiationMove> = emptyList(),
) {
    val node: NegotiationNodeDto? get() = flow.nodes[nodeId]

    val itemsPaidTotal: Int get() = itemsPaid.values.sum()

    val phase: NegotiationPhase get() = NegotiationPhase.fromTag(node?.tag)

    fun flag(key: String): Boolean = flags.contains(key)
}

/** Result of one [com.tamerslab.digiline.engine.NegotiationEngine.choose] call. */
data class NegotiationStep(
    val state: NegotiationState,
    val accepted: Boolean,
    val previousNode: NegotiationNodeDto? = null,
    val node: NegotiationNodeDto? = null,
    val option: NegotiationOptionDto? = null,
    val effects: NegotiationEffectsDto? = null,
    val bitsDelta: Long = 0,
    val itemDelta: Map<String, Int> = emptyMap(),
    val outcome: NegotiationOutcome? = null,
    val departBlocked: Boolean = false,
    val message: String? = null,
) {
    val terminal: Boolean get() = outcome != null || state.finished
}

/** Terminal persuasion results; strings match the authored `outcome` values in `persuasion.json`. */
enum class PersuasionOutcome {
    NEXT_STAGE,
    QUEST_OFFER,
    REFUSE,
    DEPART;

    companion object {
        fun fromWire(value: String?): PersuasionOutcome? {
            val normalized = value?.trim()?.uppercase() ?: return null
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { it.name == normalized }
        }

        fun isTerminal(outcome: PersuasionOutcome?): Boolean = outcome != null
    }
}

/** Immutable post-join persuasion state (`WildPersuasionEntity` mirrors [stage]/[flags]/[outcome]). */
data class PersuasionState(
    val flow: NegotiationFlowDto,
    val stage: Int,
    val nodeId: String,
    val seed: Long = 0,
    val personalityType: Int = 0,
    val flags: Set<String> = emptySet(),
    val visitedNodeIds: List<String> = emptyList(),
    val finished: Boolean = false,
    val outcome: PersuasionOutcome? = null,
) {
    val node: NegotiationNodeDto? get() = flow.nodes[nodeId]

    fun flag(key: String): Boolean = flags.contains(key)
}

/** Result of one [com.tamerslab.digiline.engine.PersuasionEngine.choose] call. */
data class PersuasionStep(
    val state: PersuasionState,
    val accepted: Boolean,
    val previousNode: NegotiationNodeDto? = null,
    val node: NegotiationNodeDto? = null,
    val option: NegotiationOptionDto? = null,
    val outcome: PersuasionOutcome? = null,
    val nextStage: Int? = null,
    val questOffer: Boolean = false,
    val message: String? = null,
) {
    val terminal: Boolean get() = outcome != null || state.finished
}

/** A deterministic event-line pick. */
data class ReactionPick(val line: EventLineDto) {
    val key: String get() = line.id
    val text: String get() = line.text
    val moodDelta: Int get() = line.moodDelta
}

/** A deterministic, LLM-free diary entry assembled from authored lines and lore facts. */
data class DiaryEntry(
    val title: String,
    val body: String,
    val event: String,
    val personalityType: Int,
    val lineId: String? = null,
    val facts: List<String> = emptyList(),
    val moodDelta: Int = 0,
)

/** Inputs for one owned-Digimon scheduling decision (see `OwnedDigilineEngine.plan`). */
data class OwnedDigilineInput(
    val individualId: String,
    val personalityType: Int = 1,
    val stageTier: Int = 2,
    val speciesKey: String? = null,
    val speciesVariant: Int = 0,
    val contentVersion: Int = 0,
    val dayIndex: Long = 0,
    val sessionCounter: Long = 0,
    val recentQuestionIds: Set<String> = emptySet(),
    val recentQuizIds: Set<String> = emptySet(),
    val allowSpeciesQuestion: Boolean = true,
    val allowGenericQuestion: Boolean = true,
    val allowQuiz: Boolean = true,
    val quizDue: Boolean = true,
) {
    val normalizedType: Int get() = personalityType.coerceIn(1, 16)

    val normalizedTier: Int get() = stageTier.coerceIn(1, 4)

    /** Deterministic seed for this decision; persist it before showing the card. */
    fun seed(): Long = DigilineSeed.sessionSeed(
        individualId = individualId,
        dayIndex = dayIndex,
        sessionCounter = sessionCounter,
        contentVersion = contentVersion,
    )
}

/** Pure owned-Digimon scene state machine (Idle -> Question/Quiz -> Answered/Reaction -> Idle). */
sealed interface OwnedDigilineState {
    data object Idle : OwnedDigilineState

    data class Question(
        val selection: QuestionSelection,
        val askedAt: Long,
        val kind: QuestionKind,
    ) : OwnedDigilineState

    data class Quiz(
        val quiz: QuizCardDto,
        val askedAt: Long,
    ) : OwnedDigilineState

    /** A question was answered; [shift] always carries the post-answer tendency snapshot. */
    data class Answered(
        val questionId: String,
        val optionId: String,
        val reaction: String,
        val moodDelta: Int,
        val shift: ShiftOutcome,
        val finishedAt: Long,
    ) : OwnedDigilineState

    data class QuizAnswered(
        val quizId: String,
        val outcome: QuizOutcome,
        val finishedAt: Long,
    ) : OwnedDigilineState

    data class Reaction(
        val event: String,
        val text: String,
        val moodDelta: Int = 0,
    ) : OwnedDigilineState
}

/** Result of [com.tamerslab.digiline.engine.OwnedDigilineEngine.answerQuestion]. */
data class OwnedDigilineAnswer(
    val accepted: Boolean,
    val option: AnswerOptionDto?,
    val reaction: String,
    val moodDelta: Int,
    val shift: ShiftOutcome?,
    val nextState: OwnedDigilineState,
    val message: String? = null,
)

/** Result of [com.tamerslab.digiline.engine.OwnedDigilineEngine.answerQuiz]. */
data class OwnedDigilineQuizAnswer(
    val accepted: Boolean,
    val outcome: QuizOutcome?,
    val nextState: OwnedDigilineState,
    val message: String? = null,
)
