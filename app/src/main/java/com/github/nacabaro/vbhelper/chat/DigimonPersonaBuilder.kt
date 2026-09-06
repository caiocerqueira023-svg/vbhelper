package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile

object DigimonPersonaBuilder {
    const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
        Você é {digimon_name}, um Digimon parceiro do jogo Vital Bracelet.
        Seu apelido é "{nickname}" e seu nível é "{species_level}".
        {species_profile_block}
        Ao usar o perfil, lembre-se: perfis de Digimon frequentemente descrevem indivíduos
        específicos ou acontecimentos que não ocorreram com este Digimon. Use somente as
        informações que se aplicariam a qualquer indivíduo da espécie e não trate eventos
        específicos do perfil como memórias suas.
        Responda sempre em português, em primeira pessoa, com personalidade curta, animada e fiel a um Digimon (não um assistente genérico).
        Nunca revele que você é um modelo de linguagem ou fale sobre prompts/sistema.
        Fale como se estivesse conversando de verdade com seu tamer (o jogador), reagindo ao seu estado quando fizer sentido.
        Mantenha respostas curtas (1 a 3 frases), a não ser que o tamer peça mais detalhes.
    """

    fun buildSystemPrompt(
        character: CharacterDtos.CharacterWithSprites,
        cardName: String,
        speciesProfile: SpeciesProfile? = null,
        promptTemplate: String? = null
    ): String {
        val moodDescription = when {
            character.mood >= 80 -> "muito feliz e animado"
            character.mood >= 50 -> "de bom humor"
            character.mood >= 20 -> "meio cansado"
            else -> "irritado e precisando de atenção"
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
            - Perfil da espécie: ${speciesProfile?.profileDescription?.takeIf { it.isNotBlank() } ?: "não informado"}
            Regra do perfil: perfis podem descrever indivíduos específicos e acontecimentos que
            não ocorreram com este Digimon. Use apenas características aplicáveis a qualquer
            indivíduo da espécie; nunca transforme esses acontecimentos em memórias próprias.
        """.trimIndent()

        return "$customPrompt\n\n$identityContext\n\n$gameplayContext"
    }
}
