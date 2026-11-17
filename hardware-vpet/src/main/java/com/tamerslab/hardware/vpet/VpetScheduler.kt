package com.tamerslab.hardware.vpet

import java.util.Collections
import java.util.Timer
import java.util.TimerTask

/** Handle of one scheduled action, so it can be cancelled like `clearTimeout` in lector.js. */
interface VpetScheduled {
    fun cancel()
}

/** Minimal timer abstraction used by [VpetReadSession] (fakeable in JVM tests). */
interface VpetScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): VpetScheduled
    fun cancelAll()
}

/**
 * Default [VpetScheduler]: a single daemon [java.util.Timer].
 *
 * Actions run on the timer thread, never synchronously inside [schedule]; [VpetReadSession]
 * synchronizes its own state and is safe to receive lines from other threads.
 */
class TimerVpetScheduler : VpetScheduler {

    private val timer = Timer("vpet-scheduler", true)
    private val pending: MutableSet<TimerTask> = Collections.synchronizedSet(HashSet())

    override fun schedule(delayMillis: Long, action: () -> Unit): VpetScheduled {
        val task = object : TimerTask() {
            override fun run() {
                pending.remove(this)
                action()
            }
        }
        pending.add(task)
        try {
            timer.schedule(task, delayMillis.coerceAtLeast(0L))
        } catch (error: IllegalStateException) {
            pending.remove(task)
            throw error
        }
        return object : VpetScheduled {
            override fun cancel() {
                task.cancel()
                pending.remove(task)
            }
        }
    }

    override fun cancelAll() {
        val snapshot = synchronized(pending) { pending.toList() }
        for (task in snapshot) task.cancel()
        pending.clear()
    }
}
