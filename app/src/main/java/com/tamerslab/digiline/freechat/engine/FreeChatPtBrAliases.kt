package com.tamerslab.digiline.freechat.engine

/**
 * PT-BR → EN intent aliasing used only by the **matching** path (owner bug report 2026-11-18:
 * basic Portuguese input produced disconnected one-liners).
 *
 * The authored corpus is EN-first, so common Brazilian-Portuguese conversation moves are translated
 * for retrieval only; the transcript keeps the player's original text and the safety filter runs on
 * the un-aliased normalized input. Longest phrase wins; unknown tokens pass through unchanged.
 *
 * This is a fixed, reviewed dictionary, not machine translation: it covers greetings, identity,
 * feelings, thanks/farewell, food, bond and battle/training vocabulary. Data-driven expansion would
 * live in a future locale pack, but the runtime contract is the same.
 */
object FreeChatPtBrAliases {
    fun alias(normalized: String): String {
        if (normalized.isEmpty()) return ""
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val output = StringBuilder(normalized.length)
        var index = 0
        while (index < tokens.size) {
            val phrase = PHRASES.entries.firstOrNull { (source, _) ->
                val parts = source.split(' ')
                index + parts.size <= tokens.size && parts.indices.all { tokens[index + it] == parts[it] }
            }
            if (output.isNotEmpty()) output.append(' ')
            if (phrase != null) {
                output.append(phrase.value)
                index += phrase.key.split(' ').size
            } else {
                output.append(WORDS[tokens[index]] ?: tokens[index])
                index++
            }
        }
        return output.toString()
    }

    /** Multi-word conversation moves, longest phrase first (keys are accent-free/lowercase). */
    private val PHRASES: Map<String, String> = linkedMapOf(
        "o que voce gosta de comer" to "what do you like to eat",
        "voce gosta de comer" to "what do you like to eat",
        "como voce se sente" to "how do you feel",
        "o que voce esta fazendo" to "what are you doing",
        "eu tive um bom dia" to "i had a good day",
        "meu dia foi bom" to "i had a good day",
        "voce confia em mim" to "do you trust me",
        "qual e o seu nome" to "what is your name",
        "como voce esta" to "how are you",
        "como vai voce" to "how are you",
        "como foi seu dia" to "how was your day",
        "o que voce acha" to "what do you think",
        "o que voce quer" to "what do you want",
        "o que tem de novo" to "what is new",
        "estou com medo" to "i am scared",
        "estou preocupado" to "i am worried",
        "estou ansioso" to "i am anxious",
        "voce e demais" to "you are the best",
        "bom dia" to "hello",
        "boa tarde" to "hello",
        "boa noite" to "good night",
        "tudo bem" to "how are you",
        "tudo bom" to "how are you",
        "quem e voce" to "who are you",
        "meu nome e" to "my name is",
        "eu te amo" to "i love you",
        "eu gosto de" to "i like",
        "eu nao sei" to "i do not know",
        "nao sei" to "i do not know",
        "estou triste" to "i am sad",
        "eu estou triste" to "i am sad",
        "me sinto triste" to "i am sad",
        "estou feliz" to "i am happy",
        "estou bem" to "i am fine",
        "estou cansado" to "i am tired",
        "confio em voce" to "i trust you",
        "vamos treinar" to "let us train",
        "ate logo" to "goodbye",
        "ate mais" to "goodbye",
        "e ai" to "hi",
        "o que voce quer dizer" to "what do you mean",
        "o que isso significa" to "what does that mean",
        "como assim" to "what do you mean",
        "nao entendi" to "i do not understand",
        "nao entendi nada" to "i do not understand",
        "repete" to "say that again",
        "repete por favor" to "say that again",
        "e voce" to "what about you",
        "eu sou" to "i am",
        "voce e" to "you are",
        "eu quero" to "i want",
        "meu parceiro" to "my partner",
        "vamos la" to "let us go",
        "vamos" to "let us",
        "por favor" to "please",
        "muito obrigado" to "thanks",
        "muito obrigada" to "thanks",
    )

    /** Single-token PT-BR conversation vocabulary → EN token. */
    private val WORDS: Map<String, String> = mapOf(
        "oi" to "hi", "ola" to "hello", "opa" to "hi", "eai" to "hi", "salve" to "hi",
        "obrigado" to "thanks", "obrigada" to "thanks", "valeu" to "thanks",
        "desculpa" to "sorry", "desculpe" to "sorry",
        "tchau" to "goodbye", "falou" to "goodbye", "fui" to "goodbye",
        "triste" to "sad", "feliz" to "happy", "cansado" to "tired", "cansada" to "tired",
        "bravo" to "angry", "brava" to "angry", "medo" to "fear", "amor" to "love",
        "saudade" to "miss", "sozinho" to "alone", "sozinha" to "alone",
        "voce" to "you", "voces" to "you", "seu" to "your", "sua" to "your",
        "meu" to "my", "minha" to "my", "estou" to "i am", "sou" to "i am",
        "esta" to "is", "ta" to "is", "tem" to "have", "ter" to "have",
        "gosta" to "like", "gosto" to "like", "quer" to "want", "quero" to "want",
        "pode" to "can", "consegue" to "can", "sabe" to "know", "sei" to "know",
        "treinar" to "train", "treino" to "training", "batalha" to "battle",
        "batalhar" to "battle", "lutar" to "fight", "evoluir" to "evolve",
        "evolucao" to "evolution", "amigo" to "friend", "amiga" to "friend",
        "parceiro" to "partner", "parceira" to "partner", "mundo" to "world",
        "comer" to "eat", "bebida" to "drink", "brincar" to "play", "jogar" to "play",
        "dormir" to "sleep", "descansar" to "rest", "ajuda" to "help", "ajudar" to "help",
        "sim" to "yes", "nao" to "no", "nunca" to "never", "sempre" to "always",
        "bom" to "good", "boa" to "good", "ruim" to "bad", "legal" to "cool",
        "digivice" to "digivice", "digimon" to "digimon", "bits" to "bits",
    )
}
