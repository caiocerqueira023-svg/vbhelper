package com.github.nacabaro.vbhelper.world.ecosystem

data class EcosystemClockAdvance(
    val session: WorldEcosystemSession,
    val steps: Int,
    val dormantMillis: Long = 0
)

/** Pure clock contract shared by live ticks and resume. No wall clock or network access here. */
object WorldEcosystemClock {
    const val RULES_VERSION = 3
    const val TICK_MILLIS = 1_500L
    const val MAX_CATCH_UP_MILLIS = 30 * 60 * 1_000L

    fun advance(checkpoint: WorldEcosystemSession, now: Long): EcosystemClockAdvance {
        require(checkpoint.rulesVersion == RULES_VERSION) { "Unsupported ecosystem rules version" }
        require(checkpoint.tickIndex >= 0 && checkpoint.lastCheckpointAt >= 0)
        require(checkpoint.tickRemainderMillis in 0 until TICK_MILLIS)
        // Preserve the high-water timestamp on rollback: elapsed time cannot be consumed twice.
        val timestamp = now.coerceAtLeast(checkpoint.lastCheckpointAt)
        val elapsed = timestamp - checkpoint.lastCheckpointAt
        if (elapsed == 0L) return EcosystemClockAdvance(checkpoint, 0)
        if (checkpoint.pauseReason == WorldPauseReason.PLAYER_BATTLE) {
            return EcosystemClockAdvance(checkpoint.copy(lastCheckpointAt = timestamp), 0)
        }
        val eligible = elapsed.coerceAtMost(MAX_CATCH_UP_MILLIS)
        val accumulated = eligible + checkpoint.tickRemainderMillis
        val steps = (accumulated / TICK_MILLIS).toInt()
        val dormant = elapsed - eligible
        return EcosystemClockAdvance(
            checkpoint.copy(
                tickIndex = checkpoint.tickIndex + steps,
                tickRemainderMillis = if (dormant > 0) 0 else accumulated % TICK_MILLIS,
                lastCheckpointAt = timestamp
            ),
            steps,
            dormant
        )
    }
}

/**
 * Rules v1 seed contract: FNV-1a over little-endian seed/tick and length-prefixed UTF-8
 * identity/purpose, followed by the SplitMix64 finalizer. JVM String hashes and iteration
 * order are deliberately absent. The input format/formula must change only with rulesVersion.
 */
object EcosystemSeed {
    fun mix(sessionSeed: Long, identity: String, purpose: String, tick: Long): Long {
        var hash = 0xcbf29ce484222325UL.toLong()
        fun byte(value: Int) { hash = (hash xor (value and 0xff).toLong()) * 0x100000001b3L }
        fun number(value: Long) { for (shift in 0..56 step 8) byte((value ushr shift).toInt()) }
        fun text(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            number(bytes.size.toLong())
            bytes.forEach { byte(it.toInt()) }
        }
        number(sessionSeed)
        text(identity)
        text(purpose)
        number(tick)
        hash = (hash xor (hash ushr 30)) * 0xbf58476d1ce4e5b9UL.toLong()
        hash = (hash xor (hash ushr 27)) * 0x94d049bb133111ebUL.toLong()
        return hash xor (hash ushr 31)
    }
}
