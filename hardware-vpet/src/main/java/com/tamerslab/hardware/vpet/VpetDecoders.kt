package com.tamerslab.hardware.vpet

/**
 * Fully decoded lector.js payload, including bookkeeping fields the public [VpetReadResult]
 * does not expose (referenced list, DMC/PenC key, warning). Internal: unit tests in this
 * module assert it against the golden vectors.
 */
internal data class VpetDecodedPayload(
    val version: Int,
    val index: Int,
    val name: String?,
    val stage: Int?,
    val attribute: Int?,
    val power: Int?,
    val payloadHex: String,
    val checksumValid: Boolean,
    val packetCount: Int,
    val listName: String? = null,
    val colorKey: String? = null,
    val warning: String? = null,
)

internal fun VpetDecodedPayload.toReadResult(model: VpetModel): VpetReadResult = VpetReadResult(
    model = model,
    version = version,
    index = index,
    name = name,
    stage = stage,
    attribute = attribute,
    power = power,
    payloadHex = payloadHex,
    checksumValid = checksumValid,
)

/** Result of decoding one 16-byte Color block (lector.js `bloque`). */
internal sealed class VpetColorBlockResult {
    data class Decoded(val payload: VpetDecodedPayload) : VpetColorBlockResult()

    /** DMC/PenC battle-result block (`w[2] == 18`): carries no Digimon, keep waiting. */
    data object Ignored : VpetColorBlockResult()

    /** PenC block whose words 0..6 do not sum up to word 7; lector.js discards it. */
    data object InvalidChecksum : VpetColorBlockResult()
}

/**
 * Decoders ported verbatim from the recovered `lector.js` (`decDM20`, `decDMX`, `decPen20`,
 * `decPenZ`, `decDMC`, `decPenC`, `decDMCcodigo`).
 *
 * All functions are pure and work on the text lines received from the adapter, so they can be
 * pinned against the JavaScript implementation with JVM unit tests.
 */
object VpetDecoders {

    /**
     * Decodes the lines collected by a read and returns the public result.
     *
     * Returns null while the collected lines do not decode for [model] (lector.js `dec*`
     * returning null / an ignored Color block / an invalid PenC checksum). Partial 16-bit
     * reads are returned with `checksumValid = false`, exactly like lector.js hands them to the
     * callback when its 30 s timeout fires.
     *
     * [version] is the version the session last requested from the VPet. The recovered
     * decoders derive the authoritative device version from the payload itself, so the argument
     * does not alter the decoded fields; it is part of the public API for session symmetry.
     */
    @Suppress("UNUSED_PARAMETER")
    fun decode(model: VpetModel, lines: List<String>, version: Int): VpetReadResult? =
        decodePayload(model, lines)?.toReadResult(model)

    internal fun decodePayload(model: VpetModel, lines: List<String>): VpetDecodedPayload? = when (model) {
        VpetModel.DM20 -> decodeDm20(lines)
        VpetModel.DMX -> decodeDmx(lines)
        VpetModel.PEN20 -> decodePen20(lines)
        VpetModel.PENZ -> decodePenz(lines)
        VpetModel.DMC -> decodeDmcLines(lines)
        VpetModel.PENC -> decodePencLines(lines)
    }

    /** Number of `r:XXXX` packets lector.js would count in [lines] (`paquetes`). */
    internal fun countPackets(lines: List<String>, onlyLowNibbleE: Boolean): Int =
        extractPackets(lines, onlyLowNibbleE).size

    internal fun extractPackets(lines: List<String>, onlyLowNibbleE: Boolean): List<Int> {
        val packets = ArrayList<Int>()
        for (line in lines) {
            for (match in PACKET_REGEX.findAll(line)) {
                val value = match.groupValues[1].toIntOrNull(16) ?: continue
                if (!onlyLowNibbleE || (value and 0xF) == 0xE) packets.add(value)
            }
        }
        return packets
    }

    // -----------------------------------------------------------------------------------
    // 16-bit packet models
    // -----------------------------------------------------------------------------------

    /** lector.js `decDM20`; versions 6..9 name the species from the Pen20 table. */
    internal fun decodeDm20(lines: List<String>): VpetDecodedPayload? {
        val packets = extractPackets(lines, onlyLowNibbleE = false).take(10)
        if (packets.size < 4) return null
        if ((packets[2] and 0xF) != 0xE || (packets[3] and 0xF) != 0xE) return null
        val version = (packets[2] shr 4) and 0xF
        val index = packets[3] shr 6
        val attribute = (packets[3] shr 4) and 3
        val power = if (packets.size > 5) (packets[5] shr 4) and 0xFF else null
        val listName = if (VpetProtocol.PEN20_VERSIONS.containsKey(version)) "pen20" else "dm20"
        val entry = VpetTables.entry(listName, index)
        return VpetDecodedPayload(
            version = version,
            index = index,
            name = entry?.name,
            stage = entry?.stage,
            attribute = attribute,
            power = power,
            payloadHex = hexWords(packets),
            checksumValid = packets.size == 10 && VpetProtocol.nibbleSum(packets) % 16 == 0,
            packetCount = packets.size,
            listName = listName,
        )
    }

    /** lector.js `decPen20`; falls back to the DM20 layout when the packets are not Pen20. */
    internal fun decodePen20(lines: List<String>): VpetDecodedPayload? {
        val packets = extractPackets(lines, onlyLowNibbleE = false).take(10)
        if (packets.size >= 2 && (packets[0] and 0xF) == 0xE && (packets[1] and 0xF) == 0xE) {
            val version = (packets[0] shr 4) and 0xF
            if (VpetProtocol.PEN20_VERSIONS.containsKey(version)) {
                val index = (packets[1] shr 6) and 0xFF
                val attribute = (packets[1] shr 4) and 3
                val power = if (packets.size > 4) (packets[4] shr 4) and 0xFF else null
                val copy = if (packets.size > 5) (packets[5] shr 14) and 3 else 0
                // copy == 2 means "copied from a DM20 / password": not a Pen20 roster species.
                val entry = if (copy == 2) null else VpetTables.entry("pen20", index)
                return VpetDecodedPayload(
                    version = version,
                    index = index,
                    name = entry?.name,
                    stage = entry?.stage,
                    attribute = attribute,
                    power = power,
                    payloadHex = hexWords(packets),
                    checksumValid = packets.size == 10 && VpetProtocol.nibbleSum(packets) % 16 == 12,
                    packetCount = packets.size,
                    listName = "pen20",
                )
            }
        }
        return decodeDm20(lines)
    }

    /** lector.js `decDMX`: keeps only packets with low nibble 0xE, no checksum. */
    internal fun decodeDmx(lines: List<String>): VpetDecodedPayload? {
        val packets = extractPackets(lines, onlyLowNibbleE = true).take(6)
        if (packets.size < 2) return null
        val version = bits(packets[0], 8, 4)
        val set = version / 2
        val stage = bits(packets[1], 0, 3)
        val index = bits(packets[1], 3, 7)
        val attribute = bits(packets[1], 10, 2)
        val listName = "x$set"
        val entry = if (index > 0) VpetTables.entry(listName, index) else null
        return VpetDecodedPayload(
            version = version,
            index = index,
            name = entry?.name,
            stage = stage,
            attribute = attribute,
            power = null,
            payloadHex = hexWords(packets),
            checksumValid = packets.size == 6,
            packetCount = packets.size,
            listName = if (index > 0) listName else null,
        )
    }

    /** lector.js `decPenZ`: Z1 for versions 6..8, Z2 for 9..11 (Jogress 78..90 shared with Z1). */
    internal fun decodePenz(lines: List<String>): VpetDecodedPayload? {
        val packets = extractPackets(lines, onlyLowNibbleE = false).take(6)
        if (packets.size < 2 || (packets[0] and 0xF) != 0xE || (packets[1] and 0xF) != 0xE) return null
        val version = (packets[0] shr 4) and 0xF
        if (!VpetProtocol.PENZ_VERSIONS.containsKey(version)) return null
        val listName = if (version <= 8) "z1" else "z2"
        val index = (packets[1] shr 6) and 127
        val attribute = (packets[1] shr 4) and 3
        val power = if (packets.size > 4) (packets[4] shr 4) and 0xFF else null
        var entry = VpetTables.entry(listName, index)
        if (entry == null && listName == "z2" && index in 78..90) entry = VpetTables.entry("z1", index)
        return VpetDecodedPayload(
            version = version,
            index = index,
            name = entry?.name,
            stage = entry?.stage,
            attribute = attribute,
            power = power,
            payloadHex = hexWords(packets),
            checksumValid = packets.size == 6 && VpetProtocol.nibbleSum(packets) % 16 == 8,
            packetCount = packets.size,
            listName = listName,
        )
    }

    // -----------------------------------------------------------------------------------
    // Color block models
    // -----------------------------------------------------------------------------------

    /** lector.js `decDMC`: one 16-byte block, no checksum, "VIP" version fallback. */
    internal fun decodeDmcBlock(blockHex: String): VpetDecodedPayload? {
        val words = VpetProtocol.parseColorWords(blockHex) ?: return null
        val versionRaw = if (words[3] <= 4) words[3] else maxOf(0, words[2] - 1)
        val index = words[4]
        val version = versionRaw + 1
        var key = VpetProtocol.pad2(version) + "-" + VpetProtocol.pad2(index)
        var warning: String? = null
        var entry = VpetTables.keyedEntry("dmc", key)
        if (entry == null) {
            // Numbers that exist in another "VIP" Color version (17..19): lector.js picks the
            // key whose version list contains the reported version.
            val candidates = VpetProtocol.DMC_VIP_VERSIONS.keys
                .filter { it.substringBefore('-').toIntOrNull() == index }
            val matching = candidates.filter { version in VpetProtocol.DMC_VIP_VERSIONS.getValue(it) }
            val pick = (if (matching.isNotEmpty()) matching else candidates).firstOrNull()
            if (pick != null) {
                if (matching.isEmpty()) warning = "No. $index does not fit version $version"
                key = pick
                entry = VpetTables.keyedEntry("dmc", key)
            }
        }
        return VpetDecodedPayload(
            version = version,
            index = index,
            name = entry?.name,
            stage = entry?.stage,
            attribute = entry?.attribute,
            power = words[5],
            payloadHex = hexWords(words),
            checksumValid = true,
            packetCount = 1,
            colorKey = key,
            warning = warning,
        )
    }

    /**
     * lector.js `decDMCcodigo`: decodes a full `C1-<block>-<block>` code printed by the adapter
     * (only the DMC header is accepted).
     */
    internal fun decodeDmcCode(command: String): VpetDecodedPayload? {
        val match = DMC_CODE_REGEX.find(command) ?: return null
        val firstBlock = match.groupValues[1]
        if (!firstBlock.take(8).equals(VpetProtocol.DMC_HEADER, ignoreCase = true)) return null
        val version = firstBlock.substring(8, 16).toIntOrNull(16) ?: return null
        val index = firstBlock.substring(16, 20).toIntOrNull(16) ?: return null
        val key = VpetProtocol.pad2(version + 1) + "-" + VpetProtocol.pad2(index)
        val entry = VpetTables.keyedEntry("dmc", key)
        return VpetDecodedPayload(
            version = version + 1,
            index = index,
            name = entry?.name,
            stage = entry?.stage,
            attribute = entry?.attribute,
            power = null,
            payloadHex = "${match.groupValues[1]} ${match.groupValues[2]}",
            checksumValid = true,
            packetCount = 2,
            colorKey = key,
        )
    }

    /** lector.js `decPenC` / `bloque` for one Pendulum Color block. */
    internal fun decodePencBlock(blockHex: String): VpetColorBlockResult? {
        val words = VpetProtocol.parseColorWords(blockHex) ?: return null
        if (words[2] == 18) return VpetColorBlockResult.Ignored
        val checksum = (words[0] + words[1] + words[2] + words[3] + words[4] + words[5] + words[6]) and 0xFFFF
        val rawVersion = words[3]
        val index = words[4]
        val keyVersion = if (rawVersion <= 5) (rawVersion + 1) % 6 else rawVersion
        val key = "$keyVersion-" + VpetProtocol.pad2(index)
        val entry = VpetTables.keyedEntry("penc", key)
        val payload = VpetDecodedPayload(
            version = rawVersion,
            index = index,
            name = entry?.name,
            stage = entry?.stage,
            attribute = entry?.attribute,
            power = words[5],
            payloadHex = hexWords(words),
            checksumValid = checksum == words[7],
            packetCount = 1,
            colorKey = key,
        )
        return if (payload.checksumValid) {
            VpetColorBlockResult.Decoded(payload)
        } else {
            VpetColorBlockResult.InvalidChecksum
        }
    }

    /** First Color data block of the collected lines, decoded (DMC never has invalid blocks). */
    internal fun decodeDmcLines(lines: List<String>): VpetDecodedPayload? {
        for (block in extractColorDataBlocks(VpetModel.DMC, lines)) {
            val payload = decodeDmcBlock(block)
            if (payload != null) return payload
        }
        // Fallback: the adapter may print a complete C1 code that is not the one we sent
        // (lector.js lineaColor "código C1 completo que no sea el nuestro").
        val text = lines.joinToString("\n")
        val lastCode = DMC_CODE_REGEX.findAll(text).lastOrNull() ?: return null
        val command = lastCode.value
        val upper = command.uppercase()
        if (upper == VpetProtocol.DMC_COMMAND || upper == VpetProtocol.DMC_RIVAL) return null
        return decodeDmcCode(command)
    }

    /** First complete, checksum-valid PenC data block of the collected lines. */
    internal fun decodePencLines(lines: List<String>): VpetDecodedPayload? {
        for (block in extractColorDataBlocks(VpetModel.PENC, lines)) {
            when (val result = decodePencBlock(block)) {
                null -> Unit
                VpetColorBlockResult.Ignored -> Unit
                VpetColorBlockResult.InvalidChecksum -> Unit
                is VpetColorBlockResult.Decoded -> return result.payload
            }
        }
        return null
    }

    /**
     * lector.js `lineaColor` block scan: the first `r:` block after each `s:` marker, in order.
     * Duplicated `r:` lines without an `s:` marker are ignored.
     */
    internal fun extractColorDataBlocks(model: VpetModel, lines: List<String>): List<String> {
        val header = if (model == VpetModel.PENC) VpetProtocol.PENC_HEADER else VpetProtocol.DMC_HEADER
        val pattern = Regex("([sr]):\\s*($header[0-9A-Fa-f]{24})", RegexOption.IGNORE_CASE)
        val text = lines.joinToString("\n")
        val blocks = ArrayList<String>()
        var seenData = false
        for (match in pattern.findAll(text)) {
            if (match.groupValues[1].equals("s", ignoreCase = true)) {
                seenData = false
                continue
            }
            if (!seenData) {
                seenData = true
                blocks.add(match.groupValues[2])
            }
        }
        return blocks
    }

    private fun hexWords(words: List<Int>): String =
        words.joinToString(" ") { VpetProtocol.hex4(it) }

    /** lector.js `bits(n, a, l)` = `(n >> (16 - a - l)) & ((1 << l) - 1)`. */
    private fun bits(value: Int, offset: Int, length: Int): Int =
        (value shr (16 - offset - length)) and ((1 shl length) - 1)

    private val PACKET_REGEX = Regex("(?:^|\\s)r:\\s*([0-9A-Fa-f]{4})(?![0-9A-Fa-f])")

    private val DMC_CODE_REGEX = Regex("C1-([0-9A-Fa-f]{32})-([0-9A-Fa-f]{32})")
}
