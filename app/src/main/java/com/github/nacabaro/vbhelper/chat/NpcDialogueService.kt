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
            DialogueLine(id,text)
        }
        require(turns.map { it.speakerId }.distinct().size==turns.size && turns.map { it.speakerId }.toSet()==speakers)
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
            val type = DialogueIntentType.valueOf(intent.optString("type", "NONE"))
            if (type == DialogueIntentType.NONE) return@let null
            val speaker = intent.getString("speakerId")
            val targets = intent.getJSONArray("targetIds").let { array -> (0 until array.length()).map { array.getString(it) } }
            val support = intent.getJSONArray("evidenceIds").let { array -> (0 until array.length()).map { array.getString(it) } }
            require(speaker in speakers && targets.isNotEmpty() && targets.distinct().size == targets.size &&
                targets.all { it in targetsAllowed && it != speaker })
            require(support.isNotEmpty() && support.all { it in evidence || it == "this:$speaker" })
            val reason = intent.getString("reason").trim()
            require(reason.isNotEmpty() && reason.length <= 160)
            DialogueProposal(type, speaker, targets, support, reason, intent.optBoolean("sparring", false))
        }
        return DialogueExchange(turns, proposal)
    }
}

class NpcDialogueService(private val chat: ChatRepository,
    private val personality: suspend (String)->com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType? = { null }) {
    suspend fun exchange(speakers: List<Pair<String, Long>>, context: String, evidence: Set<String>): DialogueExchange {
        val ids = speakers.map { it.first }.toSet()
        return try {
            val raw = withTimeout(20_000) { chat.generateRadarExchange(speakers, context, instructions(ids, evidence)) }
            WorldDialogueCodec.parseReadableExchange(raw, ids, evidence, targetsAllowed = ids + "trainer")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
            fallback(ids)
        } catch (failure: Exception) { fallback(ids) }
    }

    private fun instructions(ids: Set<String>, evidence: Set<String>) = """
        Return only JSON: {"lines":[{"speakerId":"allowed ID","text":"one short public utterance"}],
        "intent":null or {"type":"CHALLENGE_BATTLE|ACCEPT_CHALLENGE|DECLINE_CHALLENGE|DEESCALATE",
        "speakerId":"allowed ID","targetIds":["allowed ID"],"evidenceIds":["message ID or this:speakerId"],
        "reason":"brief context-grounded reason","sparring":false}}.
        Allowed speakers: ${ids.joinToString()}. Each speaker contributes exactly one line, at most 500 characters.
        Supporting prior message IDs: ${evidence.joinToString()}. Use this:speakerId for an explicit intent in the new line.
        A joke, quotation, hypothetical fight, mention of battles, or explicit refusal is NOT consent or a challenge.
        Only propose a challenge grounded in the actual attributed exchange. An actual hostile attack may begin without the target's agreement;
        friendly sparring is an invitation. Refusal/de-escalation can end an uncommitted invitation, but does not consent on anyone's behalf.
        You cannot impersonate the human, invent participants, assign teams, change numeric stats, award rewards, or use private history.
        Do not expose mood markers or reasoning. Use the configured conversation language and each speaker's persisted persona.
    """.trimIndent()

    private suspend fun fallback(ids: Set<String>): DialogueExchange {
        val language = PromptLocalization.currentLanguageTag()
        val lines = ids.sorted().mapIndexed { index, id ->
            val style=personality(id)
            val adventurous=style in listOf(com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.RECKLESS,
                com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.DARING,com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.BRAVE)
            val calm=style in listOf(com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.TOLERANT,
                com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.ENLIGHTENED,com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.STRATEGIC)
            DialogueLine(id, when {
            adventurous && language.startsWith("pt") -> "Vamos explorar um pouco, sem sair daqui de perto!"
            adventurous && language.startsWith("ja") -> "近くを少し探検してみよう！"
            adventurous -> "Let's explore a little, staying close to home!"
            calm && language.startsWith("pt") -> "Podemos ir com calma e observar o que acontece."
            calm && language.startsWith("ja") -> "落ち着いて、様子を見ていこう。"
            calm -> "We can take our time and see what happens."
            language.startsWith("pt") -> if (index == 0) "Vamos descansar um pouco por aqui." else "Boa ideia. Vou ficar por perto."
            language.startsWith("ja") -> if (index == 0) "ここで少し休もう。" else "いいね。近くにいるよ。"
            else -> if (index == 0) "Let's rest here for a moment." else "Good idea. I'll stay nearby."
        }) }
        return DialogueExchange(lines, source = DialogueTextSource.AUTHORED)
    }
}
