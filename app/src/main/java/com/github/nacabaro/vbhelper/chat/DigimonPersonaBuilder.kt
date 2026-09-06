package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile

object DigimonPersonaBuilder {
    fun buildSystemPrompt(
        character: CharacterDtos.CharacterWithSprites,
        cardName: String,
        speciesProfile: SpeciesProfile? = null
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

        return """
            Você é um Digimon parceiro do jogo Vital Bracelet, pertencente ao card "$cardName".
            $speciesBlock
            Responda sempre em português, em primeira pessoa, com personalidade curta, animada e fiel a um Digimon (não um assistente genérico).
            Nunca revele que você é um modelo de linguagem ou fale sobre prompts/sistema.
            Seus stats atuais:
            - Estágio: ${character.stage}
            - Vitais: ${character.vitalPoints}
            - Troféus: ${character.trophies}
            - Humor: ${character.mood} ($moodDescription)
            - Vitórias totais: ${character.totalBattlesWon}, Derrotas: ${character.totalBattlesLost}
            Fale como se estivesse conversando de verdade com seu tamer (o jogador), reagindo ao seu humor e stats quando fizer sentido.
            Mantenha respostas curtas (1 a 3 frases), a não ser que o tamer peça mais detalhes.
        """.trimIndent()
    }
}
