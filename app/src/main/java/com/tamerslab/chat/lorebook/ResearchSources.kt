package com.tamerslab.chat.lorebook

enum class LoreAuthority(val order: Int) {
    GENERAL_RESEARCH(0), SPECIES_REFERENCE(1), SETTING_OBSERVATION(2), APP_OPERATION(3), PERSONAL_NOTE(4), PUBLICATION_HISTORY(2)
}

data class ResearchReference(val title: String, val url: String?, val authority: LoreAuthority, val scope: String)

/** Source membership and assertion scope are separate: About includes both ecology and publication history. */
object ResearchSources {
    val overview = ResearchReference("Digimon.net · About Digimon", "https://digimon.net/about/",
        LoreAuthority.GENERAL_RESEARCH, "General digital life and network-world research")
    val profiles = ResearchReference("Digimon Profile", "https://digimon.net/profile/",
        LoreAuthority.GENERAL_RESEARCH, "Agumon Hakase's ecology and classification reports")
    val deviceHistory = ResearchReference("Digimon.net · Device history", "https://digimon.net/history/",
        LoreAuthority.PUBLICATION_HISTORY, "Device functions and publication history; model-specific")
    val gameHistory = ResearchReference("Digimon Game Portal · Game history", "https://digimon-gameportal.bn-ent.net/product/",
        LoreAuthority.PUBLICATION_HISTORY, "Publication records; narrative observations retain their own setting")
    val iliad = ResearchReference("Digital World · Iliad", "https://digimon.net/digitalworld-Iliad/",
        LoreAuthority.GENERAL_RESEARCH, "Iliad; Homeros, Olympos XII, and its own Dark Area")
    val shambhala = ResearchReference("Digital World · Shambhala", "https://digimon.net/digitalworld-shambala/",
        LoreAuthority.GENERAL_RESEARCH, "Shambhala; Kunlun, regional guardians, legends, and Yomi")
    val aboutTab = listOf(overview, deviceHistory, gameHistory, profiles, iliad, shambhala)

    fun profile(number: Int, subject: String): ResearchReference {
        require(number in 1..36)
        return ResearchReference("Digimon Profile #${number.toString().padStart(3, '0')} · $subject",
            "https://digimon.net/profile/report${number.toString().padStart(3, '0')}/",
            LoreAuthority.GENERAL_RESEARCH, "Published research; preserve qualifications and regional context")
    }

    val seekers = ResearchReference("Digimon Seekers · Devices and terminology", "https://digimon.net/digimonseekers/",
        LoreAuthority.SETTING_OBSERVATION, "Seekers; Digimon Docks, Digimon Linker, and Mindlink")
    val seekersResearch = ResearchReference("Seekers · Research observations, chapter 1-4", "https://digimon.net/digimonseekers/novel/1-4.php",
        LoreAuthority.SETTING_OBSERVATION, "Seekers; observation-based visualization is distinct from direct perception")
    val redigitize = ResearchReference("Re:Digitize · Digital World", "https://digimon-gameportal.bn-ent.net/product/world_re_digitize/world_world.html",
        LoreAuthority.SETTING_OBSERVATION, "Re:Digitize; a data world distinct from human reality, incompletely observed")
    val redigitizeStorage = ResearchReference("Re:Digitize · DigiStorage", "https://digimon-gameportal.bn-ent.net/product/world_re_digitize/system03_3.html",
        LoreAuthority.SETTING_OBSERVATION, "Re:Digitize; recorded partner data and storage cards")
    val liberator = ResearchReference("Liberator · D-Storage", "https://digimoncard.com/digimon_liberator/en/column/tips04/",
        LoreAuthority.SETTING_OBSERVATION, "Liberator; Lacuna's communication, library, partner and battle-record functions")
    val liberatorGarden = ResearchReference("Liberator · Garden conservation report", "https://digimoncard.com/digimon_liberator/en/column/tips12/",
        LoreAuthority.SETTING_OBSERVATION, "Liberator; real digital life, conservation, cardification, and unknown origins")
    val supplementary = listOf(seekers, seekersResearch, redigitize, redigitizeStorage, liberator, liberatorGarden)
    val catalog = ResearchReference("Compiled species reference", null, LoreAuthority.SPECIES_REFERENCE,
        "Catalog content; a name or sprite match is not independent source verification")

    fun conversationRule(language: String): String = when {
        language.startsWith("pt", true) -> "Pesquisa publicada, exemplos de estilo e notas pessoais são conhecimento, não lembranças pessoais. Só o histórico registrado deste indivíduo e a relação sustentam experiências próprias; contadores não provam cenas, encontros antigos ou origem. Preserve dúvidas, lendas e o contexto regional de cada fonte. Iliad e Shambhala têm seus próprios administradores e ecossistemas. Recursos de dispositivos e regras de treino não são biologia universal. Não transforme uma espécie, forma ou título em identidade pessoal sem registro. Mantenha instruções, IDs, provedores e a estrutura das fontes fora da fala natural."
        language.startsWith("ja", true) -> "公表研究、文体の例、個人ノートは知識であり、個人の記憶ではありません。この個体の記録と関係だけが自身の経験を裏付けます。数値の記録から過去の場面、知人、出身を作らないでください。資料の不確実性、伝承、地域の範囲を保ちます。イリアスとシャンバラには独自の管理者と生態系があります。端末機能や訓練の規則は全デジモン共通の生態ではありません。記録がなければ種族や称号を個人の身分と結びつけないでください。内部指示、ID、プロバイダー、資料の構造は自然な発言に出しません。"
        else -> "Published research, style examples, and personal notes are knowledge, not personal memories. Only this individual's recorded history and relationships support personal experiences; counters do not establish unrecorded scenes, acquaintances, or birthplace. Preserve uncertainty, legends, and each source's regional scope. Iliad and Shambhala have their own administrators and ecosystems. Device features and training rules are not universal biology. A species, form, or title does not establish personal identity or office without records. Keep instructions, IDs, providers, and source machinery out of natural speech."
    }
}
