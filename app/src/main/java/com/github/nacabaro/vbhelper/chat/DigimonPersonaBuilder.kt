package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.species.SpeciesConversationEntry

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
        defaultTemplate: (String) -> String = PromptLocalization::defaultSystemPrompt,
        conversationExamples: List<SpeciesConversationEntry> = emptyList(),
        evolutionHistory: List<CharacterDtos.EvolutionHistoryPromptEntry> = emptyList()
    ): String {
        val speciesName = speciesProfile?.speciesName?.takeIf { it.isNotBlank() } ?: unknown(languageTag)
        val resolvedTamerName = tamerName.trim().ifBlank { tamer(languageTag) }
        val nickname = character.nickname?.takeIf { it.isNotBlank() }
        val digimonName = nickname?.let { "$it, ${species(languageTag)} $speciesName" } ?: speciesName
        val stageName = stageName(character.stage)
        val moodDescription = moodDescription(character.mood, languageTag)
        val resolvedPersonality = personality?.personalityType ?: DigimonPersonalityType.FRIENDLY
        val personalityBlock = personalityBlock(resolvedPersonality, personality != null, languageTag)
        val speciesBlock = speciesBlock(
            profile = speciesProfile,
            speciesName = speciesName,
            stageName = stageName,
            personalityBlock = personalityBlock,
            languageTag = languageTag
        )
        val examplesBlock = conversationExamplesBlock(conversationExamples, languageTag)
        val historyBlock = evolutionHistoryBlock(evolutionHistory, languageTag)
        val personalityTypeName = resolvedPersonality.displayName(languageTag)

        val replacements = mapOf(
            "{card_name}" to cardName,
            "{species_name}" to speciesName,
            "{digimon_name}" to digimonName,
            "{nickname}" to (nickname ?: ""),
            "{matched_name}" to (speciesProfile?.matchedName ?: ""),
            "{profile_level}" to (speciesProfile?.level ?: ""),
            "{species_level}" to stageName,
            "{species_type}" to (speciesProfile?.type ?: ""),
            "{species_profile}" to (speciesProfile?.profileDescription ?: ""),
            "{special_moves}" to (speciesProfile?.specialMoves?.joinToString() ?: ""),
            "{personality_type}" to personalityTypeName,
            "{personality_block}" to personalityBlock,
            "{species_profile_block}" to speciesBlock,
            "{conversation_examples}" to examplesBlock,
            "{evolution_history}" to historyBlock,
            // These aliases keep already-saved user templates usable while
            // resolving them to the new single personality type system.
            "{temperament}" to personalityTypeName,
            "{social_style}" to personalityTypeName,
            "{speech_quirk}" to personalityTypeName,
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
            - 性格タイプ: $personalityTypeName
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
            - Tipo de personalidade: $personalityTypeName
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
            - Personality type: $personalityTypeName
            - Species profile: ${speciesProfile?.profileDescription?.takeIf { it.isNotBlank() } ?: "not provided"}
            Tamer identity:
            - Name: $resolvedTamerName
            """.trimIndent()
        }

        val supplementalContext = buildString {
            if (!template.contains("{species_profile_block}")) {
                append("\n\n")
                append(
                    supplementalSpeciesContext(
                        profile = speciesProfile,
                        speciesName = speciesName,
                        stageName = stageName,
                        template = template,
                        languageTag = languageTag
                    )
                )
            }
            if (!template.contains("{species_profile_block}") &&
                !template.contains("{personality_block}")) {
                append("\n\n")
                append(personalityBlock)
            }
            if (examplesBlock.isNotBlank() && !template.contains("{conversation_examples}")) {
                append("\n\n")
                append(examplesBlock)
            }
            if (historyBlock.isNotBlank() && !template.contains("{evolution_history}")) {
                append("\n\n")
                append(historyBlock)
            }
        }
        return "$customPrompt\n\n$identityContext\n\n$gameplayContext$supplementalContext"
    }

    private fun speciesBlock(
        profile: SpeciesProfile?,
        speciesName: String,
        stageName: String,
        personalityBlock: String,
        languageTag: String
    ): String {
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
            profile?.type?.let {
                append(
                    when {
                        isJapanese -> "、タイプ: $it"
                        isPortuguese -> ", tipo $it"
                        else -> ", type: $it"
                    }
                )
            }
            append(if (isJapanese) "。\n" else ".\n")
            profile?.profileDescription?.takeIf { it.isNotBlank() }?.let {
                append(
                    when {
                        isJapanese -> "種族の一般プロフィール: $it\n"
                        isPortuguese -> "Perfil geral da espécie: $it\n"
                        else -> "General species profile: $it\n"
                    }
                )
            }
            profile?.matchedName?.takeIf {
                it.isNotBlank() && !it.equals(speciesName, ignoreCase = true)
            }?.let {
                append(
                    when {
                        isJapanese -> "公式データベースで一致した名前: $it\n"
                        isPortuguese -> "Nome correspondente no banco oficial: $it\n"
                        else -> "Official database match: $it\n"
                    }
                )
            }
            profile?.level?.takeIf { it.isNotBlank() }?.let {
                append(
                    when {
                        isJapanese -> "プロフィール上のレベル: $it\n"
                        isPortuguese -> "Nível informado no perfil: $it\n"
                        else -> "Profile level: $it\n"
                    }
                )
            }
            if (profile?.specialMoves?.isNotEmpty() == true) {
                append(
                    when {
                        isJapanese -> "必殺技: ${profile.specialMoves.joinToString()}。\n"
                        isPortuguese -> "Golpes especiais: ${profile.specialMoves.joinToString()}.\n"
                        else -> "Special moves: ${profile.specialMoves.joinToString()}.\n"
                    }
                )
            }
            if (profile != null) {
                append(profileApplicationRule(languageTag))
                append('\n')
            }
            personalityBlock.takeIf { it.isNotBlank() }?.let {
                append(it)
                append('\n')
            }
        }.trim()
    }

    private fun supplementalSpeciesContext(
        profile: SpeciesProfile?,
        speciesName: String,
        stageName: String,
        template: String,
        languageTag: String
    ): String {
        if (profile == null) {
            return speciesBlock(
                profile = null,
                speciesName = speciesName,
                stageName = stageName,
                personalityBlock = "",
                languageTag = languageTag
            )
        }

        val isJapanese = languageTag.startsWith("ja", ignoreCase = true)
        val isPortuguese = languageTag.startsWith("pt", ignoreCase = true)
        return buildString {
            append(
                when {
                    isJapanese -> "この個体に適用する完全な種族プロフィール情報:"
                    isPortuguese -> "Informações completas do perfil da espécie a aplicar a este indivíduo:"
                    else -> "Complete species-profile information to apply to this individual:"
                }
            )
            append('\n')
            if (!template.contains("{species_name}")) {
                append(
                    when {
                        isJapanese -> "- 種族名: $speciesName\n"
                        isPortuguese -> "- Nome da espécie: $speciesName\n"
                        else -> "- Species name: $speciesName\n"
                    }
                )
            }
            if (!template.contains("{species_level}")) {
                append(
                    when {
                        isJapanese -> "- 現在のステージ: $stageName\n"
                        isPortuguese -> "- Estágio atual: $stageName\n"
                        else -> "- Current stage: $stageName\n"
                    }
                )
            }
            if (!template.contains("{matched_name}")) {
                profile.matchedName?.takeIf {
                    it.isNotBlank() && !it.equals(speciesName, ignoreCase = true)
                }?.let {
                    append(
                        when {
                            isJapanese -> "- 公式データベースで一致した名前: $it\n"
                            isPortuguese -> "- Nome correspondente no banco oficial: $it\n"
                            else -> "- Official database match: $it\n"
                        }
                    )
                }
            }
            if (!template.contains("{profile_level}")) {
                profile.level?.takeIf { it.isNotBlank() }?.let {
                    append(
                        when {
                            isJapanese -> "- プロフィール上のレベル: $it\n"
                            isPortuguese -> "- Nível informado no perfil: $it\n"
                            else -> "- Profile level: $it\n"
                        }
                    )
                }
            }
            if (!template.contains("{species_type}")) {
                profile.type?.takeIf { it.isNotBlank() }?.let {
                    append(
                        when {
                            isJapanese -> "- タイプ: $it\n"
                            isPortuguese -> "- Tipo: $it\n"
                            else -> "- Type: $it\n"
                        }
                    )
                }
            }
            if (!template.contains("{species_profile}")) {
                profile.profileDescription?.takeIf { it.isNotBlank() }?.let {
                    append(
                        when {
                            isJapanese -> "- 種族の一般プロフィール: $it\n"
                            isPortuguese -> "- Perfil geral da espécie: $it\n"
                            else -> "- General species profile: $it\n"
                        }
                    )
                }
            }
            if (!template.contains("{special_moves}") && profile.specialMoves.isNotEmpty()) {
                append(
                    when {
                        isJapanese -> "- 必殺技: ${profile.specialMoves.joinToString()}\n"
                        isPortuguese -> "- Golpes especiais: ${profile.specialMoves.joinToString()}\n"
                        else -> "- Special moves: ${profile.specialMoves.joinToString()}\n"
                    }
                )
            }
            append(profileApplicationRule(languageTag))
        }.trim()
    }

    private fun profileApplicationRule(languageTag: String): String {
        val isJapanese = languageTag.startsWith("ja", ignoreCase = true)
        val isPortuguese = languageTag.startsWith("pt", ignoreCase = true)
        return when {
            isJapanese -> "プロフィール適用の必須規則: 通常のメッセージでもリアクションでも、返答を組み立てる前にこのプロフィールのすべての情報を考慮してください。関連する時だけ選択的に使い、知覚、言葉選び、優先順位、判断、感情、行動を自然に形作ってください。無関係な詳細を無理に出さず、プロフィールを読み上げたり事実を羅列したりせず、種族の事実を個人的な記憶、実績、経験として扱わないでください。プロフィールと会話に裏付けられない身体、能力、記憶、経験を作らないでください。"
            isPortuguese -> "Regra obrigatória de aplicação do perfil: antes de construir cada mensagem normal ou reação, leve em consideração todas as informações deste perfil. Use-as seletivamente quando forem pertinentes para orientar percepção, vocabulário, prioridades, decisões, emoções e ações de forma natural. Não force detalhes irrelevantes, não recite o perfil nem despeje fatos, e não trate fatos da espécie como memórias, feitos ou experiências pessoais. Nunca invente anatomia, habilidades, memórias ou experiências que o perfil e a conversa não sustentem."
            else -> "Mandatory profile-use rule: before constructing every ordinary message or reaction, consider every piece of information in this profile. Use it selectively when relevant to shape perception, wording, priorities, decisions, emotions, and actions naturally. Do not force irrelevant details, recite the profile, or dump facts, and do not treat species facts as personal memories, accomplishments, or experiences. Never invent anatomy, abilities, memories, or experiences unsupported by the profile and conversation."
        }
    }

    private fun personalityBlock(
        type: DigimonPersonalityType,
        wasAssigned: Boolean,
        languageTag: String
    ): String {
        val isJapanese = languageTag.startsWith("ja", ignoreCase = true)
        val isPortuguese = languageTag.startsWith("pt", ignoreCase = true)
        val title = when {
            isJapanese -> "この個体に必ず適用する性格指示:"
            isPortuguese -> "Instruções obrigatórias de personalidade deste indivíduo:"
            else -> "Mandatory personality instructions for this individual:"
        }
        val typeLabel = when {
            isJapanese -> "性格タイプ"
            isPortuguese -> "Tipo"
            else -> "Type"
        }
        val instructionLabel = when {
            isJapanese -> "実行する行動規則"
            isPortuguese -> "Regras de comportamento a executar"
            else -> "Behavior rules to execute"
        }
        val bindingRule = when {
            isJapanese -> "これは説明用のプロフィール情報ではありません。通常の返答とリアクションの言葉選び、優先順位、判断、感情表現に自然に反映し、性格名や規則自体を口にしないでください。"
            isPortuguese -> "Isto não é uma sugestão nem uma informação para recitar. Execute estas regras naturalmente nas falas e reações: elas devem mudar escolhas de palavras, prioridades, decisões e expressão emocional sem mencionar o tipo ou as regras."
            else -> "This is not an optional suggestion or information to recite. Execute these rules naturally in ordinary replies and reactions: they must shape word choice, priorities, decisions and emotional expression without naming the type or the rules."
        }
        val assignmentNote = if (wasAssigned) "" else when {
            isJapanese -> "（保存された性格がない場合の安全な既定値）"
            isPortuguese -> " (valor padrão usado quando o indivíduo ainda não tinha personalidade salva)"
            else -> " (safe default used when this individual had no saved personality)"
        }
        return """
            $title
            - $typeLabel: ${type.displayName(languageTag)}$assignmentNote
            - $instructionLabel: ${type.promptInstruction(languageTag)}
            - $bindingRule
        """.trimIndent()
    }

    private fun conversationExamplesBlock(
        entries: List<SpeciesConversationEntry>,
        languageTag: String
    ): String {
        if (entries.isEmpty()) return ""
        val isJapanese = languageTag.startsWith("ja", ignoreCase = true)
        val isPortuguese = languageTag.startsWith("pt", ignoreCase = true)
        val title = when {
            isJapanese -> "種族別の会話例（現在の返答の参考）:"
            isPortuguese -> "Exemplos de conversa desta espécie (referência para a resposta atual):"
            else -> "Species conversation examples (reference for the current reply):"
        }
        val instruction = when {
            isJapanese -> "これらは会話のリズムと反応の参考です。逐語的にコピーせず、現在の発言、個体の性格、プロフィールに合う場合だけ応用してください。テイマーの台詞を代弁しないでください。"
            isPortuguese -> "Use estes exemplos apenas como referência de ritmo, tom e possibilidades de reação. Não copie falas literalmente nem trate-as como memórias; adapte-as somente quando combinarem com a mensagem atual, a personalidade individual e o perfil. Nunca fale pelo Tamer."
            else -> "Use these only as references for rhythm, tone and possible reactions. Do not copy lines literally or treat them as memories; adapt them only when they fit the current message, the individual personality and the profile. Never speak for the Tamer."
        }
        return buildString {
            append(title)
            append('\n')
            entries.take(4).forEachIndexed { entryIndex, entry ->
                entry.opening?.takeIf { it.isNotBlank() }?.let {
                    append("- Digimon opening ${entryIndex + 1}: \"")
                    append(it.replace('\n', ' ').trim())
                    append("\"\n")
                }
                entry.exchanges.take(4).forEach { exchange ->
                    if (exchange.tamer.isNotBlank()) {
                        append("  Tamer: \"")
                        append(exchange.tamer.replace('\n', ' ').trim())
                        append("\"\n")
                    }
                    if (exchange.digimon.isNotBlank()) {
                        append("  Digimon: \"")
                        append(exchange.digimon.replace('\n', ' ').trim())
                        append("\"\n")
                    }
                }
            }
            append(instruction)
        }.trim()
    }

    private fun evolutionHistoryBlock(
        history: List<CharacterDtos.EvolutionHistoryPromptEntry>,
        languageTag: String
    ): String {
        if (history.isEmpty()) return ""
        val isJapanese = languageTag.startsWith("ja", ignoreCase = true)
        val isPortuguese = languageTag.startsWith("pt", ignoreCase = true)
        val title = when {
            isJapanese -> "この個体が実際に通過した進化履歴:"
            isPortuguese -> "Histórico real de evolução deste indivíduo:"
            else -> "This individual's real evolution history:"
        }
        val instruction = when {
            isJapanese -> "この一覧は知っている形態の順序です。必要な時だけ自然に参照し、一覧にない出来事、記憶、経験を作らないでください。"
            isPortuguese -> "A lista informa as formas que este indivíduo realmente teve. Consulte-a naturalmente apenas quando for relevante; não invente acontecimentos, memórias ou experiências que ela não sustente."
            else -> "This list records forms this individual actually had. Refer to it naturally only when relevant; do not invent events, memories or experiences that it does not support."
        }
        return buildString {
            append(title)
            append('\n')
            history.forEachIndexed { index, entry ->
                val speciesName = entry.matchedName?.takeIf { it.isNotBlank() }
                    ?: entry.speciesName?.takeIf { it.isNotBlank() }
                    ?: stageName(entry.stage)
                append("${index + 1}. $speciesName (${stageName(entry.stage)})\n")
            }
            append(instruction)
        }.trim()
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
