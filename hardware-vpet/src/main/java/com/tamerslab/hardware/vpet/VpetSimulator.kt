package com.tamerslab.hardware.vpet

/**
 * Builds synthetic VPet answers so the whole read flow can be exercised without hardware
 * (D8). The generated packets/blocks are valid for the recovered decoders, including the
 * nibble checksums, the Color block checksum and the version gate:
 *
 * - 16-bit models answer with `r:XXXX` lines. When the version embedded in the DigiROM does
 *   not match [version], the VPet "hides" its species and reports index 0 — exactly the
 *   behaviour the session's version learning relies on;
 * - Color models answer with `s:<block>`, `r:<block>` and `t` lines (DMC block for DMC,
 *   Pendulum Color block for PenC).
 *
 * Use it with [ScriptedVpetLink]:
 * ```kotlin
 * val scheduler = TimerVpetScheduler()
 * val session = VpetReadSession(VpetModel.DM20, 0, link::send, scheduler, listener)
 * val link = ScriptedVpetLink(VpetSimulator.responsesFor(VpetModel.DM20, 3, 4), session::receiveLine, scheduler)
 * ```
 */
object VpetSimulator {

    /** Device power reported in simulated packets/blocks (the protocol just carries it). */
    private const val SIMULATED_POWER = 100

    /**
     * Returns a responder for [ScriptedVpetLink] that simulates a VPet of [model] running
     * device [version] and currently displaying the species at table [index].
     */
    fun responsesFor(model: VpetModel, version: Int, index: Int): (command: String) -> List<String> {
        val deviceVersion = normalizeVersion(model, version)
        val deviceIndex = index.coerceAtLeast(0)
        return { command ->
            when {
                command == VpetDigiRom.STOP_COMMAND -> emptyList()
                model.colorProtocol -> colorResponses(model, deviceVersion, deviceIndex)
                else -> {
                    val requestedVersion = requestedVersion(model, command)
                    val replyIndex = if (requestedVersion != null && requestedVersion != deviceVersion) {
                        // The VPet only reveals the Digimon number when the version matches.
                        0
                    } else {
                        deviceIndex
                    }
                    packetResponses(model, deviceVersion, replyIndex)
                }
            }
        }
    }

    private fun colorResponses(model: VpetModel, version: Int, index: Int): List<String> {
        val block = if (model == VpetModel.PENC) pencBlock(version, index) else dmcBlock(version, index)
        return listOf("s:$block", "r:$block", "t")
    }

    private fun packetResponses(model: VpetModel, version: Int, index: Int): List<String> {
        val words = when (model) {
            VpetModel.DM20 -> dm20Words(version, index)
            VpetModel.DMX -> dmxWords(version, index)
            VpetModel.PEN20 -> pen20Words(version, index)
            VpetModel.PENZ -> penzWords(version, index)
            VpetModel.DMC, VpetModel.PENC -> return emptyList()
        }
        return words.map { word -> "r:${VpetProtocol.hex4(word)}" }
    }

    private fun dm20Words(version: Int, index: Int): IntArray {
        val attribute = tableEntry(VpetModel.DM20, version, index)?.attribute ?: 0
        val words = intArrayOf(
            0x0301,
            0x0D0F,
            ((version and 0xF) shl 4) or 0xE,
            ((index and 0x3FF) shl 6) or ((attribute and 3) shl 4) or 0xE,
            0x000E,
            ((SIMULATED_POWER and 0xFF) shl 4) or 0xE,
            0x000E,
            0x000E,
            0x000E,
            0x0000,
        )
        words[9] = words[9] or (responseChecksumDigit(words, 0) shl 12)
        return words
    }

    private fun pen20Words(version: Int, index: Int): IntArray {
        val attribute = tableEntry(VpetModel.PEN20, version, index)?.attribute ?: 0
        val words = intArrayOf(
            ((version and 0xF) shl 4) or 0xE,
            ((index and 0xFF) shl 6) or ((attribute and 3) shl 4) or 0xE,
            0x000E,
            0x000E,
            ((SIMULATED_POWER and 0xFF) shl 4) or 0xE,
            // copy code 0: a regular Pen20 roster Digimon
            0x000E,
            0x0000,
            0x0000,
            0x0000,
            0x0000,
        )
        words[9] = words[9] or (responseChecksumDigit(words, 12) shl 12)
        return words
    }

    private fun penzWords(version: Int, index: Int): IntArray {
        val entry = tableEntry(VpetModel.PENZ, version, index)
        val attribute = entry?.attribute ?: 0
        val stage = entry?.stage ?: 0
        val words = intArrayOf(
            ((version and 0xF) shl 4) or 0xE,
            ((stage and 7) shl 13) or ((index and 127) shl 6) or ((attribute and 3) shl 4) or 0xE,
            0x000E,
            0x000E,
            ((SIMULATED_POWER and 0xFF) shl 4) or 0xE,
            // Every PenZ answer packet carries the 0xE low-nibble marker, including this one.
            0x000E,
        )
        words[5] = words[5] or (responseChecksumDigit(words, 8) shl 12)
        return words
    }

    private fun dmxWords(version: Int, index: Int): IntArray {
        val entry = tableEntry(VpetModel.DMX, version, index)
        val attribute = entry?.attribute ?: 0
        val stage = entry?.stage ?: 0
        return intArrayOf(
            ((version and 0xF) shl 4) or 0xE,
            ((stage and 7) shl 13) or ((index and 127) shl 6) or ((attribute and 3) shl 4) or 0xE,
            0x000E,
            0x000E,
            0x000E,
            0x000E,
        )
    }

    private fun dmcBlock(version: Int, index: Int): String {
        val words = IntArray(VpetProtocol.COLOR_BLOCK_WORDS)
        words[0] = 0x4744
        words[1] = 0x4C43
        if (version in 1..5) {
            words[2] = 0
            words[3] = version - 1
        } else {
            // VIP versions (17..19) are encoded through word 2, as lector.js decodes them.
            words[2] = version
            words[3] = 5
        }
        words[4] = index and 0xFFFF
        words[5] = SIMULATED_POWER
        return words.joinToString("") { VpetProtocol.hex4(it) }
    }

    private fun pencBlock(version: Int, index: Int): String {
        val words = IntArray(VpetProtocol.COLOR_BLOCK_WORDS)
        words[0] = 0x4744
        words[1] = 0x4470
        words[2] = 1 // a non-18 value: 18 marks a battle-result block
        words[3] = version and 0xFFFF
        words[4] = index and 0xFFFF
        words[5] = SIMULATED_POWER
        words[6] = 0
        words[7] = (words[0] + words[1] + words[2] + words[3] + words[4] + words[5] + words[6]) and 0xFFFF
        return words.joinToString("") { VpetProtocol.hex4(it) }
    }

    /**
     * Nibble-checksum digit that makes a response packet set valid for the given modulus
     * (0 DM20, 12 Pen20, 8 PenZ); the last word is expected to be zero before the OR.
     */
    private fun responseChecksumDigit(words: IntArray, modulus: Int): Int {
        val sum = VpetProtocol.nibbleSum(words)
        return (((modulus - (sum % 16)) % 16) + 16) % 16
    }

    private fun tableEntry(model: VpetModel, version: Int, index: Int): VpetListEntry? = when (model) {
        VpetModel.DM20 -> VpetTables.entry(
            if (VpetProtocol.PEN20_VERSIONS.containsKey(version)) "pen20" else "dm20",
            index,
        )

        VpetModel.PEN20 -> VpetTables.entry("pen20", index)
        VpetModel.PENZ -> VpetTables.entry(if (version <= 8) "z1" else "z2", index)
            ?: if (version > 8 && index in 78..90) VpetTables.entry("z1", index) else null

        VpetModel.DMX -> if (index > 0) VpetTables.entry("x${version / 2}", index) else null
        VpetModel.DMC, VpetModel.PENC -> null
    }

    /**
     * Extracts the version the session embedded in a `V2-…` DigiROM (null for anything else,
     * including Color commands).
     */
    private fun requestedVersion(model: VpetModel, command: String): Int? {
        if (!command.startsWith("V2-")) return null
        val words = command.split('-').drop(1).map { part -> part.toIntOrNull(16) ?: return null }
        return when (model) {
            VpetModel.DM20 -> if (words.size > 2) (words[2] shr 4) and 0xF else null
            VpetModel.DMX, VpetModel.PEN20, VpetModel.PENZ ->
                if (words.isNotEmpty()) (words[0] shr 4) and 0xF else null
            VpetModel.DMC, VpetModel.PENC -> null
        }
    }

    /** Keeps the simulated device version inside the range the model's decoder accepts. */
    private fun normalizeVersion(model: VpetModel, version: Int): Int = when (model) {
        VpetModel.DM20, VpetModel.DMX -> version and 0xF
        VpetModel.PEN20 -> if (VpetProtocol.PEN20_VERSIONS.containsKey(version)) version else 6
        VpetModel.PENZ -> if (VpetProtocol.PENZ_VERSIONS.containsKey(version)) version else 6
        VpetModel.DMC -> if (version in 1..5 || version in 17..19) version else 1
        VpetModel.PENC -> if (version in 0..6) version else 0
    }
}
