package com.tamerslab.hardware.vpet

/**
 * Constants and small helpers shared by the ported lector.js protocol code.
 *
 * Everything here is a faithful transcription of the recovered JavaScript; the golden vectors
 * in `src/test/resources/vpet-golden.txt` are generated from the original implementation and
 * pin these values.
 */
internal object VpetProtocol {

    /** Header of a Digital Monster Color data block (`47444C43` = "DMCC" little endian). */
    const val DMC_HEADER = "47444C43"

    /** Header of a Pendulum Color data block (`47444470`). */
    const val PENC_HEADER = "47444470"

    /**
     * Fixed "rival" battle code used by lector.js to request a Digital Monster Color dump
     * (before the intentional `0xFFFF` XOR of the last four digits, see [DMC_COMMAND]).
     */
    const val DMC_RIVAL =
        "C1-47444C4300000000000B007300019406-47444C4300020002000000000000938B"

    /** Same rival code for the Pendulum Color (un-XORed form). */
    const val PENC_RIVAL =
        "C1-4744447000000000000B007300018C33-47444470000200020000000000008BB8"

    /** `romDMC()` from lector.js: the DMC rival code with the last four hex digits XOR 0xFFFF. */
    val DMC_COMMAND: String = colorCommand("C1", DMC_RIVAL)

    /**
     * `romPenC()` from lector.js: `C2-` order ("the VPet sends first"), same block as
     * [PENC_RIVAL] with the last four hex digits XOR 0xFFFF.
     */
    val PENC_COMMAND: String = colorCommand("C2", PENC_RIVAL)

    /** Pendulum Ver.20th versions 6..9 and their body colors (lector.js `PEN20_VER`). */
    val PEN20_VERSIONS: Map<Int, String> = linkedMapOf(
        6 to "Silver Black",
        7 to "Silver Blue",
        8 to "Dukemon Color",
        9 to "Beelzebumon Color",
    )

    /** Pendulum Z versions 6..11 and their body colors (lector.js `PENZ_VER`). */
    val PENZ_VERSIONS: Map<Int, String> = linkedMapOf(
        6 to "Nature Spirits Z",
        7 to "Deep Savers Z",
        8 to "Nightmare Soldiers Z",
        9 to "Wind Guardians Z",
        10 to "Metal Empire Z",
        11 to "Virus Busters Z",
    )

    /**
     * DMC "VIP" (version 17-19) index remapping from lector.js: for each shared species number
     * (first key part) the list of Color versions whose table contains it.
     */
    val DMC_VIP_VERSIONS: Map<String, List<Int>> = linkedMapOf(
        "17-17" to listOf(1, 2),
        "17-18" to listOf(4, 5),
        "18-18" to listOf(3, 4),
        "19-17" to listOf(3, 5),
    )

    /** lector.js `hex4`: four uppercase hex digits. */
    fun hex4(value: Int): String = (value and 0xFFFF).toString(16).uppercase().padStart(4, '0')

    /** lector.js `padStart(2, "0")` for table keys. */
    fun pad2(value: Int): String = value.toString().padStart(2, '0')

    /** lector.js `nib`: sum of every nibble of every 16-bit word. */
    fun nibbleSum(words: List<Int>): Int {
        var total = 0
        for (word in words) total += nibbleSum(word)
        return total
    }

    /** Same as [nibbleSum] for the fixed-size word arrays used by the DigiROM builders. */
    fun nibbleSum(words: IntArray): Int {
        var total = 0
        for (word in words) total += nibbleSum(word)
        return total
    }

    private fun nibbleSum(word: Int): Int =
        ((word shr 12) and 0xF) + ((word shr 8) and 0xF) + ((word shr 4) and 0xF) + (word and 0xF)

    /**
     * lector.js `palabras(h)`: splits a 16-byte Color block into its 8 big-endian words.
     * Returns null when the text is shorter than the 32 hex digits a full block needs.
     */
    fun parseColorWords(blockHex: String): List<Int>? {
        if (blockHex.length < COLOR_BLOCK_HEX_LENGTH) return null
        val words = ArrayList<Int>(COLOR_BLOCK_WORDS)
        var offset = 0
        while (offset < COLOR_BLOCK_HEX_LENGTH) {
            val word = blockHex.substring(offset, offset + 4).toIntOrNull(16) ?: return null
            words.add(word)
            offset += 4
        }
        return words
    }

    private fun colorCommand(prefix: String, rival: String): String {
        val parts = rival.split('-')
        require(parts.size == 3) { "Malformed Color rival command: $rival" }
        val firstBlock = parts[1]
        val secondBlock = parts[2]
        require(firstBlock.length == COLOR_BLOCK_HEX_LENGTH && secondBlock.length == COLOR_BLOCK_HEX_LENGTH) {
            "Malformed Color rival command: $rival"
        }
        // lector.js roto2(): the last four digits of the second block are XOR-ed with 0xFFFF.
        val xoredTail = (secondBlock.substring(28).toInt(16) xor 0xFFFF).toString(16).uppercase().padStart(4, '0')
        return "$prefix-$firstBlock-${secondBlock.substring(0, 28)}$xoredTail"
    }

    /** Number of hex digits in one 16-byte Color block. */
    const val COLOR_BLOCK_HEX_LENGTH = 32

    /** Number of 16-bit words in one Color block. */
    const val COLOR_BLOCK_WORDS = 8
}
