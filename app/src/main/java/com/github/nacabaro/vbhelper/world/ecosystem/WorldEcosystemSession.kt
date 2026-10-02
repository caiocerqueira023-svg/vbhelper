package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WorldPauseReason { RADAR_HIDDEN, PLAYER_BATTLE }

/** Clock checkpoint. Hidden time is replayable; a committed battle interval is frozen. */
@Entity
data class WorldEcosystemSession(
    @PrimaryKey val id: String = LOCAL_SESSION_ID,
    val seed: Long,
    val rulesVersion: Int = WorldEcosystemClock.RULES_VERSION,
    val tickIndex: Long = 0,
    val tickRemainderMillis: Long = 0,
    val lastCheckpointAt: Long,
    val revision: Long = 0,
    val regionLatitude: Double? = null,
    val regionLongitude: Double? = null,
    val pauseReason: WorldPauseReason? = null
) {
    companion object { const val LOCAL_SESSION_ID = "radar-local" }
}
