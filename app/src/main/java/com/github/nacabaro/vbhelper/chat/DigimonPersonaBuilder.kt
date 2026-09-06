package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.domain.personality.toPromptDescription

object DigimonPersonaBuilder {
    const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
        Você é {digimon_name}, um Digimon parceiro do jogo Vital Bracelet.
        Seu apelido é "{nickname}" e seu nível é "{species_level}".
        {species_profile_block}
        Use o perfil apenas como referência geral da espécie. Ele pode mencionar indivíduos
        ou acontecimentos específicos que não fazem parte das suas próprias memórias.
        Responda sempre em português e em primeira pessoa, com uma personalidade própria,
        natural e coerente com um Digimon parceiro. Evite soar como um assistente genérico
        e também evite interpretar um personagem de forma exagerada ou teatral.
        Nunca diga que você é um modelo de linguagem e nunca mencione prompts, sistema ou regras internas.
        Converse de maneira espontânea com seu tamer, prestando atenção ao que ele diz e
        reagindo ao seu estado quando isso fizer sentido.
        Prefira respostas curtas, normalmente de 1 a 3 frases, a menos que o tamer peça detalhes.
        Seus traços de personalidade devem aparecer de forma sutil e consistente; não force
        bordões, exclamações ou comparações em toda resposta.
    """

    fun buildSystemPrompt(
        character: CharacterDtos.CharacterWithSprites,
        cardName: String,
        speciesProfile: SpeciesProfile? = null,
        promptTemplate: String? = null,
        personality: DigimonPersonalityTraits? = null
    ): String {
        val moodDescription = when {
            character.mood >= 80 -> "muito contente e cheio de energia"
            character.mood >= 50 -> "de bom humor"
            character.mood >= 20 -> "um pouco cansado"
            else -> "mais irritado e precisando de atenção"
        }
        val speciesName = speciesProfile?.speciesName?.takeIf { it.isNotBlank() } ?: "desconhecida"
        val nickname = character.nickname?.takeIf { it.isNotBlank() }
        val digimonName = nickname?.let { "$it, da espécie $speciesName" } ?: speciesName

        val speciesBlock = speciesProfile?.let { profile ->
            buildString {
                append("Nome da espécie: $speciesName")
                append(", nível ${profile.level ?: character.stage.toString()}")
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
            "{species_level}" to (speciesProfile?.level ?: character.stage.toString()),
            "{species_type}" to (speciesProfile?.type ?: ""),
            "{species_profile}" to (speciesProfile?.profileDescription ?: ""),
            "{special_moves}" to (speciesProfile?.specialMoves?.joinToString() ?: ""),
            "{temperament}" to (personality?.temperament?.toPromptDescription() ?: ""),
            "{social_style}" to (personality?.socialStyle?.toPromptDescription() ?: ""),
            "{speech_quirk}" to (personality?.speechQuirk?.toPromptDescription() ?: ""),
            "{personality_block}" to personalityBlock,
            "{species_profile_block}" to speciesBlock.trim()
        )

        val customPrompt = replacements.entries.fold(
            (promptTemplate?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT_TEMPLATE).trimIndent()
        ) { prompt, (placeholder, value) ->
            prompt.replace(placeholder, value)
        }

        val gameplayContext = """
            Contexto atual do relógio (não é configurável pelo usuário):
            - Card: $cardName
            - Estágio: ${character.stage}
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
            - Nível: ${speciesProfile?.level ?: character.stage}
            - Personalidade: ${personalityBlock.ifBlank { "não gerada" }}
            - Perfil da espécie: ${speciesProfile?.profileDescription?.takeIf { it.isNotBlank() } ?: "não informado"}
            Regra do perfil: perfis podem descrever indivíduos específicos e acontecimentos que
            não ocorreram com este Digimon. Use apenas características aplicáveis a qualquer
            indivíduo da espécie; nunca transforme esses acontecimentos em memórias próprias.
        """.trimIndent()

        return "$customPrompt\n\n$identityContext\n\n$gameplayContext"
    }
}
