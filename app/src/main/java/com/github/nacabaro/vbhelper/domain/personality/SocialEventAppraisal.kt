package com.github.nacabaro.vbhelper.domain.personality

enum class SocialStimulus { PRAISE, CARE, CHALLENGE, TEASING, REFUSAL, INSULT, AGREEMENT, NEUTRAL }

/** Conservative appraisal of the actual turn, never sentiment manufactured by the generated reply. */
object SocialEventAppraisal {
    fun classify(text: String): SocialStimulus {
        val lower = text.lowercase().replace(Regex("[,、，]"), " ").replace(Regex("\\s+"), " ")
        fun contains(vararg words: String) = words.any { word ->
            if (word.any { it.code > 0x3000 }) word in lower
            else Regex("(?<![\\p{L}])${Regex.escape(word)}(?![\\p{L}])").containsMatchIn(lower)
        }
        return when {
            contains("just teasing", "just kidding", "brincadeira", "só brincando", "冗談") -> SocialStimulus.TEASING
            contains("stupid", "idiot", "worthless", "estúpido", "idiota", "inútil", "ばか", "馬鹿") -> SocialStimulus.INSULT
            contains("no thanks", "not now", "leave me alone", "another time", "i'll pass", "não quero", "agora não", "não obrigado", "vou passar", "desta vez não", "遠慮", "今はやめ", "今回はやめ") -> SocialStimulus.REFUSAL
            contains("sparring", "train together", "challenge", "duel", "treinar juntos", "desafio", "duelo", "勝負", "一緒に訓練") -> SocialStimulus.CHALLENGE
            contains("lets do it", "let's do it", "count me in", "i'm in", "sounds good", "bring it on", "you're on", "youre on", "lets go", "let's go",
                "deal", "agreed", "accepted", "i accept", "ok", "okay", "yes", "sure", "yeah", "yep", "alright", "all right", "of course", "absolutely", "definitely", "sim",
                "combinado", "fechado", "aceito", "bora", "vamos", "claro", "com certeza", "pode ser", "pode vir", "traga", "tô dentro", "to dentro",
                "わかった", "いいよ", "やろう", "受けて立つ", "望むところ", "もちろん", "了解", "承知", "オッケー", "おっけー", "ぜひ") -> SocialStimulus.AGREEMENT
            contains("thank you", "well done", "proud of you", "obrigado", "obrigada", "parabéns", "orgulho de você", "ありがとう", "よくやった") -> SocialStimulus.PRAISE
            contains("are you okay", "need help", "get some rest", "está bem", "precisa de ajuda", "descanse", "大丈夫", "休んで") -> SocialStimulus.CARE
            else -> SocialStimulus.NEUTRAL
        }
    }

    fun delta(profile: DigimonSocialProfile, stimulus: SocialStimulus): Int = when (stimulus) {
        SocialStimulus.PRAISE -> (1 + profile.warmth * 3).toInt()
        SocialStimulus.CARE -> (1 + profile.empathy * 3).toInt()
        SocialStimulus.CHALLENGE -> when { profile.challenge > .65 -> 2; profile.challenge < .35 -> -1; else -> 0 }
        SocialStimulus.TEASING -> when { profile.playfulness > .65 -> 2; profile.boundarySensitivity > .8 -> -1; else -> 0 }
        SocialStimulus.REFUSAL -> 0
        SocialStimulus.INSULT -> -(2 + profile.boundarySensitivity * 3).toInt()
        SocialStimulus.AGREEMENT -> 3
        SocialStimulus.NEUTRAL -> 0
    }

    fun resolve(profile: DigimonSocialProfile, text: String, modelDelta: Int?): Int {
        val stimulus = classify(text)
        // Turning the Digimon down stings a little, no matter how politely phrased.
        if (stimulus == SocialStimulus.REFUSAL) return -1
        val local = delta(profile, stimulus)
        // A model's cheerful prose alone cannot farm trust, and it cannot zero out
        // a real signal either: only a same-sign marker is honored.
        val resolved = if (modelDelta != null && modelDelta.signMatches(local)) modelDelta.coerceIn(-4, 4) else local
        // Every other turn moves the bar at a lively pace: agreement counts most,
        // and plain chit-chat still builds real trust through shared time.
        return if (resolved == 0) 2 else resolved
    }

    fun reactionDelta(profile: DigimonSocialProfile, event: String): Int {
        val text = event.lowercase()
        return when {
            listOf("healed", "curada", "治った").any { it in text } -> (1 + profile.empathy * 2).toInt()
            listOf("injured", "machucou", "負傷").any { it in text } -> -(1 + profile.boundarySensitivity * 2).toInt()
            listOf("loss(es)", "derrotas", "敗北").any { it in text } -> if (profile.training > .8) -1 else -(1 + profile.boundarySensitivity * 2).toInt()
            listOf("win(s)", "vitórias", "勝利").any { it in text } -> (1 + profile.challenge * 3).toInt()
            else -> 0
        }
    }

    private fun Int.signMatches(other: Int) = this > 0 && other > 0 || this < 0 && other < 0
}
