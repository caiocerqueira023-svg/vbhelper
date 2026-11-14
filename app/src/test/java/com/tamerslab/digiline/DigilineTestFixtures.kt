package com.tamerslab.digiline

import com.tamerslab.digiline.model.AnswerOptionDto
import com.tamerslab.digiline.model.ContentManifest
import com.tamerslab.digiline.model.DemandDto
import com.tamerslab.digiline.model.DigilineContentPack
import com.tamerslab.digiline.model.EventLineDto
import com.tamerslab.digiline.model.LoreFactDto
import com.tamerslab.digiline.model.NegotiationEffectsDto
import com.tamerslab.digiline.model.NegotiationFlowDto
import com.tamerslab.digiline.model.NegotiationNodeDto
import com.tamerslab.digiline.model.NegotiationOptionDto
import com.tamerslab.digiline.model.NegotiationPackDto
import com.tamerslab.digiline.model.PersuasionPackDto
import com.tamerslab.digiline.model.QuestionCardDto
import com.tamerslab.digiline.model.QuizCardDto

internal const val TEST_DAY = 86_400_000L
internal const val TEST_HOUR = 3_600_000L

internal fun testManifest(contentVersion: Int = 1, locale: String = "en"): ContentManifest =
    ContentManifest(schemaVersion = 1, contentVersion = contentVersion, locale = locale, files = emptyMap())

internal fun answerOption(
    id: String,
    text: String = "Answer $id",
    reaction: String = "Reaction $id",
    moodDelta: Int = 0,
    shift: Map<String, Int> = emptyMap(),
    tone: String? = null,
): AnswerOptionDto = AnswerOptionDto(
    id = id,
    text = text,
    reaction = reaction,
    moodDelta = moodDelta,
    shift = shift,
    tone = tone,
)

internal fun questionCard(
    id: String,
    prompt: String = "Question $id",
    options: List<AnswerOptionDto> = listOf(answerOption("a"), answerOption("b")),
    stageTier: Int = 2,
    speciesKey: String? = null,
    speciesName: String? = null,
    askerTypes: List<Int> = emptyList(),
    core: String? = null,
    tags: List<String> = emptyList(),
): QuestionCardDto = QuestionCardDto(
    id = id,
    prompt = prompt,
    options = options,
    stageTier = stageTier,
    speciesKey = speciesKey,
    speciesName = speciesName,
    askerTypes = askerTypes,
    core = core,
    tags = tags,
)

internal fun quizCard(
    id: String,
    stageTier: Int = 2,
    options: List<String> = listOf("A", "B", "C"),
    correctIndex: Int = 0,
    explanation: String = "Because the catalog says so.",
): QuizCardDto = QuizCardDto(
    id = id,
    stageTier = stageTier,
    prompt = "Quiz $id",
    options = options,
    correctIndex = correctIndex,
    explanation = explanation,
)

internal fun effects(
    bits: Int = 0,
    itemId: String? = null,
    itemQuantity: Int = 0,
    persuasionFlag: String? = null,
    depart: Boolean = false,
): NegotiationEffectsDto = NegotiationEffectsDto(
    bits = bits,
    itemId = itemId,
    itemQuantity = itemQuantity,
    persuasionFlag = persuasionFlag,
    depart = depart,
)

internal fun option(
    id: String,
    text: String = "Option $id",
    next: String? = null,
    requires: String? = null,
    effects: NegotiationEffectsDto = NegotiationEffectsDto(),
): NegotiationOptionDto = NegotiationOptionDto(
    id = id,
    text = text,
    next = next,
    requires = requires,
    effects = effects,
)

internal fun node(
    id: String,
    text: String = "Node $id",
    tag: String? = null,
    outcome: String? = null,
    options: List<NegotiationOptionDto> = emptyList(),
    demand: DemandDto? = null,
): NegotiationNodeDto = NegotiationNodeDto(
    id = id,
    text = text,
    tag = tag,
    demand = demand,
    outcome = outcome,
    options = options,
)

internal fun flow(start: String, vararg nodes: NegotiationNodeDto): NegotiationFlowDto =
    NegotiationFlowDto(start = start, nodes = nodes.associateBy { it.id })

internal fun eventLine(
    id: String,
    event: String,
    types: List<Int> = emptyList(),
    text: String = "Line $id",
    moodDelta: Int = 0,
): EventLineDto = EventLineDto(id = id, event = event, types = types, text = text, moodDelta = moodDelta)

internal fun loreFact(
    speciesKey: String,
    speciesName: String = "Species $speciesKey",
    level: String? = "Adult",
    type: String? = "Vaccine",
): LoreFactDto = LoreFactDto(speciesKey = speciesKey, speciesName = speciesName, level = level, type = type)

internal fun testPack(
    questions: List<QuestionCardDto> = emptyList(),
    genericQuestions: List<QuestionCardDto> = emptyList(),
    quizzes: List<QuizCardDto> = emptyList(),
    negotiation: NegotiationPackDto = NegotiationPackDto(),
    persuasion: PersuasionPackDto = PersuasionPackDto(),
    eventLines: List<EventLineDto> = emptyList(),
    lore: List<LoreFactDto> = emptyList(),
    contentVersion: Int = 1,
): DigilineContentPack = DigilineContentPack(
    manifest = testManifest(contentVersion = contentVersion),
    questions = questions,
    genericQuestions = genericQuestions,
    quizzes = quizzes,
    negotiation = negotiation,
    persuasion = persuasion,
    eventLines = eventLines,
    lore = lore,
)
