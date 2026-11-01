package com.tamerslab.battle.offline

import com.tamerslab.battle.offline.data.*
import org.junit.Assert.*
import org.junit.Test

class AppTrainingProjectionTest {
    private val base = VitalBattleProfile(VitalStatScale.DIM, 3, 10, 2)
    @Test fun appStartingProjectionMatchesExistingCatalogScale() {
        assertEquals(TrainingBattleStats.forParticipant(2, base), TrainingBattleStats.forParticipant(2, base.copy(scale = VitalStatScale.APP_DIM)))
    }
    @Test fun appTrainingIsAppliedSeparatelyAndBoundedWithoutChangingNativeDimRules() {
        val trained = base.copy(scale = VitalStatScale.APP_DIM, trainingHp = 100)
        assertTrue(TrainingBattleStats.forParticipant(2, trained).health > TrainingBattleStats.forParticipant(2, base).health)
        assertEquals(TrainingBattleStats.forParticipant(2, base), TrainingBattleStats.forParticipant(2, base.copy(trainingHp = 100)))
        assertEquals(TrainingBattleStats.forParticipant(2, trained.copy(trainingHp = 999)),
            TrainingBattleStats.forParticipant(2, trained.copy(trainingHp = 999999)))
    }
    @Test fun originInfluencesOnlyAuthoredAppBasisAndIsBounded() {
        val app = base.copy(scale = VitalStatScale.APP_DIM)
        assertTrue(TrainingBattleStats.forParticipant(2, app.copy(originBasisPoints = 11000)).health > TrainingBattleStats.forParticipant(2, app).health)
        assertEquals(TrainingBattleStats.forParticipant(2, app.copy(originBasisPoints = 11000)), TrainingBattleStats.forParticipant(2, app.copy(originBasisPoints = 999999)))
        assertEquals(TrainingBattleStats.forParticipant(2, base), TrainingBattleStats.forParticipant(2, base.copy(originBasisPoints = 11000)))
    }
}
