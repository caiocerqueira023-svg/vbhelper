package com.tamerslab.hardware.vpet

/**
 * Test/debug transport: replies to every sent command with a canned list of adapter lines
 * (D8 — no hardware available). Lines are delivered asynchronously through a [VpetScheduler]
 * so the session sees the same interleaving as a real adapter.
 *
 * Typical use is with [VpetSimulator]:
 * ```kotlin
 * val link = ScriptedVpetLink(VpetSimulator.responsesFor(VpetModel.DM20, 3, 4), onLine, scheduler)
 * ```
 */
class ScriptedVpetLink(
    private val responder: (command: String) -> List<String>,
    private val onLine: (String) -> Unit,
    private val scheduler: VpetScheduler,
) : VpetLink {

    private val lock = Any()
    private var closed = false
    private var generation = 0

    override fun send(text: String) {
        if (closed) return
        val lines = responder(text)
        if (lines.isEmpty()) return
        synchronized(lock) {
            if (closed) return
            generation += 1
            val token = generation
            var index = 0
            fun emitNext() {
                val current = synchronized(lock) { !closed && generation == token }
                if (!current) return
                val line = lines[index]
                index += 1
                onLine(line)
                if (index < lines.size) {
                    scheduler.schedule(LINE_DELAY_MS) { emitNext() }
                }
            }
            scheduler.schedule(FIRST_LINE_DELAY_MS) { emitNext() }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            generation += 1
        }
    }

    companion object {
        /** Delay before the first canned line, mimicking the adapter turnaround. */
        const val FIRST_LINE_DELAY_MS = 10L

        /** Delay between canned lines. */
        const val LINE_DELAY_MS = 5L
    }
}
