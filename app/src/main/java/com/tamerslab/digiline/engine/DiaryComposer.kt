package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.DiaryEntry
import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.EventLineDto

/**
 * Deterministic, LLM-free diary assembly (plan §8.2, contract §3.1).
 *
 * The repository supplies the event, the individual's personality type, the lore facts it already
 * owns and the authored event lines; the composer picks one line (personality-aware, seeded) and
 * appends the facts. Same inputs => same entry, no network and no randomness outside the seed.
 */
class DiaryComposer(private val lines: List<EventLineDto> = emptyList()) {
    private val reactions = ReactionLineProvider(lines)

    /**
     * Composes one diary entry.
     *
     * @param event event key (e.g. `battle_win`, `evolution`, `diary`).
     * @param personalityType the individual's personality type number (1..16); used for line
     *   selection only.
     * @param facts already-owned lore facts to include (trimmed, de-duplicated, capped at
     *   [maxFacts]).
     * @param seed persisted deterministic seed.
     * @param dayIndex logical day, folded into the seed so entries vary per day deterministically.
     */
    fun compose(
        event: String,
        personalityType: Int,
        facts: List<String> = emptyList(),
        seed: Long = 0,
        dayIndex: Long = 0,
        maxFacts: Int = 3,
    ): DiaryEntry {
        val normalizedEvent = event.trim().ifEmpty { EVENT_DIARY }
        val type = personalityType.coerceIn(1, 16)
        val decisionSeed = DigilineSeed.combine(seed, dayIndex, DigilineSeed.stableHash(normalizedEvent))
        val pick = reactions.pick(normalizedEvent, type, decisionSeed)
        val normalizedFacts = facts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(maxFacts.coerceAtLeast(0))
        val body = when {
            pick != null && normalizedFacts.isEmpty() -> pick.text
            pick != null -> pick.text + " " + normalizedFacts.joinToString(" ")
            normalizedFacts.isNotEmpty() -> normalizedFacts.joinToString(" ")
            else -> fallbackBody(normalizedEvent)
        }
        return DiaryEntry(
            title = titleFor(normalizedEvent),
            body = body.trim(),
            event = normalizedEvent,
            personalityType = type,
            lineId = pick?.key,
            facts = normalizedFacts,
            moodDelta = pick?.moodDelta ?: 0,
        )
    }

    private fun titleFor(event: String): String = DIARY_TITLES[event] ?: humanize(event)

    private fun fallbackBody(event: String): String = DIARY_FALLBACK_BODIES[event]
        ?: "Today passed quietly. I will keep it close until we speak again."

    private fun humanize(event: String): String =
        event.split('_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { character ->
                    if (character.isLowerCase()) character.uppercaseChar() else character
                }
            }
            .ifEmpty { "Diary" }

    companion object {
        const val EVENT_DIARY = "diary"
    }
}

private val DIARY_TITLES = mapOf(
    "battle_win" to "A Hard-Won Victory",
    "battle_loss" to "The Day We Fell Short",
    "injury" to "A Painful Reminder",
    "heal" to "Mending",
    "evolution" to "A New Shape",
    "degeneration" to "Smaller, for Now",
    "milestone" to "A Milestone",
    "quest_offer" to "A Task Worth Taking",
    "quest_complete" to "We Saw It Through",
    "item_found" to "A Lucky Find",
    "diary" to "A Quiet Entry",
    "wild_depart" to "Someone Left",
    "recruit_join" to "A New Companion",
)

private val DIARY_FALLBACK_BODIES = mapOf(
    "battle_win" to "We won today. I want to remember how that felt.",
    "battle_loss" to "Today did not go our way. Next time will be different.",
    "injury" to "I am hurt, but I am still here with you.",
    "heal" to "The pain has faded. I feel like myself again.",
    "evolution" to "Something changed in me today. I hope you are proud.",
    "degeneration" to "I am smaller now. We will grow again together.",
    "milestone" to "We reached something we worked toward for a long time.",
    "quest_offer" to "A new task is waiting for us.",
    "quest_complete" to "We finished what we set out to do.",
    "item_found" to "We found something useful today.",
    "diary" to "Today passed quietly. I am glad you are here.",
    "wild_depart" to "Someone crossed our path, and then they were gone.",
    "recruit_join" to "Someone new decided to stay with us.",
)
