package com.github.nacabaro.vbhelper.gamesave

/**
 * Digimon World DS profile facade over [NdsStorySaveReader] and [NdsStoryProfiles.WORLD_DS].
 *
 * The shared reader does the parsing; this class keeps the stable World DS entry point, constants
 * and KDoc that existing callers, tests and the phone's registered reader list rely on. Behavior is
 * unchanged: any structurally recognizable save is reported, anomalies become record review issues,
 * dual card copies are surfaced with their integrity flag and ambiguity is never mistaken for a
 * content verdict.
 *
 * See game-save-core/SAVE_READING_STANDARD.md and WORLD_DS_RESEARCH.md for the pinned evidence.
 */
class WorldDsSaveReader : GameSaveReader {
    private val delegate = NdsStorySaveReader(NdsStoryProfiles.WORLD_DS)

    override val gameId: String = GAME_ID
    override val maxInputBytes: Int = FILE_BYTES + WRAPPER_SLACK_BYTES + WorldDsResearchInspector.DESMUME_FOOTER_BYTES
    override fun acceptsLength(length: Int): Boolean = length in SAVE_BYTES..maxInputBytes

    override fun read(bytes: ByteArray): SaveObservation = delegate.read(bytes)

    companion object {
        const val GAME_ID = "digimon_world_ds_nds"
        const val PROFILE = "world-ds-usa-drastic-raw-v1"
        const val FILE_BYTES = 65536

        /** The game's species range: the USA ROM's name table holds exactly this many entries. */
        const val SPECIES_COUNT = 426

        /** The save region the game writes to the card per copy (SAVE_SIZE in the matching decompilation). */
        const val SAVE_BYTES = 0x7DC0

        /** Card stride between the two save copies: SAVE_SIZE rounded up to the card's 0x100 page. */
        const val COPY_STRIDE = 0x7E00

        /** Observed header family: 64 KiB image behind a 0x7C-byte export header. */
        const val HEADER_BYTES = 0x7C

        /** Maximum recognized wrapper between a container edge and the image: bounded, content still decides. */
        const val WRAPPER_SLACK_BYTES = 0x400
    }
}

/** Published USA text-code facts, not UTF-16. Unknown glyphs remain explicit and exact bytes are retained. */
internal object WorldDsText {
    fun decode(codes: List<Int>): String = buildString {
        codes.forEach { code -> append(when (code) {
            in 0x50..0x69 -> 'A' + (code - 0x50)
            in 0x6A..0x83 -> 'a' + (code - 0x6A)
            in 1..10 -> '0' + (code - 1)
            0 -> ' '; 0x0B -> '/'; 0x0C -> '%'; 0x0D -> ':'; 0x0E -> '\''; 0x0F -> '"'
            0x11 -> ','; 0x12 -> '.'; 0x13 -> '!'; 0x14 -> '?'; 0x15 -> '('; 0x16 -> ')'
            0x17 -> '+'; 0x18 -> '-'; 0x19 -> '='; 0x1B -> '~'; 0x23 -> '&'
            else -> '\uFFFD'
        }) }
    }
}
