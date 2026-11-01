package com.tamerslab.gamesave

import com.tamerslab.gamesave.WorldDsStatConversion.AppStats
import com.tamerslab.gamesave.WorldDsStatConversion.NativeStats
import org.junit.Assert.*
import org.junit.Test

class WorldDsStatConversionTest {
    private fun app(level: Int = 0, mood: Int = 50, stage: Int = 2, training: Int = 0) = AppStats(
        stage = stage,
        baseHp = 100,
        baseBp = 100,
        baseAp = 100,
        trainingHp = training,
        trainingBp = training,
        trainingAp = training,
        vitalPoints = WorldDsStatConversion.vitalPointsForLevel(level),
        mood = mood,
        trophies = 3,
        wins = 11,
        losses = 4,
        ageInDays = 7,
        activityLevel = 21,
        generation = 2,
        rank = 3,
    )

    @Test fun vitalPointLevelMappingRoundTripsWithinOnePoint() {
        assertEquals(1, WorldDsStatConversion.levelForVitalPoints(0))
        assertEquals(99, WorldDsStatConversion.levelForVitalPoints(9999))
        for (level in 1..99) {
            val points = WorldDsStatConversion.vitalPointsForLevel(level)
            assertEquals("level $level", level, WorldDsStatConversion.levelForVitalPoints(points))
        }
    }

    @Test fun generatedNativeStatsStayInsideDocumentedRanges() {
        for (vitalPoints in listOf(0, 1, 2500, 4999, 5000, 9998, 9999)) {
            for (training in listOf(0, 1, 500, 999)) {
                val native = WorldDsStatConversion.toNative(app().copy(vitalPoints = vitalPoints, trainingHp = training,
                    trainingBp = training, trainingAp = training))
                assertTrue(native.level in 1..99)
                assertTrue(native.aptitude in 0..99)
                assertTrue(native.maxHp in 10..999999)
                assertEquals(native.maxHp, native.hp)
                assertTrue(native.maxMp in 5..9999)
                assertEquals(native.maxMp, native.mp)
                assertTrue(native.attack in 5..999)
                assertTrue(native.defense in 5..999)
                assertTrue(native.spirit in 5..999)
                assertTrue(native.speed in 5..999)
                assertEquals(8, native.exp.size)
                assertTrue(native.exp.all { it in 0..99999 })
            }
        }
    }

    @Test fun expChannelsCarryTrainingVitalsTrophiesRecordAndLifetime() {
        val native = WorldDsStatConversion.toNative(app(level = 30).copy(trainingHp = 10, trainingBp = 20, trainingAp = 30))
        assertEquals(listOf(10L, 20L, 30L, WorldDsStatConversion.vitalPointsForLevel(30).toLong(), 3L, 11L, 4L, 21L), native.exp)
    }

    @Test fun levelDeltaBecomesVitalPointsOnReturn() {
        val previous = WorldDsStatConversion.toNative(app(level = 10))
        val current = previous.copy(level = 50)
        val updated = WorldDsStatConversion.applyDelta(previous, current, app(level = 10))
        assertEquals(WorldDsStatConversion.vitalPointsForLevel(50), updated.vitalPoints)
    }

    @Test fun returnAppliesTrainingTrophyWinLossAndActivityDeltas() {
        val current = WorldDsStatConversion.toNative(app(level = 30).copy(trainingHp = 40, trainingBp = 50, trainingAp = 60))
        val previous = current.copy(
            level = 10,
            maxHp = current.maxHp - 100000, hp = current.maxHp - 100000, attack = current.attack - 40, defense = current.defense - 30,
            exp = current.exp.toMutableList().also { it[4] -= 2; it[5] -= 3; it[6] -= 1; it[7] -= 5 },
        )
        val updated = WorldDsStatConversion.applyDelta(previous, current, app(level = 10))
        assertTrue(updated.trainingHp > 0)
        assertTrue(updated.trainingBp > 0)
        assertTrue(updated.trainingAp > 0)
        assertEquals(5, updated.trophies)
        assertEquals(14, updated.wins)
        assertEquals(5, updated.losses)
        assertEquals(26, updated.activityLevel)
        assertEquals(WorldDsStatConversion.vitalPointsForLevel(30), updated.vitalPoints)
    }

    @Test fun moodRaisesSpiritOnlyWithinDocumentedBand() {
        val low = WorldDsStatConversion.toNative(app(mood = 0))
        val neutral = WorldDsStatConversion.toNative(app(mood = 50))
        val high = WorldDsStatConversion.toNative(app(mood = 100))
        assertTrue(low.spirit <= neutral.spirit)
        assertTrue(neutral.spirit <= high.spirit)
        assertTrue(low.spirit >= neutral.spirit * 75 / 100 - 1)
        assertTrue(high.spirit <= neutral.spirit * 125 / 100 + 1)
    }

    @Test fun aptitudeGrowsWithStageExperienceAndGenerationInsideTheGameBand() {
        val rookie = WorldDsStatConversion.toNative(app(stage = 1).copy(trophies = 0, wins = 0, losses = 0, ageInDays = 0, rank = 0, generation = 1))
        val mega = WorldDsStatConversion.toNative(app(stage = 5).copy(trophies = 500, wins = 500, losses = 500, ageInDays = 500, rank = 9, generation = 8))
        assertTrue(rookie.aptitude in 0..99)
        assertTrue(mega.aptitude in 0..99)
        assertTrue(mega.aptitude > rookie.aptitude)
    }

    @Test fun reverseConversionInvertsGeneratedStats() {
        val source = app(level = 40).copy(trainingHp = 120, trainingBp = 80, trainingAp = 200, mood = 70)
        val native = WorldDsStatConversion.toNative(source)
        val converted = WorldDsStatConversion.toApp(native, source)
        assertEquals(native.level, WorldDsStatConversion.levelForVitalPoints(converted.vitalPoints))
        assertEquals(3, converted.trophies)
        assertEquals(11, converted.wins)
        assertEquals(4, converted.losses)
        assertEquals(21, converted.activityLevel)
        assertEquals(source.trainingHp, converted.trainingHp)
        assertEquals(source.trainingBp, converted.trainingBp)
        assertEquals(source.trainingAp, converted.trainingAp)
        assertTrue(kotlin.math.abs(source.mood - converted.mood) <= 2)
    }

    @Test fun noOpDeltaChangesNothing() {
        val native = WorldDsStatConversion.toNative(app(level = 55))
        val before = app(level = 55)
        assertEquals(before, WorldDsStatConversion.applyDelta(native, native, before))
    }
}
