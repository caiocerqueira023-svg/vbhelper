package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.domain.digifarm.FarmResident
import org.junit.Assert.*
import org.junit.Test

class ParticipationClockTest {
    private val resident = FarmResident("partner", "farm", 10f, 20f, 30f, 40f, activity = "REST",
        energy = 50, activityStartedAt = 0, updatedAt = 0)

    @Test fun recoveryEarnedBeforeSuspensionIsNotDiscarded() {
        val paused = ParticipationClock.pause(resident, 90_000, alreadyPaused = false)
        assertEquals(55, paused.energy) // Three rest ticks, minus one normal decay tick.
        assertEquals(0L, paused.activityStartedAt)
        assertEquals(90_000L, paused.updatedAt)
        assertEquals(resident.positionX, paused.positionX)
    }

    @Test fun inactiveTimeDoesNotAccrueRecoveryOrActivityDuration() {
        val paused = ParticipationClock.pause(resident, 90_000, alreadyPaused = false)
        val resumed = ParticipationClock.pause(paused, 3_690_000, alreadyPaused = true)
        assertEquals(paused.energy, resumed.energy)
        assertEquals(paused.satiety, resumed.satiety)
        assertEquals(90_000L, resumed.updatedAt - resumed.activityStartedAt)
    }
}
