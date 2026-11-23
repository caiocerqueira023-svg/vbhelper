package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.gamesave.WorldDsStatConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryProgressTrackingTest {
    @Test
    fun `world ds record becomes the shared story block`() {
        val native = WorldDsStatConversion.NativeStats(
            level = 42,
            aptitude = 99,
            hp = 900,
            maxHp = 1200,
            mp = 90,
            maxMp = 120,
            attack = 210,
            defense = 180,
            spirit = 160,
            speed = 140,
            exp = listOf(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L),
        )
        val story = StoryProgressTracking.fromWorldDs(native)
        assertEquals(42, story.level)
        assertEquals(360L, story.expTotal)
        assertEquals(900, story.hp)
        assertEquals(1200, story.maxHp)
        assertEquals(210, story.attack)
        assertEquals(140, story.speed)
        // Aptitude is a Lost Evolution-only extra; other titles do not populate it.
        assertNull(story.aptitude)
    }

    @Test
    fun `dawn dusks nine exp channels are normalized into one total`() {
        val native = Ds2StatConversion.NativeStats(
            level = 7,
            maxLevel = 40,
            hp = 100,
            maxHp = 200,
            mp = 10,
            maxMp = 20,
            attack = 30,
            defense = 20,
            spirit = 15,
            speed = 25,
            exp = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L),
        )
        val story = StoryProgressTracking.fromDs2(native)
        assertEquals(7, story.level)
        assertEquals(45L, story.expTotal)
        assertNull(story.aptitude)
    }

    @Test
    fun `lost evolution record keeps aptitude and its single exp word`() {
        val native = LeStatConversion.NativeStats(
            level = 88,
            aptitude = 60,
            hp = 4000,
            maxHp = 4500,
            mp = 300,
            maxMp = 350,
            attack = 800,
            defense = 700,
            spirit = 650,
            speed = 60,
            exp = listOf(123_456L),
        )
        val story = StoryProgressTracking.fromLe(native)
        assertEquals(88, story.level)
        assertEquals(123_456L, story.expTotal)
        assertEquals(60, story.aptitude)
    }

    @Test
    fun `domain row carries the observed values and import provenance`() {
        val snapshot = StorySnapshot(
            level = 75,
            expTotal = 999L,
            hp = 500,
            maxHp = 600,
            mp = 50,
            maxMp = 60,
            attack = 300,
            defense = 250,
            spirit = 200,
            speed = 90,
            aptitude = 55,
        )
        val domain = StoryProgressTracking.toDomain(snapshot, "individual-x", 1_700_000_000_000L,
            ProgressSeedSource.IMPORT)
        assertEquals("individual-x", domain.individualId)
        assertEquals(75, domain.level)
        assertEquals(999L, domain.expTotal)
        assertEquals(55, domain.aptitude)
        assertEquals(ProgressSeedSource.IMPORT, domain.seedSource)
        assertEquals(0L, domain.revision)
        assertEquals(1_700_000_000_000L, domain.updatedAt)
    }

    @Test
    fun `fromRecord maps a retained record of any layout`() {
        val parameters = com.tamerslab.gamesave.NativeStatParameters(
            hp = 900, maxHp = 1200, mp = 90, maxMp = 120, attack = 210, defense = 180,
            spirit = 160, speed = 140, aptitude = 60,
            expCounters = listOf(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L), saveBlock = 1,
        )
        val record = com.tamerslab.gamesave.ObservedDigimon(
            recordKey = "retained", container = com.tamerslab.gamesave.SaveContainer.PARTY, position = 0,
            nativeSpeciesId = 2, storedName = "CAIO", level = 42,
            personalityId = null, identityCandidate = 0,
            membership = com.tamerslab.gamesave.RecordMembership.ACTIVE_RECORD,
            nativeRecordBase64 = null, nativeIdentity = "save-local:7", nativeParameters = parameters,
        )
        val worldDs = requireNotNull(StoryProgressTracking.fromRecord(
            record, com.tamerslab.gamesave.NdsStoryProfiles.WORLD_DS.gameId))
        assertEquals(42, worldDs.level)
        assertEquals(1200, worldDs.maxHp)
        assertEquals(360L, worldDs.expTotal)
    }
}
