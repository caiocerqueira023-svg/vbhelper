package com.tamerslab.hardware.vpet

/**
 * DigiROM / Color block builders ported verbatim from the recovered `lector.js`
 * (`romDM20`, `romDMX`, `romPen20`, `romPenZ`, `romDMC`, `romPenC`).
 *
 * The last checksum digit of the 16-bit DigiROMs is intentionally wrong (the original comments
 * explain that this makes the VPet dump its data instead of starting a battle), so the
 * formulas must not be "fixed". The golden vectors generated from lector.js assert this.
 */
object VpetDigiRom {

    /** Command that stops/aborts the read on the adapter side (lector.js `parar()`). */
    const val STOP_COMMAND = "0"

    /** Exact lector.js command for the given model and version. */
    fun commandFor(model: VpetModel, version: Int): String = when (model) {
        VpetModel.DM20 -> romDm20(version)
        VpetModel.DMX -> romDmx(version)
        VpetModel.PEN20 -> romPen20(version)
        VpetModel.PENZ -> romPenZ(version)
        VpetModel.DMC -> VpetProtocol.DMC_COMMAND
        VpetModel.PENC -> VpetProtocol.PENC_COMMAND
    }

    /** lector.js `romDM20(ver)`: 10 words, "intentionally broken" nibble checksum. */
    internal fun romDm20(version: Int): String {
        val words = intArrayOf(
            0x0301,
            0x0D0F,
            ((version and 0xF) shl 4) or 0xE,
            (2 shl 6) or 0xE,
            (1 shl 10) or (26 shl 4) or 0xE,
            (18 shl 4) or 0xE,
            0xE,
            0xE,
            0xE,
            0xE,
        )
        words[LAST_WORD] = words[LAST_WORD] or (romChecksumDigit(words, 16) shl 12)
        return digiRom(words)
    }

    /** lector.js `romDMX(ver)`: fixed Dukemon X battle code with the VPet version embedded. */
    internal fun romDmx(version: Int): String =
        "V2-23" + (version and 0xF).toString(16).uppercase() + "E-AA2E-560E-182E-0CAE-103E"

    /** lector.js `romPen20(ver)`: 10 words, checksum digit computed against base 12. */
    internal fun romPen20(version: Int): String {
        val words = intArrayOf(
            ((version and 0xF) shl 4) or 0xE,
            (118 shl 6) or (0 shl 4) or 0xE,
            0xE,
            0xE,
            (16 shl 4) or 0xE,
            0xE,
            0xE,
            0xE,
            0xE,
            0xE,
        )
        words[LAST_WORD] = words[LAST_WORD] or (romChecksumDigit(words, 12) shl 12)
        return digiRom(words)
    }

    /** lector.js `romPenZ(ver)`: 6 words, "intentionally broken" nibble checksum. */
    internal fun romPenZ(version: Int): String {
        val words = intArrayOf(
            0x030E or ((version and 0xF) shl 4),
            0x212E,
            0x288E,
            0x182E,
            0x018E,
            0x000E,
        )
        words[LAST_WORD_6] = words[LAST_WORD_6] or (romChecksumDigit(words, 16) shl 12)
        return digiRom(words)
    }

    private fun digiRom(words: IntArray): String =
        "V2-" + words.joinToString("-") { VpetProtocol.hex4(it) }

    /**
     * lector.js correction digit: `(((base - nib % 16) % 16 + 1) & 15) << 12`, OR-ed into the
     * last word (which already carries a 0xE low nibble in the ROMs above).
     */
    private fun romChecksumDigit(words: IntArray, formulaBase: Int): Int {
        val sum = VpetProtocol.nibbleSum(words)
        val digit = ((((formulaBase - (sum % 16)) % 16) + 16) % 16) + 1
        return digit and 0xF
    }

    private const val LAST_WORD = 9
    private const val LAST_WORD_6 = 5
}
