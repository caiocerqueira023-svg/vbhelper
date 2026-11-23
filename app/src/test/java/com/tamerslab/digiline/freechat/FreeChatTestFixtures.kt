package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.FreeChatEntityMentions
import com.tamerslab.digiline.freechat.model.FreeChatChipDto
import com.tamerslab.digiline.freechat.model.FreeChatConditionDto
import com.tamerslab.digiline.freechat.model.FreeChatContent
import com.tamerslab.digiline.freechat.model.FreeChatContext
import com.tamerslab.digiline.freechat.model.FreeChatDatabasePairDto
import com.tamerslab.digiline.freechat.model.FreeChatFallbackDto
import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import com.tamerslab.digiline.freechat.model.FreeChatManifestDto
import com.tamerslab.digiline.freechat.model.FreeChatPackDto
import com.tamerslab.digiline.freechat.model.FreeChatReplyDto
import com.tamerslab.digiline.freechat.model.SpeciesVoiceDto
import com.tamerslab.digiline.freechat.model.SpeciesVoicePackDto
import com.tamerslab.digiline.freechat.model.FreeChatAudience

/**
 * Shared test fixtures for the Free Talk engines: small, fully authored packs with one intent per
 * behavior under test. Two canonical contents are used for determinism tests: [demoContent] and
 * [emptyContent]; the ladder tests build variants from the same authored pieces.
 */

internal fun reply(
    id: String,
    text: String = "Line $id",
    tone: String? = null,
    stageTiers: List<Int> = emptyList(),
    types: List<Int> = emptyList(),
    moodDelta: Int = 0,
    loreRefs: List<String> = emptyList(),
): FreeChatReplyDto = FreeChatReplyDto(
    id = id,
    text = text,
    tone = tone,
    stageTiers = stageTiers,
    types = types,
    moodDelta = moodDelta,
    loreRefs = loreRefs,
)

internal fun chip(id: String, text: String = "Chip $id", intentId: String? = null): FreeChatChipDto =
    FreeChatChipDto(id = id, text = text, intentId = intentId)

internal fun condition(
    audience: String? = null,
    stageTiers: List<Int> = emptyList(),
    types: List<Int> = emptyList(),
    cores: List<String> = emptyList(),
    moodBands: List<String> = emptyList(),
    recentEvents: List<String> = emptyList(),
): FreeChatConditionDto = FreeChatConditionDto(
    audience = audience,
    stageTiers = stageTiers,
    types = types,
    cores = cores,
    moodBands = moodBands,
    recentEvents = recentEvents,
)

internal fun intent(
    id: String,
    topic: String = "general",
    tags: List<String> = emptyList(),
    matchPhrases: List<String> = emptyList(),
    keywords: List<String> = emptyList(),
    conditions: FreeChatConditionDto = FreeChatConditionDto(),
    replies: List<FreeChatReplyDto> = emptyList(),
    chips: List<FreeChatChipDto> = emptyList(),
    fallbackId: String? = null,
): FreeChatIntentDto = FreeChatIntentDto(
    id = id,
    topic = topic,
    tags = tags,
    matchPhrases = matchPhrases,
    keywords = keywords,
    conditions = conditions,
    replies = replies,
    chips = chips,
    fallbackId = fallbackId,
)

internal fun voice(
    speciesKey: String,
    speciesName: String = "Species $speciesKey",
    level: String? = null,
    type: String? = null,
    moves: List<String> = emptyList(),
): SpeciesVoiceDto = SpeciesVoiceDto(
    speciesKey = speciesKey,
    speciesName = speciesName,
    level = level,
    type = type,
    moves = moves,
)

internal fun content(
    intents: List<FreeChatIntentDto> = emptyList(),
    chips: List<FreeChatChipDto> = emptyList(),
    fallbacks: Map<Int, List<FreeChatReplyDto>> = emptyMap(),
    safety: Map<String, List<String>> = emptyMap(),
    voices: List<SpeciesVoiceDto> = emptyList(),
    databaseFallbacks: List<FreeChatDatabasePairDto> = emptyList(),
    contentVersion: Int = 1,
    locale: String = "en",
): FreeChatContent {
    val manifest = FreeChatManifestDto(
        contentVersion = contentVersion,
        locale = locale,
        files = mapOf("intents" to "free_chat.json", "speciesVoice" to "species_voice.json"),
    )
    val pack = FreeChatPackDto(
        contentVersion = contentVersion,
        locale = locale,
        intents = intents,
        chips = chips,
        fallbacks = fallbacks.entries
            .sortedBy { it.key }
            .map { (level, fallbackReplies) -> FreeChatFallbackDto(level = level, replies = fallbackReplies) },
        safety = safety,
    )
    val voicePack = SpeciesVoicePackDto(voices = voices)
    return FreeChatContent(
        manifest = manifest,
        pack = pack,
        speciesVoices = voicePack.voices
            .filter { it.speciesKey.isNotBlank() }
            .associateBy { it.speciesKey },
        speciesNames = voicePack.voices.map { it.speciesName }.filter { it.isNotBlank() },
        contentVersion = contentVersion,
        locale = locale,
        databaseFallbacks = databaseFallbacks,
    )
}

/** One licensed dataset fallback pair used by the database-layer tests. */
internal fun databasePair(
    user: String,
    response: String,
    source: String = "test-dataset",
    license: String = "test-license",
): FreeChatDatabasePairDto = FreeChatDatabasePairDto(
    user = user,
    response = response,
    source = source,
    license = license,
)

internal fun context(
    individualId: String = "ind-1",
    speciesKey: String? = null,
    speciesName: String? = null,
    level: String? = null,
    speciesType: String? = null,
    stageTier: Int = 2,
    personalityType: Int = 1,
    core: String = "PHILANTHROPY",
    mood: Int = 50,
    audience: FreeChatAudience = FreeChatAudience.OWNED,
    recentEventKeys: Set<String> = emptySet(),
    recentReplyIds: Set<String> = emptySet(),
    turnCounter: Long = 0,
    contentVersion: Int = 1,
): FreeChatContext = FreeChatContext(
    individualId = individualId,
    speciesKey = speciesKey,
    speciesName = speciesName,
    level = level,
    speciesType = speciesType,
    stageTier = stageTier,
    personalityType = personalityType,
    core = core,
    mood = mood,
    audience = audience,
    recentEventKeys = recentEventKeys,
    recentReplyIds = recentReplyIds,
    turnCounter = turnCounter,
    contentVersion = contentVersion,
)

internal fun noEntities(): FreeChatEntityMentions = FreeChatEntityMentions.EMPTY

/** Authored fallbacks shared by every content variant. */
internal fun authoredFallbacks(): Map<Int, List<FreeChatReplyDto>> = mapOf(
    1 to listOf(reply("fallback.level1.r1", "First light, first line.", tone = "warm")),
    2 to listOf(reply("fallback.2.r1", "That question broke the channel. Ask it another way.")),
    3 to listOf(
        reply("fallback.3.r1", "Let us leave that one alone."),
        reply("fallback.3.r2", "The signal went sideways. Try again."),
    ),
    4 to listOf(reply("fallback.4.r1", "I did not catch that. Say it smaller.")),
)

internal fun demoSafety(): Map<String, List<String>> = mapOf(
    "S1" to listOf("idiot", "burro", "stupid"),
    "S2" to listOf("kill you", "nude"),
    "S3" to listOf("suicid", "me matar"),
    "PII" to listOf("cpf", "phone"),
)

internal fun demoVoices(): List<SpeciesVoiceDto> = listOf(
    voice(
        speciesKey = "agumon",
        speciesName = "Agumon",
        level = "Child",
        type = "Vaccine",
        moves = listOf("Baby Flame", "Pepper Breath"),
    ),
    voice(
        speciesKey = "aegiomon",
        speciesName = "Aegiomon",
        level = "Adult",
        type = "God Man",
        moves = listOf("Tidal Wave"),
    ),
)

/** Intents of the canonical demo pack; one intent per engine behavior. */
internal fun demoIntents(): List<FreeChatIntentDto> = listOf(
    intent(
        id = "fc.greeting.1",
        topic = "greeting",
        matchPhrases = listOf("hi", "hello", "hey", "bom dia"),
        keywords = listOf("hi", "hello"),
        replies = listOf(
            reply("greeting.r1", "You came back. Good.", tone = "warm"),
            reply("greeting.r2", "Back again. The static was dull.", tone = "dry"),
            reply("greeting.r3", "Say it again, slower.", tone = "warm", moodDelta = 5),
        ),
        chips = listOf(chip("chip.what_are_you", "What are you?", "fc.identity.1")),
    ),
    intent(
        id = "fc.identity.1",
        topic = "identity",
        matchPhrases = listOf("what are you", "who are you"),
        keywords = listOf("digimon"),
        replies = listOf(
            reply("identity.r1", "I am what the field records say I am.", tone = "insight"),
        ),
    ),
    intent(
        id = "fc.agumon.1",
        topic = "species",
        tags = listOf("species"),
        matchPhrases = listOf("agumon"),
        replies = listOf(
            reply("agumon.r1", "Agumon. Child level, Vaccine type.", tone = "warm", loreRefs = listOf("agumon")),
        ),
    ),
    intent(
        id = "fc.audience.owned",
        topic = "partner",
        matchPhrases = listOf("partner talk"),
        conditions = condition(audience = "owned"),
        replies = listOf(reply("audience.owned.r1", "Owned partner line.", tone = "warm")),
    ),
    intent(
        id = "fc.audience.wild",
        topic = "partner",
        matchPhrases = listOf("partner talk"),
        conditions = condition(audience = "wild"),
        replies = listOf(reply("audience.wild.r1", "Wild contact line.", tone = "dry")),
    ),
    intent(
        id = "fc.tier.two",
        topic = "training",
        matchPhrases = listOf("training plan"),
        conditions = condition(stageTiers = listOf(2)),
        replies = listOf(reply("tier.two.r1", "Tier two training line.", tone = "courage")),
    ),
    intent(
        id = "fc.tier.three",
        topic = "training",
        matchPhrases = listOf("training plan"),
        conditions = condition(stageTiers = listOf(3)),
        replies = listOf(reply("tier.three.r1", "Tier three training line.", tone = "courage")),
    ),
    intent(
        id = "fc.type.five",
        topic = "battle",
        matchPhrases = listOf("battle plan"),
        conditions = condition(types = listOf(5)),
        replies = listOf(reply("type.five.r1", "Zealous battle line.", tone = "courage")),
    ),
    intent(
        id = "fc.type.nine",
        topic = "battle",
        matchPhrases = listOf("battle plan"),
        conditions = condition(types = listOf(9)),
        replies = listOf(reply("type.nine.r1", "Enlightened battle line.", tone = "insight")),
    ),
    intent(
        id = "fc.mood.low",
        topic = "mood",
        matchPhrases = listOf("how do you feel"),
        conditions = condition(moodBands = listOf("LOW")),
        replies = listOf(reply("mood.low.r1", "Low band line.", tone = "compassion")),
    ),
    intent(
        id = "fc.mood.positive",
        topic = "mood",
        matchPhrases = listOf("how do you feel"),
        conditions = condition(moodBands = listOf("POSITIVE")),
        replies = listOf(reply("mood.positive.r1", "Positive band line.", tone = "playful")),
    ),
    intent(
        id = "fc.core.valor",
        topic = "core",
        matchPhrases = listOf("valor talk"),
        conditions = condition(cores = listOf("VALOR")),
        replies = listOf(reply("core.valor.r1", "Valor core line.", tone = "courage")),
    ),
    intent(
        id = "fc.event.rain",
        topic = "event",
        matchPhrases = listOf("rain event"),
        conditions = condition(recentEvents = listOf("battle_win")),
        replies = listOf(reply("event.rain.r1", "Battle win memory line.", tone = "warm")),
    ),
    intent(
        id = "fc.reply.filter",
        topic = "training",
        matchPhrases = listOf("quiz me"),
        replies = listOf(
            reply("reply.filter.stage1", "Stage one line.", tone = "warm", stageTiers = listOf(1)),
            reply("reply.filter.stage2", "Stage two line.", tone = "warm", stageTiers = listOf(2)),
        ),
    ),
    intent(
        id = "fc.tone.split",
        topic = "story",
        matchPhrases = listOf("tell me a story"),
        replies = listOf(
            reply("tone.split.warm", "Warm story line.", tone = "warm"),
            reply("tone.split.dry", "Dry story line.", tone = "dry"),
        ),
    ),
    intent(
        id = "fc.recent",
        topic = "repeat",
        matchPhrases = listOf("again please"),
        replies = listOf(
            reply("recent.r1", "Repeat one.", tone = "warm"),
            reply("recent.r2", "Repeat two.", tone = "warm"),
        ),
    ),
    intent(
        id = "fc.needs.fallback",
        topic = "secret",
        matchPhrases = listOf("secret word"),
        replies = emptyList(),
        fallbackId = "fallback.2",
    ),
)

internal fun demoChips(): List<FreeChatChipDto> = listOf(
    chip("chip.greet", "Say hi", "fc.greeting.1"),
    chip("chip.wild", "Wild thing", "fc.audience.wild"),
    chip("chip.plain", "Plain chip"),
    chip("chip.extra", "Extra chip"),
)

/** Canonical full fixture used by determinism tests. */
internal fun demoContent(contentVersion: Int = 3): FreeChatContent = content(
    intents = demoIntents(),
    chips = demoChips(),
    fallbacks = authoredFallbacks(),
    safety = demoSafety(),
    voices = demoVoices(),
    contentVersion = contentVersion,
)

/** Canonical empty fixture: empty pack, no voices, no safety terms. */
internal fun emptyContent(): FreeChatContent = content()

/** Demo content with the authored level-1 fallback removed (ladder rung-2 tests). */
internal fun contentWithoutLevelOne(): FreeChatContent = content(
    intents = demoIntents(),
    chips = demoChips(),
    fallbacks = authoredFallbacks().filterKeys { it != 1 },
    safety = demoSafety(),
    voices = emptyList(),
)

/** Content with only rungs 3 and 4 authored. */
internal fun deflectionOnlyContent(): FreeChatContent = content(
    intents = emptyList(),
    fallbacks = authoredFallbacks().filterKeys { it >= 3 },
    safety = demoSafety(),
    voices = emptyList(),
)

/** Content with only rung 4 authored. */
internal fun confusionOnlyContent(): FreeChatContent = content(
    intents = emptyList(),
    fallbacks = authoredFallbacks().filterKeys { it == 4 },
    safety = demoSafety(),
    voices = emptyList(),
)

/** Deterministic floor-mod for seeded picks (mirrors the engine's private helper). */
internal fun floorMod(value: Long, modulus: Long): Int {
    if (modulus <= 0L) return 0
    val remainder = value % modulus
    return (if (remainder < 0L) remainder + modulus else remainder).toInt()
}
