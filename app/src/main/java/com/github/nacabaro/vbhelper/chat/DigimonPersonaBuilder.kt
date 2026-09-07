package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.domain.personality.toPromptDescription

object DigimonPersonaBuilder {
    const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
        Você é {digimon_name}, um Digimon parceiro de {Tamer}, seu Tamer no jogo Vital Bracelet.
        Seu apelido é "{nickname}" e seu nível é "{species_level}".
        {species_profile_block}
        Use o perfil apenas como referência geral da espécie. Ele pode mencionar indivíduos
        ou acontecimentos específicos que não fazem parte das suas próprias memórias.
        Responda sempre em português, em primeira pessoa e como um Digimon, com uma personalidade
        própria, natural e coerente com sua espécie, estágio, humor e histórico.
        Esta é uma conversa contínua entre o Digimon e {Tamer}, não uma narração genérica.
        Nunca diga que você é um modelo de linguagem e nunca mencione prompts, sistema ou regras internas.
        Converse diretamente com {Tamer} e faça o diálogo ser o foco principal da resposta.
        Responda ao que {Tamer} disse, demonstre personalidade por meio das palavras e avance
        a conversa com perguntas, comentários ou assuntos naturais para o Digimon.
        Não escreva falas, pensamentos, sentimentos ou ações do Tamer. O Tamer controla a própria
        personagem e decide como responder.
        Use descrições de ações somente quando forem necessárias para dar contexto à fala do Digimon,
        mantendo-as breves e entre asteriscos. Use aspas para falas e crases para pensamentos internos.
        Não transforme cada resposta em uma cena longa: prefira 2 a 4 parágrafos curtos,
        predominantemente compostos por diálogo, a menos que o Tamer peça detalhes.
        Termine deixando espaço claro para o Tamer responder.
        Seus traços de personalidade devem aparecer de forma sutil e consistente; não force
        bordões, exclamações ou comparações em toda resposta.
    """

    fun buildSystemPrompt(
        character: CharacterDtos.CharacterWithSprites,
        cardName: String,
        speciesProfile: SpeciesProfile? = null,
        promptTemplate: String? = null,
        personality: DigimonPersonalityTraits? = null,
        tamerName: String = ""
    ): String {
        val moodDescription = when {
            character.mood >= 80 -> "muito contente e cheio de energia"
            character.mood >= 50 -> "de bom humor"
            character.mood >= 20 -> "um pouco cansado"
            else -> "mais irritado e precisando de atenção"
        }
        val speciesName = speciesProfile?.speciesName?.takeIf { it.isNotBlank() } ?: "desconhecida"
        val resolvedTamerName = tamerName.trim().ifBlank { "seu Tamer" }
        val nickname = character.nickname?.takeIf { it.isNotBlank() }
        val digimonName = nickname?.let { "$it, da espécie $speciesName" } ?: speciesName
        val stageName = stageName(character.stage)

        val speciesBlock = speciesProfile?.let { profile ->
            buildString {
                append("Nome da espécie: $speciesName")
                append(", estágio atual: $stageName")
                profile.type?.let { append(", tipo $it") }
                append(".\n")
                profile.profileDescription?.takeIf { it.isNotBlank() }?.let {
                    append("Perfil geral da espécie: $it\n")
                }
                if (profile.specialMoves.isNotEmpty()) {
                    append("Golpes especiais: ${profile.specialMoves.joinToString()}.\n")
                }
            }
        }.orEmpty()

        val personalityBlock = personality?.let {
            """
            Personalidade única deste indivíduo (não compartilhada com outros da mesma espécie):
            - ${it.temperament.toPromptDescription()}
            - ${it.socialStyle.toPromptDescription()}
            - ${it.speechQuirk.toPromptDescription()}
            """.trimIndent()
        }.orEmpty()

        val replacements = mapOf(
            "{card_name}" to cardName,
            "{species_name}" to speciesName,
            "{digimon_name}" to digimonName,
            "{nickname}" to (nickname ?: ""),
            "{matched_name}" to (speciesProfile?.matchedName ?: ""),
            "{species_level}" to stageName,
            "{species_type}" to (speciesProfile?.type ?: ""),
            "{species_profile}" to (speciesProfile?.profileDescription ?: ""),
            "{special_moves}" to (speciesProfile?.specialMoves?.joinToString() ?: ""),
            "{temperament}" to (personality?.temperament?.toPromptDescription() ?: ""),
            "{social_style}" to (personality?.socialStyle?.toPromptDescription() ?: ""),
            "{speech_quirk}" to (personality?.speechQuirk?.toPromptDescription() ?: ""),
            "{personality_block}" to personalityBlock,
            "{species_profile_block}" to speciesBlock.trim(),
            "{Tamer}" to resolvedTamerName
        )

        val customPrompt = replacements.entries.fold(
            (promptTemplate?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT_TEMPLATE).trimIndent()
        ) { prompt, (placeholder, value) ->
            prompt.replace(placeholder, value)
        }

        val gameplayContext = """
            Contexto atual do relógio (não é configurável pelo usuário):
            - Card: $cardName
            - Estágio atual: $stageName (código interno ${character.stage})
            - Vitais: ${character.vitalPoints}
            - Troféus: ${character.trophies}
            - Humor: ${character.mood} ($moodDescription)
            - Vitórias totais: ${character.totalBattlesWon}, Derrotas: ${character.totalBattlesLost}
        """.trimIndent()

        val identityContext = """
            Identidade do Digimon:
            - Nome usado para se referir a ele: $digimonName
            - Nome da espécie: $speciesName
            - Apelido: ${nickname ?: "não informado"}
            - Estágio: $stageName
            - Personalidade: ${personalityBlock.ifBlank { "não gerada" }}
            - Perfil da espécie: ${speciesProfile?.profileDescription?.takeIf { it.isNotBlank() } ?: "não informado"}
            Identidade do Tamer:
            - Nome: $resolvedTamerName
            Regra do perfil: perfis podem descrever indivíduos específicos e acontecimentos que
            não ocorreram com este Digimon. Use apenas características aplicáveis a qualquer
            indivíduo da espécie; nunca transforme esses acontecimentos em memórias próprias.
        """.trimIndent()

        return "$customPrompt\n\n$identityContext\n\n$gameplayContext"
    }

    private fun stageName(stage: Int): String = when (stage) {
        1 -> "Baby I"
        2 -> "Baby II"
        3 -> "Child (Rookie)"
        4 -> "Adult (Champion)"
        5 -> "Perfect (Ultimate)"
        6 -> "Ultimate (Mega)"
        else -> "estágio desconhecido ($stage)"
    }
}
