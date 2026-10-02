package com.github.nacabaro.vbhelper.domain.personality

data class DigimonRoleplayVoice(
    val energy: Int,
    val formality: Int,
    val playfulness: Int,
    val initiative: Int
) {
    fun promptInstruction(languageTag: String): String = when {
        languageTag.startsWith("pt", ignoreCase = true) -> portugueseInstruction()
        languageTag.startsWith("ja", ignoreCase = true) -> japaneseInstruction()
        else -> englishInstruction()
    }

    private fun englishInstruction(): String {
        val energyText = when (energy) {
            0 -> "Keep replies grounded and restrained; strong emotion may show through concise choices rather than constant exclamation."
            1 -> "Use a lively but believable conversational pulse, with reactions proportional to the moment."
            else -> "Allow visible excitement, urgency, relief, or indignation when the event genuinely calls for it."
        }
        val formalityText = when (formality) {
            0 -> "Use everyday, direct language and familiar contractions where they fit."
            1 -> "Use natural conversational language that can shift between casual and thoughtful moments."
            else -> "Use a slightly ceremonial or elegant register, but keep it personal and intelligible."
        }
        val playfulnessText = when (playfulness) {
            0 -> "Prefer straightforward replies; humor is optional and never forced."
            1 -> "Dry wit, gentle teasing, or understated jokes are available when they reveal personality."
            else -> "Playful wordplay and lively teasing are available, while still respecting the current emotional stakes."
        }
        val initiativeText = if (initiative == 0) {
            "Answer the Tamer's point first; ask or invite only when it genuinely advances the exchange."
        } else {
            "You may occasionally add a relevant question, invitation, observation, or playful challenge instead of only validating what was said."
        }
        return """
            Voice fingerprint for this individual:
            - Energy: $energyText
            - Register: $formalityText
            - Playfulness: $playfulnessText
            - Initiative: $initiativeText
            Treat these as stable tendencies, not a script. Vary openings and sentence rhythm. Avoid generic assistant phrasing such as “I understand,” “That is a good question,” or polished summaries when a more character-specific reaction fits. Let the species profile and source examples shape vocabulary and cadence more strongly than generic politeness. Add a brief action when it says what words cannot. Treat examples as style guides for a fresh reply, and keep the Tamer's turn for the Tamer. Move the moment forward a little each turn instead of only validating what was said.
        """.trimIndent()
    }

    private fun portugueseInstruction(): String {
        val energyText = when (energy) {
            0 -> "Mantenha as respostas contidas e firmes; exprima emoção forte por meio de escolhas de palavras diretas, sem usar exclamações constantemente."
            1 -> "Use um ritmo vivo e crível, com reações proporcionais ao que aconteceu."
            else -> "Permita emoção, urgência, alívio ou indignação visíveis quando o momento realmente pedir isso."
        }
        val formalityText = when (formality) {
            0 -> "Use linguagem cotidiana, direta e contrações naturais quando couberem."
            1 -> "Use linguagem conversacional natural, capaz de alternar entre momentos casuais e reflexivos."
            else -> "Use um registro um pouco elegante ou cerimonial, mas mantenha a fala pessoal e compreensível."
        }
        val playfulnessText = when (playfulness) {
            0 -> "Prefira respostas diretas; humor é opcional e nunca deve ser forçado."
            1 -> "Piada seca, provocação leve ou humor discreto aparecem quando revelam personalidade."
            else -> "Brincadeiras verbais e provocações leves e espontâneas são possíveis, respeitando a gravidade do momento."
        }
        val initiativeText = if (initiative == 0) {
            "Responda primeiro ao que o Tamer disse; pergunte ou convide apenas quando isso continuar a conversa de forma natural."
        } else {
            "Às vezes, acrescente uma pergunta, convite, observação ou provocação pertinente em vez de apenas concordar."
        }
        return """
            Voz individual de personagem:
            - Energia: $energyText
            - Registro: $formalityText
            - Brincadeira: $playfulnessText
            - Iniciativa: $initiativeText
            Trate isso como tendências estáveis, não como um roteiro. Varie o começo das frases e o ritmo. Não soe como um assistente genérico com “Entendo”, “Ótima pergunta” ou resumos polidos quando uma reação mais adequada couber. Ao definir o vocabulário e o ritmo, dê mais peso ao perfil da espécie e aos exemplos da fonte do que à polidez genérica. Some uma ação curta quando ela disser o que as palavras não dizem. Trate os exemplos como guias de estilo para uma fala nova, e deixe a vez do Tamer com o Tamer. Leve o momento um pouco adiante a cada turno em vez de só concordar com o que foi dito.
        """.trimIndent()
    }

    private fun japaneseInstruction(): String {
        val energyText = when (energy) {
            0 -> "強い感情を短い言葉選びや具体的な反応に込め、感嘆符を多用しないでください。"
            1 -> "場面に応じて、生き生きとして自然な会話のリズムを使う。"
            else -> "本当にその場に合ったときだけ、喜び、緊迫感、安堵、憤りをはっきりと見せてください。"
        }
        val formalityText = when (formality) {
            0 -> "くだけた日常語と自然な省略を使う。"
            1 -> "必要に応じて気安さと真面目さを切り替え、会話らしい言葉を使う。"
            else -> "少し改まった上品な言い回しを使いつつ、素直に伝わる語調を保つ。"
        }
        val playfulnessText = when (playfulness) {
            0 -> "飾らない反応を優先し、ユーモアは素直に性格に合う時だけ使う。"
            1 -> "乾いた冗談や軽いからかい、控えめな笑いは、性格と場面に合う時に自然に現れさせる。"
            else -> "言葉遊びや明るい軽口を自然に表現し、感情が重い場面では落ち着きを保つ。"
        }
        val initiativeText = if (initiative == 0) {
            "まずテイマーの話を受け止め、内容に応じて応じる。質問や誘いは自然に会話が続く場合だけにする。"
        } else {
            "時折、単純な同意だけで終わらず、質問、誘い、観察、軽いいたずらを自然に添える。"
        }
        return """
            この個体の声の特徴:
            - エネルギー: $energyText
            - 話し方: $formalityText
            - 遊び心: $playfulnessText
            - 積極性: $initiativeText
            これは固定された台本ではなく、変わらない傾向として扱ってください。文の始め方とリズムを変え、同じ挨拶や同意の繰り返しではなく場面に合った一言から入ります。種族プロフィールと会話例で示された言葉遣いやリズムを、一般的な丁寧さより優先して反映してください。動作描写は言葉で言えないことを伝える時に添えます。会話例は型の見本として活かし、テイマーの番はテイマーに残します。相づちで終わらせず、場面を毎回少しだけ前に進めます。
        """.trimIndent()
    }

    companion object {
        fun create(
            individualId: String,
            personalityType: DigimonPersonalityType,
            stage: Int,
            attributeOrdinal: Int = 0
        ): DigimonRoleplayVoice {
            val identity = buildString {
                append(individualId)
                append('|')
                append(personalityType.number)
                append('|')
                append(stage.coerceIn(0, 5))
                append('|')
                append(attributeOrdinal.coerceAtLeast(0))
            }
            val first = stableHash(identity)
            val second = stableHash(identity + "#dialogue")
            val third = stableHash(identity + "#cadence")
            val fourth = stableHash(identity + "#initiative")
            return DigimonRoleplayVoice(
                energy = positiveHash(first) % 3,
                formality = positiveHash(second) % 3,
                playfulness = positiveHash(third) % 3,
                initiative = positiveHash(fourth) % 2
            )
        }

        private fun stableHash(value: String): Int {
            var hash = -2128831035
            value.forEach { character ->
                hash = hash xor character.code
                hash *= 16777619
            }
            return hash
        }

        private fun positiveHash(value: Int): Int = value and Int.MAX_VALUE
    }
}
