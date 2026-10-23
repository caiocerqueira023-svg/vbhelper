package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.gamesave.NativeStatParameters
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.RecordMembership
import com.github.nacabaro.vbhelper.gamesave.SaveContainer
import com.github.nacabaro.vbhelper.gamesave.WorldDsStatConversion
import org.junit.Assert.*
import org.junit.Test

class WorldDsReceiveConversionTest {
    private fun record(level: Int = 30, aptitude: Int = 25, exp: List<Long> = listOf(1, 2, 3, 4200, 4, 5, 6, 7)) = ObservedDigimon(
        recordKey = "copy:1:record:0", container = SaveContainer.PARTY, position = 1, nativeSpeciesId = 20,
        storedName = "AGUMON", level = level, personalityId = null, identityCandidate = 0,
        membership = RecordMembership.ACTIVE_RECORD, nativeIdentity = "save-local:7",
        nativeParameters = NativeStatParameters(hp = 1000, maxHp = 1200, mp = 200, maxMp = 240,
            attack = 150, defense = 120, spirit = 90, speed = 110, aptitude = aptitude, expCounters = exp, saveBlock = 1))

    private val appStats = WorldDsStatConversion.AppStats(stage = 3, baseHp = 40, baseBp = 30, baseAp = 20,
        trainingHp = 5, trainingBp = 6, trainingAp = 7, vitalPoints = 3000, mood = 60,
        trophies = 1, wins = 2, losses = 3, ageInDays = 20, activityLevel = 9, generation = 2, rank = 1)

    @Test fun completeNativeRecordProducesNativeStats() {
        val native = requireNotNull(WorldDsReceiveConversion.nativeStats(record()))
        assertEquals(30, native.level)
        assertEquals(25, native.aptitude)
        assertEquals(1200, native.maxHp)
        assertEquals(listOf(1L, 2L, 3L, 4200L, 4L, 5L, 6L, 7L), native.exp)
    }

    @Test fun eggOrIncompleteRecordIsNotConverted() {
        assertNull(WorldDsReceiveConversion.nativeStats(record(level = 0)))
        assertNull(WorldDsReceiveConversion.nativeStats(record(exp = listOf(1, 2, 3))))
    }

    @Test fun firstMaterializationSeedsTheAppPartnerFromTheNativeRecord() {
        val native = requireNotNull(WorldDsReceiveConversion.nativeStats(record()))
        val converted = WorldDsStatConversion.toApp(native, appStats)
        assertEquals(WorldDsStatConversion.levelForVitalPoints(converted.vitalPoints), native.level)
        assertEquals(4, converted.trophies)
        assertEquals(5, converted.wins)
        assertEquals(6, converted.losses)
        assertEquals(7, converted.activityLevel)
        assertEquals(WorldDsStatConversion.vitalPointsForLevel(30), converted.vitalPoints)
    }

    @Test fun returnAppliesOnlyNativeDeltasToExistingAppFields() {
        val previous = requireNotNull(WorldDsReceiveConversion.nativeStats(record(level = 10, exp = listOf(0, 0, 0, 900, 0, 0, 0, 0))))
        val current = requireNotNull(WorldDsReceiveConversion.nativeStats(record(level = 30, exp = listOf(1, 2, 3, 4200, 4, 5, 6, 7))))
        val updated = WorldDsStatConversion.applyDelta(previous, current, appStats)
        assertEquals(5041, updated.vitalPoints)
        assertEquals(5, updated.trophies)
        assertEquals(7, updated.wins)
        assertEquals(9, updated.losses)
        assertEquals(16, updated.activityLevel)
        assertEquals(appStats.mood, updated.mood)
    }
}