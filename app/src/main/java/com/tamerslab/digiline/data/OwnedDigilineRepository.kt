package com.tamerslab.digiline.data

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.digiline.content.DigilineContentProvider
import com.tamerslab.digiline.engine.OwnedDigilineEngine
import com.tamerslab.digiline.engine.QuizEngine
import com.tamerslab.digiline.gateway.DigilineOptionView
import com.tamerslab.digiline.gateway.DigilineResult
import com.tamerslab.digiline.gateway.DigilineScene
import com.tamerslab.digiline.model.DigilineContentPack
import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.OwnedDigilineInput
import com.tamerslab.digiline.model.OwnedDigilineState
import com.tamerslab.digiline.model.QuestionKind
import com.tamerslab.digiline.model.QuizCardDto
import com.tamerslab.digiline.native.NativePersonalityMapping
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.device_data.UserCharacter
import com.tamerslab.domain.personality.DigimonPersonalityTraits
import com.tamerslab.domain.personality.DigimonPersonalityType
import com.tamerslab.species.SpeciesRepository

/**
 * Owned-Digimon DigiLine state (questions, quizzes, mood-as-bond and personality shifts).
 *
 * This is the persistence/orchestration half of the owned flow: it loads the authored pack once,
 * applies the deterministic cadence rules and persists every decision before rendering. Pure
 * decisions live in `com.tamerslab.digiline.engine`; all writes happen in Room transactions.
 *
 * Locked rules: mood is the bond stat (D2), quiz correct +3 / wrong -5 with a floor at 0 (D8),
 * personality shifts are applied to the persisted 16-type value (D3) and enqueue the native save
 * sync (D4/R2).
 */
class OwnedDigilineRepository(
    private val database: AppDatabase,
    private val content: DigilineContentProvider,
    private val speciesRepository: SpeciesRepository,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Whether a writable Time Stranger save path exists. The app currently has no Time Stranger
     * save writer (its capabilities are read/provisional-copy only), so a shift is queued as
     * UNAVAILABLE instead of pretending it reached the game. When a writer is added/opened for a
     * linked save, this callback returns true and new shifts queue as PENDING for the auto-sync.
     */
    private val timeStrangerWriteAvailable: () -> Boolean = { false },
) {
    private val dao get() = database.digilineDao()
    private val engine = OwnedDigilineEngine()

    /**
     * Reaction shown after an answer but not yet written to the transcript. The UI renders it as a
     * small acknowledgement card with a Continue button; the line is committed to history when the
     * player continues, so the card and the transcript never duplicate each other.
     */
    private data class PendingAck(
        val text: String,
        val contextKey: String,
        val outcomeKey: String? = null,
    )

    private val pendingAck = mutableMapOf<String, PendingAck>()

    fun observePendingBadge(individualId: String) = dao.observePendingQuestion(individualId)

    fun observeProfile(individualId: String) = dao.observeProfile(individualId)

    fun observeNativeSync(individualId: String) = dao.observeNativeSync(individualId)

    /** True until the one-time "social answers can change battle tendencies" notice is shown. */
    suspend fun shiftNoticePending(individualId: String): Boolean {
        val profile = dao.profile(individualId) ?: return false
        return !profile.nativeNoticeShown && profile.lastShiftAt > 0
    }

    suspend fun markShiftNoticeShown(individualId: String) {
        dao.profile(individualId)?.let { dao.upsertProfile(it.copy(nativeNoticeShown = true)) }
    }

    suspend fun individualIdForCharacter(characterId: Long): String? =
        runCatching { database.userCharacterDao().getCharacter(characterId).individualId }.getOrNull()

    /** Starts or resumes the next owned scene; [DigilineResult.Closed] means nothing is due. */
    suspend fun ensureScene(individualId: String): DigilineResult {
        val pack = content.get()
        val user = character(individualId)
            ?: return DigilineResult.Error("This partner has no active character record.")
        commitPendingAck(individualId)
        val profile = ensuredProfile(individualId, pack)

        pendingScene(individualId, pack, profile)?.let { return DigilineResult.Scene(it) }

        val current = now()
        if (current - profile.lastAskedAt in 0 until QUESTION_COOLDOWN_MILLIS) {
            return DigilineResult.Closed(null)
        }
        if (!pack.hasContent) return DigilineResult.Closed(null)

        val state = engine.plan(pack, ownedInput(individualId, user, profile, pack, current), current)
        return when (state) {
            is OwnedDigilineState.Question -> {
                dao.upsertQuestionRecord(
                    DigilineQuestionRecordEntity(
                        individualId = individualId,
                        questionKey = state.selection.card.id,
                        kind = state.kind.name,
                        askedAt = current,
                        status = STATUS_ASKED,
                    )
                )
                dao.upsertProfile(
                    profile.copy(
                        lastAskedAt = current,
                        contentVersion = pack.contentVersion,
                    )
                )
                DigilineResult.Scene(questionScene(state))
            }

            is OwnedDigilineState.Quiz -> {
                dao.upsertQuestionRecord(
                    DigilineQuestionRecordEntity(
                        individualId = individualId,
                        questionKey = state.quiz.id,
                        kind = KIND_QUIZ,
                        askedAt = current,
                        status = STATUS_ASKED,
                    )
                )
                dao.upsertProfile(
                    profile.copy(
                        lastAskedAt = current,
                        lastQuizAt = current,
                        contentVersion = pack.contentVersion,
                    )
                )
                DigilineResult.Scene(quizScene(state))
            }

            else -> DigilineResult.Closed(null)
        }
    }

    /** Applies one authored option id to the pending scene. */
    suspend fun choose(individualId: String, optionId: String): DigilineResult {
        val pack = content.get()
        val profile = ensuredProfile(individualId, pack)
        val record = dao.pendingQuestion(individualId)
            ?: return DigilineResult.Error("Nothing is waiting for an answer.")
        return if (record.kind == KIND_QUIZ) {
            chooseQuiz(individualId, optionId, record, profile, pack)
        } else {
            chooseQuestion(individualId, optionId, record, profile, pack)
        }
    }

    /** Closes the pending scene without answering; cooldowns still apply. */
    suspend fun dismiss(individualId: String): DigilineResult {
        commitPendingAck(individualId)
        val pack = content.get()
        val profile = ensuredProfile(individualId, pack)
        val current = now()
        val record = dao.pendingQuestion(individualId)
        if (record != null) {
            dao.upsertQuestionRecord(record.copy(status = STATUS_DISMISSED))
        }
        val quiz = record?.kind == KIND_QUIZ
        dao.upsertProfile(
            profile.copy(
                lastAskedAt = current,
                lastQuizAt = if (quiz) current else profile.lastQuizAt,
                sessionCounter = profile.sessionCounter + 1,
            )
        )
        return DigilineResult.Closed(null)
    }

    // ------------------------------------------------------------------ question answer

    private suspend fun chooseQuestion(
        individualId: String,
        optionId: String,
        record: DigilineQuestionRecordEntity,
        profile: DigilineProfileEntity,
        pack: DigilineContentPack,
    ): DigilineResult {
        val card = pack.questionById(record.questionKey)
            ?: return dismiss(individualId)
        val kind = runCatching { QuestionKind.valueOf(record.kind) }.getOrDefault(QuestionKind.GENERIC)
        val state = engine.questionState(card, seedFor(record, individualId, profile, pack), kind, record.askedAt)
        val current = now()
        val tendencies = dao.tendencies(individualId).associate { it.typeNumber to it.points }
        val traits = database.digimonIndividualDao().getPersonality(individualId)
        val currentType = traits?.personalityType?.number ?: 1
        val answeredSinceShift = dao.answeredQuestionCountSince(
            individualId,
            profile.lastShiftAt.takeIf { it > 0 } ?: 0L,
        )
        val lastDecayAt = dao.questionRecords(individualId).mapNotNull { it.answeredAt }.maxOrNull()
        val answer = engine.answerQuestion(
            state = state,
            optionId = optionId,
            tendencyPoints = tendencies,
            currentType = currentType,
            now = current,
            lastShiftAt = profile.lastShiftAt.takeIf { it > 0 },
            answeredSinceShift = answeredSinceShift,
            lastDecayAt = lastDecayAt,
        )
        if (!answer.accepted) return DigilineResult.Error(answer.message ?: "Unknown answer option.")

        val shift = answer.shift
        val user = character(individualId)
        database.withTransaction {
            dao.upsertQuestionRecord(
                record.copy(
                    answeredAt = current,
                    optionId = optionId,
                    shiftJson = shift?.let { encodePoints(it.points) },
                    moodDelta = answer.moodDelta,
                    status = STATUS_ANSWERED,
                )
            )
            if (shift != null) {
                dao.deleteTendencies(individualId)
                if (shift.points.isNotEmpty()) {
                    dao.upsertTendencies(
                        shift.points.map { (type, points) ->
                            PersonalityTendencyEntity(
                                individualId = individualId,
                                typeNumber = type,
                                points = points,
                                updatedAt = current,
                            )
                        }
                    )
                }
            }
            dao.upsertProfile(
                profile.copy(
                    lastAskedAt = profile.lastAskedAt,
                    lastShiftAt = shift?.lastShiftAt ?: profile.lastShiftAt,
                    sessionCounter = profile.sessionCounter + 1,
                    contentVersion = pack.contentVersion,
                )
            )
            if (shift != null && shift.typeChanged) {
                applyShift(individualId, traits, shift.newType, current)
            }
            user?.let { updateMood(it, answer.moodDelta) }
        }

        append(individualId, ROLE_USER, answer.option?.text)
        val reaction = answer.reaction.trim().ifEmpty {
            answer.option?.reaction?.trim().orEmpty()
        }
        if (reaction.isNotEmpty()) {
            pendingAck[individualId] = PendingAck(reaction, contextKey = "owned_reaction")
        }
        return if (reaction.isEmpty()) {
            DigilineResult.Closed(null)
        } else {
            DigilineResult.Scene(
                DigilineScene(
                    kind = DigilineScene.Kind.REACTION,
                    lines = listOf(reaction),
                    contextKey = "owned_reaction",
                    canDismiss = true,
                )
            )
        }
    }

    private suspend fun chooseQuiz(
        individualId: String,
        optionId: String,
        record: DigilineQuestionRecordEntity,
        profile: DigilineProfileEntity,
        pack: DigilineContentPack,
    ): DigilineResult {
        val quiz = pack.quizById(record.questionKey) ?: return dismiss(individualId)
        val index = optionId.toIntOrNull() ?: return DigilineResult.Error("Unknown answer option.")
        val current = now()
        val state = OwnedDigilineState.Quiz(quiz, record.askedAt)
        val result = engine.answerQuiz(state, index, current)
        val outcome = result.outcome ?: return DigilineResult.Error(result.message ?: "Quiz could not be resolved.")
        val user = character(individualId)
        val currentMood = user?.mood ?: QUIZ_DEFAULT_MOOD
        val newMood = QuizEngine.moodAfter(currentMood, outcome)
        val moodDelta = newMood - currentMood

        database.withTransaction {
            dao.upsertQuestionRecord(
                record.copy(
                    answeredAt = current,
                    optionId = index.toString(),
                    moodDelta = moodDelta,
                    status = STATUS_ANSWERED,
                )
            )
            dao.upsertQuizRecord(
                DigilineQuizRecordEntity(
                    individualId = individualId,
                    quizKey = quiz.id,
                    chosenIndex = index,
                    correct = outcome.correct,
                    moodDelta = moodDelta,
                    answeredAt = current,
                )
            )
            dao.upsertProfile(
                profile.copy(
                    lastAskedAt = profile.lastAskedAt,
                    lastQuizAt = current,
                    sessionCounter = profile.sessionCounter + 1,
                    contentVersion = pack.contentVersion,
                )
            )
            user?.let { updateMood(it, moodDelta) }
        }

        append(individualId, ROLE_USER, quiz.options.getOrNull(index))
        val explanation = quiz.explanation.trim()
        pendingAck[individualId] = PendingAck(
            text = explanation,
            contextKey = "quiz_result",
            outcomeKey = if (outcome.correct) "QUIZ_CORRECT" else "QUIZ_WRONG",
        )
        return DigilineResult.Scene(
            DigilineScene(
                kind = DigilineScene.Kind.REACTION,
                lines = if (explanation.isEmpty()) emptyList() else listOf(explanation),
                contextKey = "quiz_result",
                outcomeKey = if (outcome.correct) "QUIZ_CORRECT" else "QUIZ_WRONG",
                canDismiss = true,
            )
        )
    }

    // ------------------------------------------------------------------------ helpers

    private suspend fun applyShift(
        individualId: String,
        traits: DigimonPersonalityTraits?,
        newTypeNumber: Int,
        current: Long,
    ) {
        val type = DigimonPersonalityType.entries.firstOrNull { it.number == newTypeNumber } ?: return
        if (traits != null) {
            database.digimonIndividualDao()
                .upsertPersonality(traits.copy(personalityType = type, generatedAt = current))
        }
        dao.upsertNativeSync(
            NativePersonalitySyncEntity(
                individualId = individualId,
                targetType = newTypeNumber,
                targetNativeId = NativePersonalityMapping.toNativeId(type),
                state = if (timeStrangerWriteAvailable()) NATIVE_SYNC_PENDING else NATIVE_SYNC_UNAVAILABLE,
                updatedAt = current,
            )
        )
    }

    private suspend fun updateMood(user: UserCharacter, delta: Int) {
        if (delta == 0) return
        val newMood = (user.mood + delta).coerceIn(0, 100)
        if (newMood == user.mood) return
        database.userCharacterDao().updateCharacter(user.copy(mood = newMood))
    }

    private suspend fun character(individualId: String): UserCharacter? =
        database.userCharacterDao().getByIndividualId(individualId).firstOrNull()

    private suspend fun ensuredProfile(individualId: String, pack: DigilineContentPack): DigilineProfileEntity {
        dao.profile(individualId)?.let { existing ->
            if (existing.contentVersion != pack.contentVersion && pack.contentVersion > 0) {
                val updated = existing.copy(contentVersion = pack.contentVersion)
                dao.upsertProfile(updated)
                return updated
            }
            return existing
        }
        val variant = (DigilineSeed.stableHash(individualId) ushr 1).toInt().mod(SPECIES_VARIANTS)
        val created = DigilineProfileEntity(
            individualId = individualId,
            speciesVariant = variant,
            contentVersion = pack.contentVersion,
        )
        dao.upsertProfile(created)
        return created
    }

    private suspend fun pendingScene(
        individualId: String,
        pack: DigilineContentPack,
        profile: DigilineProfileEntity,
    ): DigilineScene? {
        val record = dao.pendingQuestion(individualId) ?: return null
        val scene = sceneForRecord(individualId, record, pack, profile)
        if (scene == null) {
            dao.upsertQuestionRecord(record.copy(status = STATUS_DISMISSED))
        }
        return scene
    }

    private fun sceneForRecord(
        individualId: String,
        record: DigilineQuestionRecordEntity,
        pack: DigilineContentPack,
        profile: DigilineProfileEntity,
    ): DigilineScene? {
        val seed = seedFor(record, individualId, profile, pack)
        return if (record.kind == KIND_QUIZ) {
            val quiz = pack.quizById(record.questionKey) ?: return null
            quizScene(OwnedDigilineState.Quiz(quiz, record.askedAt))
        } else {
            val card = pack.questionById(record.questionKey) ?: return null
            val kind = runCatching { QuestionKind.valueOf(record.kind) }.getOrDefault(QuestionKind.GENERIC)
            questionScene(engine.questionState(card, seed, kind, record.askedAt))
        }
    }

    private fun seedFor(
        record: DigilineQuestionRecordEntity,
        individualId: String,
        profile: DigilineProfileEntity,
        pack: DigilineContentPack,
    ): Long = DigilineSeed.sessionSeed(
        individualId = individualId,
        dayIndex = DigilineSeed.dayIndex(record.askedAt),
        sessionCounter = profile.sessionCounter,
        contentVersion = pack.contentVersion,
    )

    private suspend fun ownedInput(
        individualId: String,
        user: UserCharacter,
        profile: DigilineProfileEntity,
        pack: DigilineContentPack,
        current: Long,
    ): OwnedDigilineInput {
        val species = runCatching { speciesRepository.getProfileForCharacter(user.charId) }.getOrNull()
        val individual = database.digimonIndividualDao().getIndividual(individualId)
        val speciesKey = DigilineStages.speciesKey(
            species?.speciesName ?: species?.matchedName ?: individual?.speciesName
        )
        val traits = database.digimonIndividualDao().getPersonality(individualId)
        val personality = traits?.personalityType?.number ?: 1
        val tier = DigilineStages.tier(null, species?.level)
        return OwnedDigilineInput(
            individualId = individualId,
            personalityType = personality,
            stageTier = tier,
            speciesKey = speciesKey,
            speciesVariant = profile.speciesVariant,
            contentVersion = pack.contentVersion,
            dayIndex = DigilineSeed.dayIndex(current),
            sessionCounter = profile.sessionCounter,
            recentQuestionIds = dao.questionKeysSince(individualId, current - RECENT_QUESTION_WINDOW_MILLIS).toSet(),
            recentQuizIds = dao.quizKeysSince(individualId, current - RECENT_QUIZ_WINDOW_MILLIS).toSet(),
            allowSpeciesQuestion = speciesKey != null,
            allowGenericQuestion = true,
            allowQuiz = true,
            quizDue = current - profile.lastQuizAt >= QUIZ_COOLDOWN_MILLIS,
        )
    }

    private fun questionScene(state: OwnedDigilineState.Question): DigilineScene = DigilineScene(
        kind = DigilineScene.Kind.QUESTION,
        lines = listOf(state.selection.card.prompt),
        options = state.selection.orderedOptions.map { DigilineOptionView(it.id, it.text) },
        canDismiss = true,
        contextKey = "owned_question",
    )

    private fun quizScene(state: OwnedDigilineState.Quiz): DigilineScene = quizScene(state.quiz)

    private fun quizScene(quiz: QuizCardDto): DigilineScene = DigilineScene(
        kind = DigilineScene.Kind.QUIZ,
        lines = listOf(quiz.prompt),
        options = quiz.options.mapIndexed { index, text -> DigilineOptionView(index.toString(), text) },
        canDismiss = true,
        contextKey = "owned_quiz",
    )

    private suspend fun commitPendingAck(individualId: String) {
        val ack = pendingAck.remove(individualId) ?: return
        append(individualId, ROLE_ASSISTANT, ack.text)
    }

    private suspend fun append(individualId: String, role: String, text: String?) {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return
        database.chatDao().insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = role,
                content = value,
                timestamp = now(),
                kind = if (role == ROLE_USER) KIND_USER_MESSAGE else KIND_ASSISTANT_MESSAGE,
            )
        )
    }

    private fun encodePoints(points: Map<Int, Int>): String =
        points.entries.sortedBy { it.key }.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

    companion object {
        const val QUESTION_COOLDOWN_MILLIS = 12L * 60L * 60L * 1000L
        const val QUIZ_COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L
        const val RECENT_QUESTION_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L
        const val RECENT_QUIZ_WINDOW_MILLIS = 30L * 24L * 60L * 60L * 1000L

        const val STATUS_ASKED = "ASKED"
        const val STATUS_ANSWERED = "ANSWERED"
        const val STATUS_DISMISSED = "DISMISSED"
        const val KIND_QUIZ = "QUIZ"
        const val NATIVE_SYNC_PENDING = "PENDING"
        const val NATIVE_SYNC_UNAVAILABLE = "UNAVAILABLE_WRITE_PATH"

        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val KIND_USER_MESSAGE = "digiline_user"
        const val KIND_ASSISTANT_MESSAGE = "digiline_assistant"

        private const val SPECIES_VARIANTS = 4
        private const val QUIZ_DEFAULT_MOOD = 50
    }
}
