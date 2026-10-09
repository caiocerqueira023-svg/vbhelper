package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.world.ecosystem.*
import org.json.JSONObject
import com.github.nacabaro.vbhelper.domain.mood.MoodDirectiveParser
import kotlinx.coroutines.withTimeout

/** Strict structured proposals: narrative text itself never executes a battle command. */
object WorldDialogueCodec {
    private fun payload(raw: String): JSONObject {
        require(raw.length<=12_000)
        return JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    }

    private fun attributedLines(json: JSONObject, speakers: Set<String>, maxLines: Int): List<DialogueLine> {
        val lines=json.getJSONArray("lines")
        require(lines.length() in 1..maxLines)
        val turns=(0 until lines.length()).map { index ->
            val line=lines.getJSONObject(index)
            val id=line.getString("speakerId")
            val text=MoodDirectiveParser.extract(line.getString("text")).first.trim()
            require(id in speakers && text.isNotBlank() && text.length<=500)
            val response = line.optString("response").takeIf { it.isNotBlank() }
                ?.let { runCatching { SocialResponse.valueOf(it) }.getOrNull() }
            DialogueLine(id,text,response = response)
        }
        require(turns.map { it.speakerId }.distinct().size==turns.size)
        return turns
    }

    /** Invalid intent metadata is discarded independently of valid, attributed speech. */
    fun parseReadableExchange(raw: String, speakers: Set<String>, evidence: Set<String>, maxLines: Int=speakers.size,
        targetsAllowed: Set<String> = speakers): DialogueExchange = try {
        parse(raw,speakers,evidence,maxLines,targetsAllowed)
    } catch (invalid: Exception) {
        DialogueExchange(attributedLines(payload(raw),speakers,maxLines))
    }

    /** Also used for legacy history: a failed envelope must never be printed as a reply. */
    fun visibleText(raw: String, speakerId: String): String? {
        val structured=raw.contains(Regex("\"(?:lines|intent)\"\\s*:"))
        if(!structured) return MoodDirectiveParser.extract(raw).first.trim().takeIf { it.isNotBlank() }
        return runCatching {
            val lines=payload(raw).getJSONArray("lines")
            val matching=(0 until lines.length()).map { lines.getJSONObject(it) }.filter { it.optString("speakerId")==speakerId }
            require(matching.size==1)
            MoodDirectiveParser.extract(matching.single().getString("text")).first.trim().take(1200).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun unreadableReply(language: String): String = when {
        language.startsWith("pt") -> "Não consegui concluir a resposta. Tente enviar a mensagem novamente."
        language.startsWith("ja") -> "返答を読み取れませんでした。もう一度メッセージを送ってください。"
        else -> "I could not finish that reply. Please try sending your message again."
    }

    fun parse(raw: String, speakers: Set<String>, evidence: Set<String>, maxLines: Int = speakers.size,
        targetsAllowed: Set<String> = speakers): DialogueExchange {
        val json = payload(raw)
        val turns = attributedLines(json,speakers,maxLines)
        val proposal = json.optJSONObject("intent")?.let { intent ->
            // Models often abbreviate the battle vocabulary (ACCEPT vs ACCEPT_CHALLENGE)
            // or mix letter case; normalize before validating so a real agreement is
            // not silently dropped while invented types still fail closed.
            val type = when (val rawType = intent.optString("type", "NONE").trim().uppercase()) {
                "NONE", "" -> return@let null
                "ACCEPT", "ACCEPT_CHALLENGE" -> DialogueIntentType.ACCEPT_CHALLENGE
                "DECLINE", "DECLINE_CHALLENGE" -> DialogueIntentType.DECLINE_CHALLENGE
                "CHALLENGE", "CHALLENGE_BATTLE" -> DialogueIntentType.CHALLENGE_BATTLE
                "DEESCALATE" -> DialogueIntentType.DEESCALATE
                else -> throw IllegalArgumentException("unknown intent type: $rawType")
            }
            val speaker = intent.getString("speakerId")
            val targets = intent.getJSONArray("targetIds").let { array -> (0 until array.length()).map { array.getString(it) } }
                .map { if (it.equals("trainer", ignoreCase = true)) "trainer" else it }
            val evidenceArray = intent.optJSONArray("evidenceIds")
            val support = if (evidenceArray != null) {
                (0 until evidenceArray.length()).map { evidenceArray.getString(it) }
            } else {
                // Singular evidenceId is accepted as a single-element evidence list.
                listOf(intent.getString("evidenceId"))
            }
            require(speaker in turns.map { it.speakerId } && targets.isNotEmpty() && targets.distinct().size == targets.size &&
                targets.all { it in targetsAllowed && it != speaker })
            require(support.isNotEmpty() && support.all { it in evidence || it == "this:$speaker" })
            val reason = intent.getString("reason").trim().take(160)
            require(reason.isNotEmpty())
            DialogueProposal(type, speaker, targets, support, reason, intent.optBoolean("sparring", false))
        }
        return DialogueExchange(turns, proposal)
    }
}

class NpcDialogueService(private val chat: ChatRepository,
    private val personality: suspend (String)->com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType? = { null }) {
    suspend fun exchange(speakers: List<Pair<String, Long>>, context: String, evidence: Set<String>,
        decision: SocialEncounterDecision? = null, recentKeys: Map<String, Set<String>> = emptyMap(),
        activeChallenge: DialogueProposal? = null): DialogueExchange {
        val ids = speakers.map { it.first }.toSet()
        val speakingIds = if (decision != null && !decision.listenerSpeaks && activeChallenge == null && decision.initiatorId in ids) setOf(decision.initiatorId) else ids
        return try {
            val raw = withTimeout(20_000) { chat.generateRadarExchange(speakers, context, instructions(speakingIds, evidence)) }
            WorldDialogueCodec.parseReadableExchange(raw, speakingIds, evidence, targetsAllowed = ids + "trainer")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
            fallback(speakingIds, decision, recentKeys, activeChallenge)
        } catch (failure: Exception) { fallback(speakingIds, decision, recentKeys, activeChallenge) }
    }

    private fun instructions(ids: Set<String>, evidence: Set<String>) = """
        Return only JSON: {"lines":[{"speakerId":"allowed ID","text":"one short public utterance","response":"OBSERVE"}],
        "intent":null or {"type":"CHALLENGE_BATTLE|ACCEPT_CHALLENGE|DECLINE_CHALLENGE|DEESCALATE",
        "speakerId":"allowed ID","targetIds":["allowed ID"],"evidenceIds":["message ID or this:speakerId"],
        "reason":"brief context-grounded reason","sparring":false}}.
        Allowed speaking participants: ${ids.joinToString()}. Return one or more attributed lines, at most one per speaking participant and 500 characters per line.
        Reserved participants may observe without speaking. Address the actual listener, not an imaginary human Tamer.
        Express the current encounter motive and relationship. Do not repeat recent greetings, questions, or topics.
        A participant who declined an invitation cannot be described as agreeing or participating.
        For a shared activity invitation, the invited participant must explicitly set response to ACCEPT_INVITATION or DECLINE_INVITATION.
        OBSERVE means they are listening or discussing; speaking alone does not mean acceptance. Never consent for another participant.
        This response field is not battle consent; battles require the separately validated intent and reciprocal challenge.
        Supporting prior message IDs: ${evidence.joinToString()}. Use this:speakerId for an explicit intent in the new line.
        A joke, quotation, hypothetical fight, mention of battles, or explicit refusal is NOT consent or a challenge.
        Only propose a challenge grounded in the actual attributed exchange. An actual hostile attack may begin without the target's agreement;
        friendly sparring is an invitation. Refusal/de-escalation can end an uncommitted invitation, but does not consent on anyone's behalf.
        You cannot impersonate the human, invent participants, assign teams, change numeric stats, award rewards, or use private history.
        Do not expose mood markers or reasoning. Use the configured conversation language and each speaker's persisted persona.
    """.trimIndent()

    private suspend fun fallback(ids: Set<String>, decision: SocialEncounterDecision?, recentKeys: Map<String, Set<String>>,
        activeChallenge: DialogueProposal?): DialogueExchange {
        val language = PromptLocalization.currentLanguageTag()
        val fallbackChoice = decision ?: SocialEncounterDecision(ids.sorted().first(), ids.sorted().last(), SocialMotive.OBSERVATION,
            (com.github.nacabaro.vbhelper.domain.personality.SocialRandom.unit(0, ids.sorted().joinToString(), contextKey(recentKeys), 0) * 64).toInt())
        val acceptance = decision?.motive == SocialMotive.CHALLENGE_SPARRING && activeChallenge?.sparring == true &&
            !decision.listenerDeclines && decision.targetId in ids && activeChallenge.speakerId == decision.initiatorId &&
            activeChallenge.targetIds == listOf(decision.targetId)
        val lines = ids.sorted().map { id ->
            val profile = com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile.forIndividual(id,
                personality(id) ?: com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.FRIENDLY)
            val opening = SocialOpenings.create(fallbackChoice, profile, language, recentKeys[id].orEmpty(), responding = id != fallbackChoice.initiatorId)
            val text = if (acceptance && id == fallbackChoice.targetId) when {
                language.startsWith("pt") -> "Aceito a luta amistosa de treino. Vamos praticar juntos."
                language.startsWith("ja") -> "友好的な練習試合に同意するよ。一緒に練習しよう。"
                else -> "I accept the friendly practice match. Let's train together."
            } else opening.text
            val response = if (id == fallbackChoice.targetId && fallbackChoice.motive == SocialMotive.SHARED_ACTIVITY) {
                if (fallbackChoice.listenerDeclines) SocialResponse.DECLINE_INVITATION else SocialResponse.ACCEPT_INVITATION
            } else if (id == fallbackChoice.targetId && fallbackChoice.listenerDeclines) SocialResponse.DECLINE_INVITATION else null
            DialogueLine(id, text, opening.key, response)
        }
        val proposal = when {
            acceptance -> DialogueProposal(DialogueIntentType.ACCEPT_CHALLENGE, fallbackChoice.targetId,
                listOf(fallbackChoice.initiatorId),listOf("this:${fallbackChoice.targetId}"),"Accepted friendly practice",true)
            decision?.motive == SocialMotive.CHALLENGE_SPARRING && activeChallenge == null && decision.initiatorId in ids ->
                DialogueProposal(DialogueIntentType.CHALLENGE_BATTLE,decision.initiatorId,listOf(decision.targetId),
                    listOf("this:${decision.initiatorId}"),"Invitation to friendly practice",true)
            activeChallenge != null && decision?.listenerDeclines == true && decision.targetId in ids ->
                DialogueProposal(DialogueIntentType.DECLINE_CHALLENGE,decision.targetId,listOf(decision.initiatorId),
                    listOf("this:${decision.targetId}"),"Declined the invitation",true)
            else -> null
        }
        return DialogueExchange(lines, proposal, source = DialogueTextSource.AUTHORED, locallyPlanned = decision != null)
    }

    private fun contextKey(keys: Map<String, Set<String>>) = keys.toSortedMap().entries.joinToString { (id, values) -> "$id:${values.sorted().joinToString()}" }
}
