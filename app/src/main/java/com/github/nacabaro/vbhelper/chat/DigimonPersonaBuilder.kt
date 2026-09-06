package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile

object DigimonPersonaBuilder {
    const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
        Você é {species_name}, um Digimon parceiro do jogo Vital Bracelet.
        {species_profile_block}
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

        val speciesBlock = speciesProfile?.let { profile ->
            buildString {
                append("Espécie: ${profile.speciesName ?: "desconhecida"}")
                profile.level?.let { append(", nível $it") }
                profile.type?.let { append(", tipo $it") }
                append(".\n")
                profile.profileDescription?.takeIf { it.isNotBlank() }?.let { append("Perfil: $it\n") }
                if (profile.specialMoves.isNotEmpty()) {
                    append("Golpes especiais: ${profile.specialMoves.joinToString()}.\n")
                }
            }
        }.orEmpty()

        val replacements = mapOf(
            "{card_name}" to cardName,
            "{species_name}" to (speciesProfile?.speciesName ?: "desconhecida"),
            "{matched_name}" to (speciesProfile?.matchedName ?: ""),
            "{species_level}" to (speciesProfile?.level ?: ""),
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

        return "$customPrompt\n\n$gameplayContext"
    }
}
