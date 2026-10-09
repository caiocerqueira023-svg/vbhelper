package com.github.nacabaro.vbhelper.quests

/** Authored gameplay patterns, not LLM-created rules. Sources describe the original inspiration. */
data class QuestTemplate(val id: String, val title: String, val sourceQuest: String, val sourceUrl: String, val version: Int = 1)

object QuestTemplates {
    private const val NEXT_ORDER = "https://gamefaqs.gamespot.com/ps4/196176-digimon-world-next-order/faqs/79149/recruitment-guide"
    private const val CYBER_SLEUTH = "https://www.gamersheroes.com/game-guides/digimon-story-cyber-sleuth-case-guide/"
    val supplies = QuestTemplate("supplies", "A practical favor", "Palmon's supply request", NEXT_ORDER)
    val rival = QuestTemplate("rival", "A rival's challenge", "Dream of the Sky", CYBER_SLEUTH)
    val patrol = QuestTemplate("patrol", "Trouble nearby", "Search Wanted Hacker", CYBER_SLEUTH)
    val training = QuestTemplate("training", "Show your progress", "KaiserGreymon's partner-stat trial", NEXT_ORDER)
    val medicine = QuestTemplate("medicine", "A field medicine trial", "Dr. Datamon's Medicine Trial", CYBER_SLEUTH)
    val recruitment = QuestTemplate("recruitment", "A promise to travel together", "KaiserGreymon's recruitment chain", NEXT_ORDER)
    val missing = QuestTemplate("missing", "Someone is missing", "Find the Missing", CYBER_SLEUTH, version = 2)
    val property = QuestTemplate("property", "A lost keepsake", "Digimon's Property", CYBER_SLEUTH, version = 2)
    val courier = QuestTemplate("courier", "A letter and a reply", "Stingmon's letter to MegaKabuterimon", NEXT_ORDER, version = 2)
    val rescue = QuestTemplate("rescue", "A friend in trouble", "A Missing Friend",
        "https://game8.co/games/Digimon-Story-Time-Stranger/archives/556109", version = 2)
    val supplyRound = QuestTemplate("supply_round", "Supplies for everyone", "Dr. Datamon's Medicine Trial", CYBER_SLEUTH, version = 3)
    val timeTrial = QuestTemplate("time_trial", "A race against time", "MachGaogamon's timed recruitment battle", NEXT_ORDER, version = 3)
    val carefulVictory = QuestTemplate("careful_victory", "A careful victory", "Paildramon's constrained recruitment battle", NEXT_ORDER, version = 3)
    val techniqueTrial = QuestTemplate("technique_trial", "Show your fighting style", "Darkdramon's partner-capability trials", NEXT_ORDER, version = 3)
    val recruitmentTrial = QuestTemplate("recruitment_trial", "Earn a place together", "KaiserGreymon and Darkdramon's recruitment trials", NEXT_ORDER, version = 4)
    val recruitmentService = QuestTemplate("recruitment_service", "Prove you can care for a team", "Palmon's supply recruitment request", NEXT_ORDER, version = 4)
    val recruitmentRescue = QuestTemplate("recruitment_rescue", "Bring a friend home", "A Missing Friend",
        "https://game8.co/games/Digimon-Story-Time-Stranger/archives/556109", version = 4)
    val ppTraining = QuestTemplate("pp_training", "A little stronger each day", "KaiserGreymon's partner-stat trial", NEXT_ORDER, version = 5)
    val ppMilestone = QuestTemplate("pp_milestone", "Show your training milestone", "KaiserGreymon's partner-stat trial", NEXT_ORDER, version = 5)
    val watchAdventure = QuestTemplate("watch_adventure", "Beyond the next area", "The Trial",
        "https://game8.co/games/Digimon-Story-Time-Stranger/archives/555817", version = 5)
    val all = listOf(supplies, rival, patrol, training, medicine, recruitment, missing, property, courier, rescue,
        supplyRound, timeTrial, carefulVictory, techniqueTrial, recruitmentTrial, recruitmentService, recruitmentRescue,
        ppTraining, ppMilestone, watchAdventure)
    fun get(id: String): QuestTemplate = all.first { it.id == id }
}

/** DIM/BEM raw stages are zero-based: Rookie=2, Champion=3, Perfect=4, Ultimate/Mega=5. */
data class RecruitmentEffort(val radarWins: Int, val watchBattles: Int, val watchWins: Int, val trophies: Int, val partnerStage: Int)

object QuestDifficulty {
    fun recruitment(stage: Int): RecruitmentEffort = when {
        stage <= 1 -> RecruitmentEffort(1, 0, 0, 0, 0)
        stage == 2 -> RecruitmentEffort(1, 0, 0, 0, 1)
        stage == 3 -> RecruitmentEffort(1, 6, 3, 4, 2)
        stage == 4 -> RecruitmentEffort(2, 12, 6, 10, 3)
        stage == 5 -> RecruitmentEffort(3, 20, 12, 16, 4)
        else -> RecruitmentEffort(4, 30, 20, 20, 5)
    }
}
