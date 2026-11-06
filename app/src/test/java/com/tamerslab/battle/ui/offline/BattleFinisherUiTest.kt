package com.tamerslab.battle.ui.offline

import com.tamerslab.battle.offline.core.BattleFinisherKind
import com.tamerslab.battle.offline.core.BattleFinisherPhase
import com.tamerslab.battle.offline.core.BattleFinisherTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFinisherUiTest {
    private fun fighter(id: String, name: String, special: String?) = BattleFighterPresentation(
        combatantId = id,
        externalCharacterId = id,
        displayName = name,
        modelGlb = byteArrayOf(),
        poseNames = emptySet(),
        setKey = "$id:$name",
        attackVisuals = emptyMap(),
        impactModels = emptyMap(),
        specialDisplayName = special
    )

    @Test fun transformedTitleUsesTheChosenResultsFirstSpecialAndLead() {
        val lead = fighter("lead", "Base", "Base special")
        val form = fighter("lead", "Result", "Result special")
        val otherSlot = fighter("lead", "Other result", "Other special")
        val otherLead = fighter("partner", "Result", "Partner special")
        val forms = listOf(otherLead, otherSlot, form).associateBy { it.setKey }
        for (kind in listOf(BattleFinisherKind.FORM, BattleFinisherKind.JOGRESS)) {
            val movie = BattleFinisherTimeline.snapshot(1, kind, "lead",
                resultSpecies = "result", specialName = "Base special")
            assertEquals("Result", battleFinisherTitle(movie.copy(phase = BattleFinisherPhase.REVEAL),
                mapOf("lead" to lead), forms))
            for (phase in listOf(BattleFinisherPhase.CHARGE, BattleFinisherPhase.RELEASE,
                BattleFinisherPhase.IMPACT, BattleFinisherPhase.AFTERMATH)) {
                assertEquals("Result special", battleFinisherTitle(movie.copy(phase = phase),
                    mapOf("lead" to lead), forms))
            }
        }
    }

    @Test fun missingResultSpecialNeverFallsBackToTheBasesMove() {
        val lead = fighter("lead", "Base", "Base special")
        val result = fighter("lead", "Result", " ")
        val movie = BattleFinisherTimeline.snapshot(1, BattleFinisherKind.FORM, "lead",
            resultSpecies = "Result", specialName = "Base special").copy(phase = BattleFinisherPhase.CHARGE)
        assertNull(battleFinisherTitle(movie, mapOf("lead" to lead), mapOf(result.setKey to result)))
        assertNull(battleFinisherTitle(movie, mapOf("lead" to lead), emptyMap()))
        assertEquals("Result", battleFinisherTitle(movie.copy(phase = BattleFinisherPhase.REVEAL),
            mapOf("lead" to lead), emptyMap()))
    }

    @Test fun powerAndDuoKeepTheLeadsNameAndOwnSpecial() {
        val lead = fighter("lead", "Lead", "Lead special")
        val partner = fighter("partner", "Partner", "Partner special")
        for (kind in listOf(BattleFinisherKind.POWER, BattleFinisherKind.DUO)) {
            val movie = BattleFinisherTimeline.snapshot(2, kind, "lead", partnerId = "partner",
                specialName = "Catalog special")
            val fighters = mapOf("lead" to lead, "partner" to partner)
            assertEquals("Lead", battleFinisherTitle(movie.copy(phase = BattleFinisherPhase.REVEAL), fighters, emptyMap()))
            assertEquals("Lead special", battleFinisherTitle(movie.copy(phase = BattleFinisherPhase.RELEASE), fighters, emptyMap()))
        }
    }

    @Test fun focusTransformationAndRestorationHaveNoArenaTitle() {
        val movie = BattleFinisherTimeline.snapshot(1, BattleFinisherKind.JOGRESS, "lead",
            resultSpecies = "Result", specialName = "Special")
        for (phase in listOf(BattleFinisherPhase.FOCUS, BattleFinisherPhase.TRANSFORM, BattleFinisherPhase.RESTORE)) {
            assertNull(battleFinisherTitle(movie.copy(phase = phase), emptyMap(), emptyMap()))
        }
    }
}
