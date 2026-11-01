package com.tamerslab.farm

import com.tamerslab.domain.digifarm.FarmResident

/** Account for eligible time up to first suspension; freeze only time actually spent inactive. */
object ParticipationClock {
    fun pause(resident: FarmResident, now: Long, alreadyPaused: Boolean): FarmResident =
        if (!alreadyPaused) FarmBehaviorPolicy.advanceNeeds(resident, now)
        else resident.copy(updatedAt = now,
            activityStartedAt = resident.activityStartedAt + (now - resident.updatedAt).coerceAtLeast(0))
}
