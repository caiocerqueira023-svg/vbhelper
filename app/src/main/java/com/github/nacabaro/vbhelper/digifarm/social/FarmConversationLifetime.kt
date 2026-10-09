package com.github.nacabaro.vbhelper.digifarm.social

import kotlinx.coroutines.Job

/** Shared leases and epochs fence requests across leaving, reopening, and overlapping surfaces. */
class FarmConversationLifetime {
    private val lock = Any()
    private val owners = mutableMapOf<String, Int>()
    private val epochs = mutableMapOf<String, Long>()
    private val jobs = mutableMapOf<String, MutableSet<Job>>()

    fun acquire(farm: String) = synchronized(lock) { owners[farm] = (owners[farm] ?: 0) + 1 }
    fun release(farm: String) = synchronized(lock) {
        owners[farm] = ((owners[farm] ?: 1) - 1).coerceAtLeast(0)
        if (owners[farm] == 0) cancelLocked(farm)
    }
    fun suspendIfUnobserved(farm: String) = synchronized(lock) {
        if ((owners[farm] ?: 0) == 0) cancelLocked(farm)
    }
    fun token(farm: String): Long = synchronized(lock) { epochs[farm] ?: 0 }
    fun begin(farm: String): Long? = synchronized(lock) {
        if ((owners[farm] ?: 0) > 0) epochs[farm] ?: 0 else null
    }
    fun isCurrent(farm: String, token: Long): Boolean = synchronized(lock) { currentLocked(farm, token) }
    fun register(farm: String, token: Long, job: Job?): Boolean = synchronized(lock) {
        if (!currentLocked(farm, token)) false else {
            if (job != null) jobs.getOrPut(farm) { mutableSetOf() }.add(job)
            true
        }
    }
    fun unregister(farm: String, job: Job?) = synchronized(lock) { if (job != null) jobs[farm]?.remove(job); Unit }
    private fun currentLocked(farm: String, token: Long) = (owners[farm] ?: 0) > 0 && (epochs[farm] ?: 0) == token
    private fun cancelLocked(farm: String) {
        epochs[farm] = (epochs[farm] ?: 0) + 1
        jobs.remove(farm)?.toList()?.forEach { it.cancel() }
    }
}
