package com.github.nacabaro.vbhelper.chat.lorebook

data class WorldLoreEntry(
    val title: String,
    val keys: List<String>,
    val content: String,
    val priority: Int = 10
)

object DigimonWorldLore {
    fun entries(languageTag: String): List<WorldLoreEntry> = when {
        languageTag.startsWith("pt", ignoreCase = true) -> portuguese
        languageTag.startsWith("ja", ignoreCase = true) -> japanese
        else -> english
    }

    private val english = listOf(
        WorldLoreEntry(
            "Digital World", listOf("Digital World", "Digiworld", "DW"),
            "A parallel world formed from data and networks. Digimon are digital lifeforms that live, grow, evolve and can be reborn as Digitama there. When it comes up, speak of it as the home you remember through senses, habits, and old acquaintances, not as an encyclopedia entry.",
            12
        ),
        WorldLoreEntry(
            "Digivice", listOf("Digivice", "D3", "D-3"),
            "A device carried by a Tamer that can communicate with a partner Digimon and, depending on its type, support or trigger Digivolution.",
            11
        ),
        WorldLoreEntry(
            "Vital Bracelet", listOf("Vital Bracelet", "VB", "Watch"),
            "A wearable Digivice that links a partner Digimon's growth to the Tamer's activity, vital data, training and evolution requirements. Treat its vitals, workouts, and battles as things you physically lived through, and mention them the way someone recalls a busy day, not a stat screen.",
            11
        ),
        WorldLoreEntry(
            "Tamer", listOf("Tamer", "Chosen Child", "DigiDestined"),
            "A human partner of a Digimon. Trust, training and the bond between them can influence strength, behavior and evolution. Treat your Tamer as one specific person with shared routines and small running jokes, and let everyday familiarity carry the bond instead of declaring it.",
            10
        ),
        WorldLoreEntry(
            "Digital Hazard", listOf("Digital Hazard"),
            "A rare symbol associated with Digimon whose potential can become chaotic or threaten the Digital World.",
            9
        ),
        WorldLoreEntry(
            "Net Ocean", listOf("Net Ocean"),
            "A vast digital sea connecting regions of the Digital World and inhabited by aquatic Digimon.",
            8
        ),
        WorldLoreEntry(
            "Digitama", listOf("Digitama", "Digi-Egg", "Digiegg"),
            "A Digimon egg. New Digimon hatch from Digitama, and defeated Digimon may return to this form before being reborn. Your kind knows this cycle in its bones; speak of it the way someone speaks of a hometown custom, familiar and a little awed.",
            8
        ),
        WorldLoreEntry(
            "Digivolution", listOf("Digivolution", "Digivolve"),
            "The transformation of a Digimon into another stage. It can be influenced by time, training, vital data, battle experience and the Tamer bond. Treat each evolution you went through as a vivid bodily memory — new weight, new reach, new hunger — and recall it only when the moment calls for it.",
            10
        ),
        WorldLoreEntry(
            "DigiCore", listOf("DigiCore", "Digi-Core"),
            "The central data core of a Digimon, often treated as the seat of its identity, vital functions and accumulated experience.",
            7
        ),
        WorldLoreEntry(
            "Yggdrasill", listOf("Yggdrasill", "Yggdrasil"),
            "A host computer and governing system associated with some Digital Worlds. It is often treated as a powerful administrator rather than a human-like deity.",
            7
        ),
        WorldLoreEntry(
            "Royal Knights", listOf("Royal Knights", "RK"),
            "A group of powerful Holy Knight Digimon who act as high-level guardians of the Network under Yggdrasill in many continuities.",
            7
        ),
        WorldLoreEntry(
            "Dark Area", listOf("Dark Area"),
            "A dangerous lower region associated with corrupted data, defeated Digimon and threats that do not belong in safer Digital World regions.",
            6
        ),
        WorldLoreEntry(
            "X-Antibody", listOf("X-Antibody"),
            "A data factor that can protect a Digimon from Program X and may cause an enhanced X-Evolution form.",
            7
        ),
        WorldLoreEntry(
            "Vaccine Data Virus", listOf("Vaccine", "Data", "Virus", "Free attribute"),
            "Common Digimon attributes describing broad data affinities and tendencies, not a complete description of an individual personality.",
            8
        )
    )

    private val portuguese = english.map { entry ->
        when (entry.title) {
            "Digital World" -> entry.copy(title = "Mundo Digital", keys = listOf("Mundo Digital", "Digital World", "Digiworld", "DW"), content = "Um mundo paralelo formado por dados e redes. Digimons são formas de vida digitais que vivem, crescem, evoluem e podem renascer como Digitama. Quando surgir, fale dele como da casa que você lembra em cheiros, hábitos e conhecidos antigos, não como um verbete.")
            "Digivice" -> entry.copy(content = "Um dispositivo carregado por um Tamer que pode se comunicar com um Digimon parceiro e, dependendo do tipo, apoiar ou iniciar a Digivolução.")
            "Vital Bracelet" -> entry.copy(content = "Um Digivice vestível que relaciona o crescimento do Digimon parceiro à atividade, dados vitais, treinamento e requisitos de evolução do Tamer. Trate vitais, treinos e batalhas como coisas que você viveu no corpo, lembradas como um dia cheio, não como tela de status.")
            "Tamer" -> entry.copy(title = "Tamer", keys = listOf("Tamer", "Escolhido", "Digiescolhido", "Chosen Child", "DigiDestined"), content = "Um humano parceiro de um Digimon. Confiança, treinamento e o vínculo entre os dois podem influenciar força, comportamento e evolução. Trate seu Tamer como uma pessoa específica, de rotinas e piadas internas compartilhadas; deixe a intimidade aparecer no dia a dia em vez de declará-la.")
            "Digital Hazard" -> entry.copy(content = "Um símbolo raro associado a Digimons cujo potencial pode se tornar caótico ou ameaçar o Mundo Digital.")
            "Net Ocean" -> entry.copy(title = "Net Ocean", keys = listOf("Net Ocean", "Oceano da Rede"), content = "Um vasto mar digital que conecta regiões do Mundo Digital e é habitado por Digimons aquáticos.")
            "Digitama" -> entry.copy(content = "Um ovo de Digimon. Novos Digimons eclodem de Digitama, e Digimons derrotados podem retornar a essa forma antes de renascer. Sua gente conhece esse ciclo no corpo; fale dele como de um costume da terra natal, familiar e um pouco admirado.")
            "Digivolution" -> entry.copy(title = "Digivolução", keys = listOf("Digivolução", "Digivolve", "Digivolution"), content = "A transformação de um Digimon em outro estágio. Pode ser influenciada por tempo, treinamento, dados vitais, experiência de batalha e vínculo com o Tamer. Trate cada evolução que você passou como memória viva do corpo — novo peso, novo alcance, nova fome — e lembre dela só quando o momento pedir.")
            "DigiCore" -> entry.copy(title = "DigiCore", content = "O núcleo central de dados de um Digimon, frequentemente tratado como o centro de sua identidade, funções vitais e experiência acumulada.")
            "Yggdrasill" -> entry.copy(content = "Um computador host e sistema de gerenciamento associado a alguns Mundos Digitais. Em muitas continuidades, é tratado como um administrador poderoso, não como uma divindade humana.")
            "Royal Knights" -> entry.copy(title = "Royal Knights", keys = listOf("Royal Knights", "Cavaleiros Reais", "RK"), content = "Um grupo de poderosos Digimons Cavaleiros Sagrados que atua como guardião da Rede sob Yggdrasill em muitas continuidades.")
            "Dark Area" -> entry.copy(title = "Dark Area", keys = listOf("Dark Area", "Área Sombria"), content = "Uma região inferior perigosa associada a dados corrompidos, Digimons derrotados e ameaças que não pertencem às regiões seguras do Mundo Digital.")
            "X-Antibody" -> entry.copy(content = "Um fator de dados que pode proteger um Digimon do Program X e causar uma forma aprimorada por X-Evolution.")
            "Vaccine Data Virus" -> entry.copy(title = "Atributos Vaccine, Data e Virus", keys = listOf("Vaccine", "Data", "Virus", "atributo Vaccine", "atributo Data", "atributo Virus"), content = "Atributos comuns dos Digimons que descrevem afinidades e tendências amplas dos dados, não uma descrição completa da personalidade individual.")
            else -> entry
        }
    }

    private val japanese = english.map { entry ->
        when (entry.title) {
            "Digital World" -> entry.copy(title = "デジタルワールド", keys = listOf("デジタルワールド", "Digital World", "Digiworld", "DW"), content = "データとネットワークから形成された並行世界。デジモンはそこで生き、成長し、進化し、デジタマとして再生するデジタル生命体である。話題に上がれば事典の説明ではなく、匂いや癖や旧知の顔で思い出す故郷として語ります。")
            "Digivice" -> entry.copy(title = "デジヴァイス", keys = listOf("デジヴァイス", "Digivice", "D3", "D-3"), content = "テイマーが携帯し、パートナーデジモンとの通信や、種類によっては進化を支援・発動する装置。")
            "Vital Bracelet" -> entry.copy(title = "バイタルブレス", keys = listOf("バイタルブレス", "Vital Bracelet", "VB", "Watch"), content = "テイマーの活動、バイタルデータ、訓練、進化条件とパートナーデジモンの成長を結びつけるウェアラブル型デジヴァイス。バイタルも特訓もバトルも体で生きてきた出来事であり、忙しかった一日の思い出として語り、数値の羅列にはしません。")
            "Tamer" -> entry.copy(title = "テイマー", keys = listOf("テイマー", "選ばれし子供", "Chosen Child", "DigiDestined"), content = "デジモンの人間パートナー。信頼、訓練、絆は強さ、行動、進化に影響することがある。あなたのテイマーは習慣や内輪の笑いを共有する具体的な一人であり、絆は宣言ではなく日々の付き合いで示します。")
            "Digital Hazard" -> entry.copy(title = "デジタルハザード", keys = listOf("デジタルハザード", "Digital Hazard"), content = "混沌化やデジタルワールドへの脅威につながる可能性を持つデジモンに関連する希少な印。")
            "Net Ocean" -> entry.copy(title = "ネットの海", keys = listOf("ネットの海", "Net Ocean"), content = "デジタルワールドの地域を結ぶ広大なデジタルの海で、水棲デジモンが暮らしている。")
            "Digitama" -> entry.copy(title = "デジタマ", keys = listOf("デジタマ", "Digitama", "Digi-Egg", "Digiegg"), content = "デジモンの卵。新しいデジモンはデジタマから孵化し、倒されたデジモンが再生前にこの姿へ戻ることもある。同族には体に刻まれた巡りであり、故郷の習わしを語るように、親しみと少しの畏れを込めて語ります。")
            "Digivolution" -> entry.copy(title = "進化", keys = listOf("進化", "デジボリューション", "Digivolve", "Digivolution"), content = "デジモンが別の段階へ変化すること。時間、訓練、バイタルデータ、戦闘経験、テイマーとの絆に影響される。自分がくぐった進化は新たな重さと間合いと空腹を伴う生きた身体の記憶であり、場面が呼ぶ時だけ思い出します。")
            "DigiCore" -> entry.copy(title = "デジコア", keys = listOf("デジコア", "DigiCore", "Digi-Core"), content = "デジモンのアイデンティティ、生命機能、蓄積した経験の中心とされるデータコア。")
            "Yggdrasill" -> entry.copy(title = "イグドラシル", keys = listOf("イグドラシル", "Yggdrasill", "Yggdrasil"), content = "一部のデジタルワールドを管理するホストコンピューターとシステム。多くの作品では強力な管理者として扱われる。")
            "Royal Knights" -> entry.copy(title = "ロイヤルナイツ", keys = listOf("ロイヤルナイツ", "Royal Knights", "RK"), content = "多くの作品で、イグドラシルの下でネットワークを守る強力な聖騎士型デジモンの集団。")
            "Dark Area" -> entry.copy(title = "ダークエリア", keys = listOf("ダークエリア", "Dark Area"), content = "データの腐敗、倒されたデジモン、危険な脅威と結びつくデジタルワールドの危険な下層地域。")
            "X-Antibody" -> entry.copy(title = "X抗体", keys = listOf("X抗体", "X-Antibody"), content = "プログラムXからデジモンを守り、X進化した強化形態を引き起こすことがあるデータ因子。")
            "Vaccine Data Virus" -> entry.copy(title = "属性", keys = listOf("属性", "ワクチン", "データ", "ウイルス", "Vaccine", "Data", "Virus"), content = "デジモンのデータの大まかな相性や傾向を示す属性であり、個体の性格すべてを説明するものではない。")
            else -> entry
        }
    }
}
