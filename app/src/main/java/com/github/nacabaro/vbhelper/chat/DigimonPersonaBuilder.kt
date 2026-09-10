package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.domain.personality.toPromptDescription
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.dtos.CharacterDtos

object DigimonPersonaBuilder {
    val DEFAULT_SYSTEM_PROMPT_TEMPLATE: String
        get() = PromptLocalization.defaultSystemPrompt(PromptLocalization.currentLanguageTag())

    val DEFAULT_WILD_SYSTEM_PROMPT_TEMPLATE: String
        get() = PromptLocalization.defaultWildSystemPrompt(PromptLocalization.currentLanguageTag())

    fun buildSystemPrompt(
        character: CharacterDtos.CharacterWithSprites,
        cardName: String,
        speciesProfile: SpeciesProfile? = null,
        promptTemplate: String? = null,
        personality: DigimonPersonalityTraits? = null,
        tamerName: String = "",
        languageTag: String = PromptLocalization.currentLanguageTag(),
        defaultTemplate: (String) -> String = PromptLocalization::defaultSystemPrompt
    ): String {
        val speciesName = speciesProfile?.speciesName?.takeIf { it.isNotBlank() } ?: unknown(languageTag)
        val resolvedTamerName = tamerName.trim().ifBlank { tamer(languageTag) }
        val nickname = character.nickname?.takeIf { it.isNotBlank() }
        val digimonName = nickname?.let { "$it, ${species(languageTag)} $speciesName" } ?: speciesName
        val stageName = stageName(character.stage)
        val moodDescription = moodDescription(character.mood, languageTag)
        val speciesBlock = speciesBlock(speciesProfile, speciesName, stageName, languageTag)
        val personalityBlock = personality?.let {
            personalityBlock(it, languageTag)
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
            "{species_profile_block}" to speciesBlock,
            "{Tamer}" to resolvedTamerName
        )

        val template = promptTemplate?.takeIf { it.isNotBlank() }
            ?: defaultTemplate(languageTag)
        val customPrompt = replacements.entries.fold(template.trimIndent()) { prompt, (key, value) ->
            prompt.replace(key, value)
        }

        val gameplayContext = if (languageTag.startsWith("ja", ignoreCase = true)) {
            """
            時計の現在のコンテキスト（ユーザーは変更できません）:
            - カード: $cardName
            - 現在のステージ: $stageName（内部コード ${character.stage}）
            - バイタル: ${character.vitalPoints}
            - トロフィー: ${character.trophies}
            - 気分: ${character.mood}（$moodDescription）
            - 総勝利数: ${character.totalBattlesWon}、敗北数: ${character.totalBattlesLost}
            """.trimIndent()
        } else if (languageTag.startsWith("pt", ignoreCase = true)) {
            """
            Contexto atual do relógio (não é configurável pelo usuário):
            - Card: $cardName
            - Estágio atual: $stageName (código interno ${character.stage})
            - Vitais: ${character.vitalPoints}
            - Troféus: ${character.trophies}
            - Humor: ${character.mood} ($moodDescription)
            - Vitórias totais: ${character.totalBattlesWon}, Derrotas: ${character.totalBattlesLost}
            """.trimIndent()
        } else {
            """
            Current device context (not configurable by the user):
            - Card: $cardName
            - Current stage: $stageName (internal code ${character.stage})
            - Vitals: ${character.vitalPoints}
            - Trophies: ${character.trophies}
            - Mood: ${character.mood} ($moodDescription)
            - Total wins: ${character.totalBattlesWon}, losses: ${character.totalBattlesLost}
            """.trimIndent()
        }

        val identityContext = if (languageTag.startsWith("ja", ignoreCase = true)) {
            """
            デジモンのアイデンティティ:
            - 呼び名: $digimonName
            - 種族名: $speciesName
            - ニックネーム: ${nickname ?: "未設定"}
            - ステージ: $stageName
            - 性格: ${personalityBlock.ifBlank { "未生成" }}
            - 種族プロフィール: ${speciesProfile?.profileDescription?.takeIf { it.isNotBlank() } ?: "未設定"}
            テイマーのアイデンティティ:
            - 名前: $resolvedTamerName
            """.trimIndent()
        } else if (languageTag.startsWith("pt", ignoreCase = true)) {
            """
            Identidade do Digimon:
            - Nome usado para se referir a ele: $digimonName
            - Nome da espécie: $speciesName
            - Apelido: ${nickname ?: "não informado"}
            - Estágio: $stageName
            - Personalidade: ${personalityBlock.ifBlank { "não gerada" }}
            - Perfil da espécie: ${speciesProfile?.profileDescription?.takeIf { it.isNotBlank() } ?: "não informado"}
            Identidade do Tamer:
            - Nome: $resolvedTamerName
            """.trimIndent()
        } else {
            """
            Digimon identity:
            - Name used to refer to it: $digimonName
            - Species name: $speciesName
            - Nickname: ${nickname ?: "not provided"}
            - Stage: $stageName
            - Personality: ${personalityBlock.ifBlank { "not generated" }}
            - Species profile: ${speciesProfile?.profileDescription?.takeIf { it.isNotBlank() } ?: "not provided"}
            Tamer identity:
            - Name: $resolvedTamerName
            """.trimIndent()
        }

        return "$customPrompt\n\n$identityContext\n\n$gameplayContext"
    }

    private fun speciesBlock(
        profile: SpeciesProfile?,
        speciesName: String,
        stageName: String,
        languageTag: String
    ): String {
        if (profile == null) return ""
        val isJapanese = languageTag.startsWith("ja", ignoreCase = true)
        val isPortuguese = languageTag.startsWith("pt", ignoreCase = true)
        return buildString {
            append(
                when {
                    isJapanese -> "種族名: $speciesName、現在のステージ: $stageName"
                    isPortuguese -> "Nome da espécie: $speciesName, estágio atual: $stageName"
                    else -> "Species name: $speciesName, current stage: $stageName"
                }
            )
            profile.type?.let {
                append(
                    when {
                        isJapanese -> "、タイプ: $it"
                        isPortuguese -> ", tipo $it"
                        else -> ", type: $it"
                    }
                )
            }
            append(if (isJapanese) "。\n" else ".\n")
            profile.profileDescription?.takeIf { it.isNotBlank() }?.let {
                append(
                    when {
                        isJapanese -> "種族の一般プロフィール: $it\n"
                        isPortuguese -> "Perfil geral da espécie: $it\n"
                        else -> "General species profile: $it\n"
                    }
                )
            }
            if (profile.specialMoves.isNotEmpty()) {
                append(
                    when {
                        isJapanese -> "必殺技: ${profile.specialMoves.joinToString()}。\n"
                        isPortuguese -> "Golpes especiais: ${profile.specialMoves.joinToString()}.\n"
                        else -> "Special moves: ${profile.specialMoves.joinToString()}.\n"
                    }
                )
            }
        }.trim()
    }

    private fun personalityBlock(
        personality: DigimonPersonalityTraits,
        languageTag: String
    ): String {
        val labels = if (languageTag.startsWith("ja", ignoreCase = true)) {
            Triple("この個体独自の性格:", "気質", "社会的なスタイル")
        } else if (languageTag.startsWith("pt", ignoreCase = true)) {
            Triple("Personalidade única deste indivíduo:", "Temperamento", "Estilo social")
        } else {
            Triple("Unique personality of this individual:", "Temperament", "Social style")
        }
        return """
            ${labels.first}
            - ${labels.second}: ${personality.temperament.toPromptDescription()}
            - ${labels.third}: ${personality.socialStyle.toPromptDescription()}
            - ${if (languageTag.startsWith("ja", ignoreCase = true)) "話し方の特徴" else if (languageTag.startsWith("pt", ignoreCase = true)) "Jeito de falar" else "Speech quirk"}: ${personality.speechQuirk.toPromptDescription()}
        """.trimIndent()
    }

    private fun moodDescription(mood: Int, languageTag: String): String {
        val isJapanese = languageTag.startsWith("ja", ignoreCase = true)
        val isPortuguese = languageTag.startsWith("pt", ignoreCase = true)
        return when {
            mood >= 80 -> if (isJapanese) "とても嬉しく元気" else if (isPortuguese) "muito contente e cheio de energia" else "very happy and energetic"
            mood >= 50 -> if (isJapanese) "機嫌が良い" else if (isPortuguese) "de bom humor" else "in a good mood"
            mood >= 20 -> if (isJapanese) "少し疲れている" else if (isPortuguese) "um pouco cansado" else "a little tired"
            else -> if (isJapanese) "苛立っていて世話を必要としている" else if (isPortuguese) "mais irritado e precisando de atenção" else "irritated and in need of attention"
        }
    }

    private fun unknown(languageTag: String) =
        if (languageTag.startsWith("ja", ignoreCase = true)) "不明"
        else if (languageTag.startsWith("pt", ignoreCase = true)) "desconhecida"
        else "unknown"

    private fun tamer(languageTag: String) =
        if (languageTag.startsWith("ja", ignoreCase = true)) "あなたのテイマー"
        else if (languageTag.startsWith("pt", ignoreCase = true)) "seu Tamer"
        else "your Tamer"

    private fun species(languageTag: String) =
        if (languageTag.startsWith("ja", ignoreCase = true)) "種族"
        else if (languageTag.startsWith("pt", ignoreCase = true)) "da espécie"
        else "of the species"

    private fun stageName(stage: Int): String = when (stage) {
        0 -> "Baby I"
        1 -> "Baby II"
        2 -> "Child (Rookie)"
        3 -> "Adult (Champion)"
        4 -> "Perfect (Ultimate)"
        5 -> "Ultimate (Mega)"
        else -> "unknown stage ($stage)"
    }
}
