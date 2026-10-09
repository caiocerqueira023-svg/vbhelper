package com.github.nacabaro.vbhelper.domain.personality

/** Derived behavior, not a new personality assignment. Evolution does not change this baseline. */
data class DigimonSocialProfile(
    val personality: DigimonPersonalityType,
    val initiative: Double = 0.45,
    val curiosity: Double = 0.5,
    val warmth: Double = 0.5,
    val playfulness: Double = 0.4,
    val challenge: Double = 0.4,
    val territoriality: Double = 0.35,
    val patience: Double = 0.5,
    val loyalty: Double = 0.5,
    val empathy: Double = 0.5,
    val boundarySensitivity: Double = 0.5,
    val training: Double = 0.4
) {
    val playerCooldownTicks: Long get() = (100 + patience * 90 - initiative * 35).toLong()
    val activityCommitmentMillis: Long get() = (18_000 + patience * 22_000).toLong()

    fun instruction(language: String): String {
        val interests = listOf("company" to initiative, "exploration" to curiosity, "training" to training,
            "play" to playfulness, "helping" to empathy, "established companions" to loyalty)
            .sortedByDescending { it.second }.take(3).map { (key, _) -> when {
                language.startsWith("pt", true) -> when(key) {
                    "company" -> "companhia"; "exploration" -> "exploração"; "training" -> "treino";
                    "play" -> "brincadeiras"; "helping" -> "ajudar"; else -> "companheiros conhecidos"
                }
                language.startsWith("ja", true) -> when(key) {
                    "company" -> "交流"; "exploration" -> "探索"; "training" -> "訓練";
                    "play" -> "遊び"; "helping" -> "助けること"; else -> "親しい仲間"
                }
                else -> key
            } }
        return when {
            language.startsWith("pt", true) -> "Prioridades sociais deste indivíduo: ${interests.joinToString()}. " +
                "Deixe essas preferências mudar o que você nota, propõe, aceita ou recusa. Afeto e familiaridade dependem da relação registrada; " +
                "você pode discordar, observar ou encerrar uma conversa sem fazer uma pergunta. ${personality.promptInstruction(language)}"
            language.startsWith("ja", true) -> "この個体の関心: ${interests.joinToString()}。関心を、気づくことや提案、承諾、断り方に反映する。" +
                "親しさは記録された関係に合わせる。毎回質問する必要はなく、観察したり会話を終えたりしてよい。${personality.promptInstruction(language)}"
            else -> "This individual's strongest social interests: ${interests.joinToString()}. " +
                "Let these change what you notice, propose, accept, or decline. Warmth and familiarity depend on the recorded relationship. " +
                "You can disagree, observe, or finish a conversation without asking a question. ${personality.promptInstruction(language)}"
        }
    }

    companion object {
        const val BEHAVIOR_VERSION = 1

        fun forIndividual(id: String, type: DigimonPersonalityType): DigimonSocialProfile {
            val base = DigimonSocialProfile(type)
            val p = when (type) {
                DigimonPersonalityType.ADORING -> base.copy(initiative = .65, warmth = .95, empathy = .85, loyalty = .85, challenge = .18)
                DigimonPersonalityType.DEVOTED -> base.copy(initiative = .35, curiosity = .25, warmth = .7, loyalty = .98, patience = .8, training = .6)
                DigimonPersonalityType.TOLERANT -> base.copy(initiative = .32, patience = .95, empathy = .7, boundarySensitivity = .3, territoriality = .12, challenge = .2)
                DigimonPersonalityType.OVERPROTECTIVE -> base.copy(initiative = .6, empathy = .9, loyalty = .9, boundarySensitivity = .95, territoriality = .7, challenge = .25)
                DigimonPersonalityType.ZEALOUS -> base.copy(initiative = .82, challenge = .7, training = .92, loyalty = .8, patience = .3, playfulness = .22)
                DigimonPersonalityType.BRAVE -> base.copy(initiative = .7, challenge = .9, empathy = .65, training = .72, territoriality = .45, patience = .4)
                DigimonPersonalityType.RECKLESS -> base.copy(initiative = .9, curiosity = .78, challenge = .97, territoriality = .65, patience = .1, boundarySensitivity = .75, training = .65)
                DigimonPersonalityType.DARING -> base.copy(initiative = .78, curiosity = .96, challenge = .8, playfulness = .75, loyalty = .35, patience = .35)
                DigimonPersonalityType.ENLIGHTENED -> base.copy(initiative = .2, curiosity = .65, patience = .95, empathy = .7, territoriality = .1, challenge = .18)
                DigimonPersonalityType.SLY -> base.copy(initiative = .48, curiosity = .78, playfulness = .88, warmth = .3, challenge = .55, patience = .7, territoriality = .4)
                DigimonPersonalityType.ASTUTE -> base.copy(initiative = .3, curiosity = .9, patience = .8, playfulness = .35, warmth = .4, training = .55)
                DigimonPersonalityType.STRATEGIC -> base.copy(initiative = .28, curiosity = .6, patience = .9, training = .85, playfulness = .15, challenge = .55, territoriality = .55)
                DigimonPersonalityType.OPPORTUNISTIC -> base.copy(initiative = .62, curiosity = .8, patience = .3, challenge = .6, loyalty = .3, territoriality = .5)
                DigimonPersonalityType.FRIENDLY -> base.copy(initiative = .72, warmth = .9, playfulness = .6, empathy = .65, territoriality = .12, challenge = .25)
                DigimonPersonalityType.SOCIABLE -> base.copy(initiative = .98, curiosity = .8, warmth = .8, playfulness = .85, patience = .35, loyalty = .45, challenge = .3)
                DigimonPersonalityType.COMPASSIONATE -> base.copy(initiative = .6, warmth = .9, empathy = .98, patience = .8, territoriality = .15, challenge = .15, loyalty = .75)
            }
            fun vary(value: Double, tag: String) = (value + (SocialRandom.unit(0, id, "${type.number}:$tag", 0) - .5) * .10).coerceIn(.05, .99)
            return p.copy(initiative = vary(p.initiative, "initiative"), curiosity = vary(p.curiosity, "curiosity"),
                warmth = vary(p.warmth, "warmth"), playfulness = vary(p.playfulness, "play"), challenge = vary(p.challenge, "challenge"),
                territoriality = vary(p.territoriality, "territory"), patience = vary(p.patience, "patience"),
                loyalty = vary(p.loyalty, "loyalty"), empathy = vary(p.empathy, "empathy"),
                boundarySensitivity = vary(p.boundarySensitivity, "boundaries"), training = vary(p.training, "training"))
        }
    }
}

/** Stable, purpose-separated draws shared by local social policies. No wall clock or iteration order. */
object SocialRandom {
    fun unit(seed: Long, id: String, purpose: String, step: Long): Double {
        var hash = -3750763034362895579L
        "$seed:${id.length}:$id:${purpose.length}:$purpose:$step".forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
        hash = (hash xor (hash ushr 30)) * -4658895280553007687L
        hash = (hash xor (hash ushr 27)) * -7723592293110705685L
        hash = hash xor (hash ushr 31)
        return (hash ushr 11).toDouble() / 9_007_199_254_740_992.0
    }
}
