package com.tamerslab.hardware.vpet

import java.nio.charset.StandardCharsets

/**
 * Golden vectors emitted by `tools/vpet/extract-lists.mjs` from the recovered lector.js.
 * Format documented in the generated file header.
 */
internal object VpetGoldenVectors {

    data class RomVector(
        val model: VpetModel,
        val version: Int,
        val command: String,
    )

    data class DecodeVector(
        val model: VpetModel,
        val version: Int,
        val index: Int,
        val name: String?,
        val stage: Int?,
        val attribute: Int?,
        val power: Int?,
        val checksumValid: Boolean,
        val payloadHex: String,
        val lines: List<String>,
    )

    data class Golden(
        val roms: List<RomVector>,
        val decodes: List<DecodeVector>,
        val tableCounts: Map<String, Int>,
    )

    fun load(): Golden {
        val stream = VpetGoldenVectors::class.java.getResourceAsStream(RESOURCE)
            ?: error("Missing test resource $RESOURCE; run `node tools/vpet/extract-lists.mjs`")
        val text = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val roms = ArrayList<RomVector>()
        val decodes = ArrayList<DecodeVector>()
        val tableCounts = LinkedHashMap<String, Int>()
        var pendingDecode: List<String>? = null

        for (rawLine in text.lines()) {
            val line = rawLine.trimEnd('\r')
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split('\t')
            when (parts[0]) {
                "rom" -> {
                    require(parts.size == 4) { "Malformed rom golden line: $line" }
                    roms.add(RomVector(modelOf(parts[1]), parts[2].toInt(), parts[3]))
                }

                "table" -> {
                    require(parts.size == 3) { "Malformed table golden line: $line" }
                    tableCounts[parts[1]] = parts[2].toInt()
                }

                "decode" -> {
                    require(parts.size == 10) { "Malformed decode golden line: $line" }
                    require(pendingDecode == null) { "decode line without a following response: $line" }
                    pendingDecode = parts.subList(1, 10)
                }

                "response" -> {
                    require(parts.size == 3) { "Malformed response golden line: $line" }
                    val fields = pendingDecode ?: error("response line without a decode line: $line")
                    pendingDecode = null
                    decodes.add(
                        DecodeVector(
                            model = modelOf(fields[0]),
                            version = fields[1].toInt(),
                            index = fields[2].toInt(),
                            name = fields[3].takeUnless { it == "-" },
                            stage = fields[4].takeUnless { it == "-" }?.toInt(),
                            attribute = fields[5].takeUnless { it == "-" }?.toInt(),
                            power = fields[6].takeUnless { it == "-" }?.toInt(),
                            checksumValid = fields[7] == "1",
                            payloadHex = fields[8],
                            lines = parts[2].split('|'),
                        ),
                    )
                }

                else -> error("Unknown golden line kind '${parts[0]}': $line")
            }
        }
        require(pendingDecode == null) { "Golden file ends with a decode line without response" }
        require(roms.isNotEmpty()) { "Golden file contains no rom vectors" }
        require(decodes.isNotEmpty()) { "Golden file contains no decode vectors" }
        return Golden(roms, decodes, tableCounts)
    }

    private fun modelOf(key: String): VpetModel =
        VpetModel.fromKey(key) ?: error("Golden file references unknown model '$key'")

    private const val RESOURCE = "/vpet-golden.txt"
}

/** Virtual-time scheduler used by the session tests (actions never run inside `schedule`). */
internal class FakeVpetScheduler : VpetScheduler {

    private class Task(val atMillis: Long, val action: () -> Unit) : VpetScheduled {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }
    }

    private val tasks = ArrayList<Task>()

    var nowMillis: Long = 0L
        private set

    override fun schedule(delayMillis: Long, action: () -> Unit): VpetScheduled {
        val task = Task(nowMillis + delayMillis.coerceAtLeast(0L), action)
        tasks.add(task)
        return task
    }

    override fun cancelAll() {
        for (task in tasks) task.cancelled = true
        tasks.clear()
    }

    /** Runs every task due at or before `now + millis`, in scheduling order. */
    fun advanceBy(millis: Long) {
        val target = nowMillis + millis
        while (true) {
            val next = tasks
                .filter { !it.cancelled && it.atMillis <= target }
                .minByOrNull { it.atMillis }
                ?: break
            tasks.remove(next)
            if (next.atMillis > nowMillis) nowMillis = next.atMillis
            next.action()
        }
        if (target > nowMillis) nowMillis = target
    }

    fun pendingCount(): Int = tasks.count { !it.cancelled }
}

/** Captures everything a [VpetReadSession] reports. */
internal class RecordingVpetListener : VpetReadSession.Listener {

    val statuses = ArrayList<String>()
    val progress = ArrayList<Int>()
    val results = ArrayList<VpetReadResult>()
    val failures = ArrayList<VpetReadFailure>()

    override fun onStatus(line: String) {
        statuses.add(line)
    }

    override fun onProgress(percent: Int) {
        progress.add(percent)
    }

    override fun onFinished(result: VpetReadResult) {
        results.add(result)
    }

    override fun onFailed(failure: VpetReadFailure) {
        failures.add(failure)
    }
}
