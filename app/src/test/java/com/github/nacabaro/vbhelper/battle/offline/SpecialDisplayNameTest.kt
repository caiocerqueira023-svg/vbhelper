package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.BattleSide
import com.github.nacabaro.vbhelper.battle.offline.data.BattleParticipantProfile
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.cfogrady.vbnfc.data.NfcCharacter
import org.junit.Assert.*
import org.junit.Test

class SpecialDisplayNameTest {
    private fun profile(movesJson: String?) = BattleParticipantProfile(
        sourceCharacterId = 1, individualId = "i1", externalCharacterId = "dim000_mon03",
        displayName = "Pulsemon", stage = 1, attribute = NfcCharacter.Attribute.Vaccine,
        personalityType = DigimonPersonalityType.FRIENDLY,
        baseHp = 1800, baseBp = 2400, baseAp = 700,
        trainingHp = 0, trainingBp = 0, trainingAp = 0,
        vitalPoints = 0, mood = 0, isBemCard = false,
        specialMovesJson = movesJson
    )

    @Test fun firstSpecialMoveParsesFirstSkillListEntry() {
        assertEquals("Sakkuri", profile("[\"Sakkuri\"]").firstSpecialMove())
        assertEquals("Twenty Dive", profile("[\"Twenty Dive\", \"Second\"]").firstSpecialMove())
    }

    @Test fun firstSpecialMoveFallsBackToNull() {
        assertNull(profile(null).firstSpecialMove())
        assertNull(profile("").firstSpecialMove())
        assertNull(profile("[]").firstSpecialMove())
        assertNull(profile("not json").firstSpecialMove())
        assertNull(profile("[\"\"]").firstSpecialMove())
    }

    @Test fun factoryForwardsSpecialDisplayNameOverride() {
        val withOverride = TrainingBattleFactory.definition(
            TrainingParticipantInput("a", displayName = "A", stage = 2, maxHealth = 1_000, attack = 1,
                specialDisplayNameOverride = "Sakkuri"),
            BattleSide.ALLIED
        )
        assertEquals("Sakkuri", withOverride.specialDisplayNameOverride)
        val withoutOverride = TrainingBattleFactory.definition(
            TrainingParticipantInput("b", displayName = "B", stage = 2, maxHealth = 1_000, attack = 1),
            BattleSide.ALLIED
        )
        assertNull(withoutOverride.specialDisplayNameOverride)
    }
}
