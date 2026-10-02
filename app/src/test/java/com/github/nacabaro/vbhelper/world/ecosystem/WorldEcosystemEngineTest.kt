package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import org.junit.Assert.*
import org.junit.Test

class WorldEcosystemEngineTest {
    private fun population() = listOf("a", "b", "c").mapIndexed { index, id -> WorldSpawn(
        id = index + 1L, cardCharacterId = 1, individualId = id, latitude = 0.0,
        longitude = index * 0.0001, spawnedAt = 0, expiresAt = Long.MAX_VALUE, mood = 82
    ) }

    @Test fun `individual ordering cannot change wandering decisions`() {
        var forward = population()
        var reversed = forward.reversed()
        for (tick in 1L..400L) {
            forward = WorldEcosystemEngine.step(42L, tick, forward)
            reversed = WorldEcosystemEngine.step(42L, tick, reversed)
        }
        assertEquals(forward, reversed)
    }

    @Test fun `live and batched replay stay identical through 1200 ticks`() {
        var live = population()
        var replay = population()
        for (tick in 1L..1200L) live = WorldEcosystemEngine.step(42L, tick, live)
        for (batch in 0..37) {
            for (tick in (batch * 32L + 1)..minOf((batch + 1) * 32L, 1200L)) {
                replay = WorldEcosystemEngine.step(42L, tick, replay)
            }
        }
        assertEquals(live, replay)
    }

    @Test fun `motion stays inside anchors and within half a meter per second without changing player trust`() {
        var current = population()
        val homes = current.associate { it.individualId to GeoPoint(it.latitude, it.longitude) }
        for (tick in 1L..1200L) {
            val next = WorldEcosystemEngine.step(42L, tick, current)
            current.zip(next).forEach { (before, after) ->
                val position = GeoPoint(after.latitude, after.longitude)
                assertTrue(RadarWorldGeometry.relative(homes[after.individualId]!!, position).distanceMeters <= after.anchorRadiusMeters + 0.01)
                assertTrue(RadarWorldGeometry.relative(GeoPoint(before.latitude, before.longitude), position).distanceMeters <= 0.751)
                assertEquals(82, after.mood)
                assertEquals(before.homeLatitude, after.homeLatitude, 0.0)
                assertEquals(before.homeLongitude, after.homeLongitude, 0.0)
            }
            current = next
        }
    }

    @Test fun `claimed idle participants never wander away from their conversation`() {
        val original = population()
        var current = original
        for (tick in 1L..200L) current = WorldEcosystemEngine.step(42L, tick, current, claimed = setOf("a"))
        assertEquals(original[0].latitude, current[0].latitude, 0.0)
        assertEquals(original[0].longitude, current[0].longitude, 0.0)
    }

    @Test fun `personality changes movement opportunities without stat bonuses`() {
        val first = WorldEcosystemEngine.step(42L, 1L, population(), personalities = mapOf("a" to DigimonPersonalityType.RECKLESS))
        val second = WorldEcosystemEngine.step(42L, 1L, population(), personalities = mapOf("a" to DigimonPersonalityType.TOLERANT))
        assertNotEquals(first[0].nextDecisionTick, second[0].nextDecisionTick)
        assertEquals(first[0].mood, second[0].mood)
    }
}
