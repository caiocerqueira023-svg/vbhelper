package com.tamerslab.gamesave

/**
 * Layout and identity profile for one persisted Digimon NDS Story-family save.
 *
 * A profile describes bytes, never a product decision: container geometry, the two card
 * copies and their integrity header, the individual-record region and every record field
 * window. World DS is the first profile; Dawn/Dusk will be a second one (see
 * DAWN_DUSK_TRANSFER_PLAN.md). Generic [NdsStorySaveReader], [NdsStoryRecordCodec],
 * [NdsStoryResearchInspector] and [NdsStoryCopySelection] behavior is driven entirely by
 * these values, while the WorldDs* objects remain facades over the DS1 profile so existing
 * callers and tests keep their contract.
 *
 * Evidence stays per profile: this class only carries values that were verified for that
 * layout. It must never be filled with guesses from a sibling game.
 */
data class NdsStoryProfile(
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    /** Stable display/debug key for the layout family, e.g. "world-ds-usa-raw". */
    val layoutId: String,
    /** Evidence label used by aggregate research summaries (e.g. the observed game code). */
    val layoutEvidence: String,
    val fileBytes: Int,
    val saveBytes: Int,
    val copyStride: Int,
    val copyCount: Int,
    val wrapperSlackBytes: Int,
    val imageStep: Int,
    val copyHeader: NdsStoryCopyHeaderRule,
    /** Optional game-written magic inside a copy; corroboration only, never the recognition rule. */
    val signature: List<Byte>?,
    val recordRegion: NdsStoryRecordRegion,
    val partyReferenceBase: Int,
    val partySlots: Int,
    val partyVisibleCap: Int,
    /** Farm table, when the save carries one. Null when the layout keeps no farm data in the save. */
    val farm: NdsStoryFarmLayout?,
    val item: NdsStoryItemLayout?,
    val miscReferenceOffset: Int?,
    /**
     * Bit mask of the record's live ownership/state word that means "not an ordinary owned record".
     * Zero means the layout's flag semantics are not established, so no ownership claim is made.
     */
    val liveFlagBits: Int = 0,
    /**
     * Whether recognition must see the game-written signature. Real World DS saves lack theirs, so
     * the DS1 profile stays structural; Dawn/Dusk saves always carry "DSDIG2", which also keeps the
     * two families from decoding each other's bytes.
     */
    val requireSignature: Boolean = false,
    /**
     * Whether a counter tie between copies holding the same individuals is resolved automatically.
     * The Dawn/Dusk engine can produce such a tie by writing counter 1 into an erased slot; World DS
     * keeps its previous "no automatic choice on a tie" behaviour.
     */
    val resolveEquivalentCopyTie: Boolean = false,
    val lastIdOffset: Int,
    val idReserved: Long,
    val emptyRecordId: Long,
    val speciesCount: Int,
    val record: NdsStoryRecordLayout,
    val speciesData: NdsStorySpeciesData,
    val text: NdsStoryTextCodec,
    /** Whether a native writer/editor profile for this layout exists in the app layer. */
    val nativeWriterAvailable: Boolean = false,
    /**
     * Whether farm residents are part of the native transfer scope. Only a layout whose editor
     * clears the farm reference (Lost Evolution) sets this; World DS/Dawn-Dusk keep farm data
     * inspection-only until their farm semantics are decided.
     */
    val farmRecordsMovable: Boolean = false,
)

data class NdsStoryCopyHeaderRule(
    /** Digest algorithm over the copy with the header window zeroed, e.g. MD5. */
    val digest: String,
    val headerBytes: Int,
    /** Offset of the per-copy write sequence inside a copy. */
    val sequenceOffset: Int,
    val sequenceBytes: Int,
)

data class NdsStoryRecordRegion(val base: Int, val stride: Int, val count: Int)

data class NdsStoryFarmLayout(
    val base: Int,
    val groups: Int,
    val stride: Int,
    val residents: Int,
    val residentOffset: Int,
    val lastVisitorOffset: Int,
)

data class NdsStoryItemLayout(val base: Int, val slots: Int, val stride: Int, val idOffset: Int)

/** Every window of one native individual record. Values are layout facts, not policy. */
data class NdsStoryRecordLayout(
    val bytes: Int,
    val nameSlots: Int,
    val nameTerminator: Int,
    val formSlots: Int,
    val skillSlots: Int,
    val abilitySlots: Int,
    val resistanceSlots: Int,
    val expChannels: Int,
    val unk70Native: Int,
    val unk108Native: Int,
    val offsetLocalId: Int,
    val offsetSpeciesId: Int,
    val offsetName: Int,
    val offsetUnk1A: Int,
    val offsetExpCurve: Int,
    val offsetGrowth: Int,
    val offsetUnk1D: Int,
    val offsetStage: Int,
    val offsetAbilities: Int,
    val offsetAbilitySources: Int,
    val offsetHp: Int,
    val offsetMaxHp: Int,
    val offsetMp: Int,
    val offsetMaxMp: Int,
    val offsetAttack: Int,
    val offsetDefense: Int,
    val offsetSpirit: Int,
    val offsetSpeed: Int,
    val offsetUnk50: Int,
    val offsetMaxLevel: Int,
    val offsetSkills: Int,
    val offsetUnk70: Int,
    val offsetLevel: Int,
    val offsetExp: Int,
    val offsetExpToNext: Int,
    val offsetHistory: Int,
    val offsetUnk100: Int,
    val offsetFlags: Int,
    val offsetUnk108: Int,
    val historyUnk0: Int,
    val historyForms: Int,
    val historyStage: Int,
    val historyUnk4F: Int,
    val historyUnk50: Int,
    val historyResistances: Int,
) {
    val maxNameChars: Int get() = nameSlots - 1
}

/** Species-derived stage/curve tables. World DS and later titles answer these differently. */
interface NdsStorySpeciesData {
    fun nativeStage(speciesId: Long): Int
    fun recordStage(speciesId: Long): Int
    fun expCurve(speciesId: Long): Int
    fun growth(speciesId: Long): Int
    fun expToNext(curve: Int, level: Int, maxLevel: Int): Int
}

data class NdsStoryNameEncoding(val codes: List<Int>, val fallback: Boolean, val truncated: Boolean)

/** Game glyph codes. A display name never maps a species; decoding only reproduces stored text. */
interface NdsStoryTextCodec {
    fun decode(codes: List<Int>): String
    fun encode(text: String, maxChars: Int): NdsStoryNameEncoding
}

/** The published USA World DS text-code facts mirrored by [WorldDsText]. */
object Ds1TextCodec : NdsStoryTextCodec {
    override fun decode(codes: List<Int>): String = WorldDsText.decode(codes)

    override fun encode(text: String, maxChars: Int): NdsStoryNameEncoding {
        val codes = mutableListOf<Int>()
        var fallback = false
        for (char in text) {
            if (codes.size == maxChars) break
            val code = glyphCode(char)
            if (code == null) fallback = true else codes += code
        }
        return NdsStoryNameEncoding(codes, fallback, text.length > maxChars)
    }

    private fun glyphCode(char: Char): Int? = when (char) {
        in 'A'..'Z' -> 0x50 + (char - 'A')
        in 'a'..'z' -> 0x6A + (char - 'a')
        in '0'..'9' -> 1 + (char - '0')
        ' ' -> 0x00; '/' -> 0x0B; '%' -> 0x0C; ':' -> 0x0D; '\'' -> 0x0E; '"' -> 0x0F
        ',' -> 0x11; '.' -> 0x12; '!' -> 0x13; '?' -> 0x14; '(' -> 0x15; ')' -> 0x16
        '+' -> 0x17; '-' -> 0x18; '=' -> 0x19; '~' -> 0x1B; '&' -> 0x23
        else -> null
    }
}

/** World DS species stage bands and the three ARM9 EXP curves. */
object Ds1SpeciesData : NdsStorySpeciesData {
    override fun nativeStage(speciesId: Long): Int = WorldDsSpeciesStages.nativeStage(speciesId)
    override fun recordStage(speciesId: Long): Int = WorldDsSpeciesStages.recordStage(speciesId)
    override fun expCurve(speciesId: Long): Int = WorldDsSpeciesStages.expCurve(speciesId)
    override fun growth(speciesId: Long): Int = WorldDsSpeciesStages.growth(speciesId)
    override fun expToNext(curve: Int, level: Int, maxLevel: Int): Int =
        WorldDsExpCurves.expToNext(curve, level, maxLevel)
}

/** The registered NDS Story layouts. The World DS entry is the only verified production one today. */
object NdsStoryProfiles {
    val WORLD_DS: NdsStoryProfile = NdsStoryProfile(
        gameId = WorldDsSaveReader.GAME_ID,
        formatProfile = WorldDsSaveReader.PROFILE,
        codecVersion = 1,
        layoutId = "ds1-usa-raw-v1",
        layoutEvidence = "ADNE",
        fileBytes = WorldDsSaveReader.FILE_BYTES,
        saveBytes = WorldDsSaveReader.SAVE_BYTES,
        copyStride = WorldDsSaveReader.COPY_STRIDE,
        copyCount = 2,
        wrapperSlackBytes = WorldDsSaveReader.WRAPPER_SLACK_BYTES,
        imageStep = 4,
        copyHeader = NdsStoryCopyHeaderRule(digest = "MD5", headerBytes = 16, sequenceOffset = 0x18, sequenceBytes = 4),
        signature = "DSDIGI\u0000\u0000".toByteArray(Charsets.US_ASCII).toList(),
        recordRegion = NdsStoryRecordRegion(base = 0x234, stride = 0x180, count = 40),
        partyReferenceBase = 0x20C,
        partySlots = 10,
        partyVisibleCap = 8,
        farm = NdsStoryFarmLayout(base = 0x3E40, groups = 4, stride = 0x6C, residents = 8,
            residentOffset = 0x44, lastVisitorOffset = 0x64),
        item = NdsStoryItemLayout(base = 0x43D8, slots = 8, stride = 0x0C, idOffset = 4),
        miscReferenceOffset = 0x443C,
        lastIdOffset = 0x208,
        idReserved = 0xFFFFFF00L,
        emptyRecordId = 0xFFFFFFFFL,
        speciesCount = WorldDsSaveReader.SPECIES_COUNT,
        record = ds1RecordLayout(),
        speciesData = Ds1SpeciesData,
        text = Ds1TextCodec,
        liveFlagBits = 0x36,
        nativeWriterAvailable = true,
    )

    /**
     * Digimon World Dawn (USA A3VE) and Dusk (USA A6RE). Both titles persist the identical save
     * layout, species table and EXP curves (verified against two real saves and the shared engine
     * code), so they share one profile; the app resolves which retail title a file belongs to for
     * presentation only. Evidence: analysis/ds2/DAWN_DUSK_SAVE_LAYOUT.md.
     */
    val DAWN_DUSK: NdsStoryProfile = NdsStoryProfile(
        gameId = "digimon_world_ds2_nds",
        formatProfile = "ds2-usa-raw-v1",
        codecVersion = 1,
        layoutId = "ds2-usa-raw-v1",
        layoutEvidence = "A3VE/A6RE",
        fileBytes = 65536,
        saveBytes = 0x7B2C,
        copyStride = 0x7B80,
        copyCount = 2,
        wrapperSlackBytes = 0x400,
        imageStep = 4,
        copyHeader = NdsStoryCopyHeaderRule(digest = "MD5", headerBytes = 16, sequenceOffset = 0x18, sequenceBytes = 4),
        signature = "DSDIG2\u0000\u0000".toByteArray(Charsets.US_ASCII).toList(),
        recordRegion = NdsStoryRecordRegion(base = 0x288, stride = 0x164, count = 40),
        partyReferenceBase = 0x260,
        partySlots = 10,
        partyVisibleCap = 8,
        farm = null,
        item = null,
        miscReferenceOffset = null,
        liveFlagBits = 0,
        requireSignature = true,
        resolveEquivalentCopyTie = true,
        lastIdOffset = 0x25C,
        idReserved = 0xFFFFFF00L,
        emptyRecordId = 0L,
        speciesCount = 501,
        record = ds2RecordLayout(),
        speciesData = Ds2SpeciesData,
        text = Ds2TextCodec,
        nativeWriterAvailable = true,
    )

    /** Digimon Story: Lost Evolution (Japan BLEJ with the Operation Decoded English translation). */
    val LOST_EVOLUTION: NdsStoryProfile = LostEvolution.profile()

    val all: List<NdsStoryProfile> = listOf(WORLD_DS, DAWN_DUSK, LOST_EVOLUTION)

    fun forTriple(gameId: String, formatProfile: String, codecVersion: Int): NdsStoryProfile? =
        all.firstOrNull { it.gameId == gameId && it.formatProfile == formatProfile && it.codecVersion == codecVersion }

    fun forGameId(gameId: String): NdsStoryProfile? = all.firstOrNull { it.gameId == gameId }

    /** Binding rows persist game id and profile but not the codec; every registered codec is checked. */
    fun forGameAndProfile(gameId: String, formatProfile: String): NdsStoryProfile? =
        all.firstOrNull { it.gameId == gameId && it.formatProfile == formatProfile }

    fun forObservation(observation: SaveObservation): NdsStoryProfile? =
        forTriple(observation.gameId, observation.formatProfile, observation.codecVersion)

    fun isNdsStory(observation: SaveObservation): Boolean = forObservation(observation) != null

    private fun ds1RecordLayout() = NdsStoryRecordLayout(
        bytes = 0x180, nameSlots = 9, nameTerminator = 0xFFFF, formSlots = 5, skillSlots = 7,
        abilitySlots = 4, resistanceSlots = 7, expChannels = 8, unk70Native = 33, unk108Native = 0x40,
        offsetLocalId = 0x00, offsetSpeciesId = 0x04, offsetName = 0x08, offsetUnk1A = 0x1A,
        offsetExpCurve = 0x1B, offsetGrowth = 0x1C, offsetUnk1D = 0x1D, offsetStage = 0x1E,
        offsetAbilities = 0x22, offsetAbilitySources = 0x2C, offsetHp = 0x3C, offsetMaxHp = 0x40,
        offsetMp = 0x44, offsetMaxMp = 0x46, offsetAttack = 0x48, offsetDefense = 0x4A,
        offsetSpirit = 0x4C, offsetSpeed = 0x4E, offsetUnk50 = 0x50, offsetMaxLevel = 0x52,
        offsetSkills = 0x54, offsetUnk70 = 0x70, offsetLevel = 0x74, offsetExp = 0x78,
        offsetExpToNext = 0x9C, offsetHistory = 0xA0, offsetUnk100 = 0x100, offsetFlags = 0x104,
        offsetUnk108 = 0x108, historyUnk0 = 0x00, historyForms = 0x04, historyStage = 0x4E,
        historyUnk4F = 0x4F, historyUnk50 = 0x50, historyResistances = 0x52,
    )

    /**
     * The 0x164-byte Dawn/Dusk record. Fields up to the skill list keep the World DS positions; the
     * level lives at 0x76, there are nine 32-bit EXP channels, the history block starts at 0xB0 and
     * the state word is 0x120. Verified by ARM9 decompilation (FUN_02030d44 record initialiser,
     * FUN_02030c64 add-Digimon) and by decoding two real saves.
     */
    private fun ds2RecordLayout() = NdsStoryRecordLayout(
        bytes = 0x164, nameSlots = 9, nameTerminator = 0xFFFF, formSlots = 5, skillSlots = 14,
        abilitySlots = 4, resistanceSlots = 8, expChannels = 9, unk70Native = 0x21, unk108Native = 0,
        offsetLocalId = 0x00, offsetSpeciesId = 0x04, offsetName = 0x08, offsetUnk1A = 0x1A,
        offsetExpCurve = 0x1B, offsetGrowth = 0x1C, offsetUnk1D = 0x1D, offsetStage = 0x1E,
        offsetAbilities = 0x22, offsetAbilitySources = 0x2C, offsetHp = 0x3C, offsetMaxHp = 0x40,
        offsetMp = 0x44, offsetMaxMp = 0x46, offsetAttack = 0x48, offsetDefense = 0x4A,
        offsetSpirit = 0x4C, offsetSpeed = 0x4E, offsetUnk50 = 0x50, offsetMaxLevel = 0x52,
        offsetSkills = 0x54, offsetUnk70 = 0x70, offsetLevel = 0x76, offsetExp = 0x78,
        offsetExpToNext = 0x9C, offsetHistory = 0xB0, offsetUnk100 = 0x120, offsetFlags = 0x120,
        offsetUnk108 = 0x120, historyUnk0 = 0x00, historyForms = 0x04, historyStage = 0x57,
        historyUnk4F = 0x58, historyUnk50 = 0x1A, historyResistances = 0x58,
    )
}

/** Dawn/Dusk stage bands from the ARM9 stage function FUN_02030714. */
object Ds2SpeciesData : NdsStorySpeciesData {
    const val FIRST_SPECIES = 0x41
    const val LAST_SPECIES = 0x1F4

    override fun nativeStage(speciesId: Long): Int = when (speciesId) {
        in 0x41L..0x5FL -> 0
        in 0x61L..0xA6L -> 1
        in 0xA8L..0x11EL -> 2
        in 0x120L..399L -> 3
        in 401L..0x1F4L -> 4
        else -> 0
    }

    override fun recordStage(speciesId: Long): Int = nativeStage(speciesId)

    override fun expCurve(speciesId: Long): Int = when (nativeStage(speciesId)) {
        0, 1 -> 0
        2 -> 1
        else -> 2
    }

    override fun growth(speciesId: Long): Int = when (nativeStage(speciesId)) {
        0 -> 3
        1, 2 -> 2
        else -> 1
    }

    /** The three ARM9 EXP curves (0x0211E440) hold the same values as the World DS curves. */
    override fun expToNext(curve: Int, level: Int, maxLevel: Int): Int =
        WorldDsExpCurves.expToNext(curve, level, maxLevel)
}

/** Dawn/Dusk 2-byte glyph codes: printable text is 0x01..0x6B plus accents at 0x80..0xBC. */
object Ds2TextCodec : NdsStoryTextCodec {
    private val glyphs: Map<Int, Char> = buildMap {
        put(0x00, ' ')
        for (i in 0..9) put(0x01 + i, ('0' + i))
        put(0x0B, '/'); put(0x0C, '%'); put(0x0D, ':'); put(0x0E, '\''); put(0x0F, '"')
        put(0x10, '\u00b7'); put(0x11, ','); put(0x12, '.'); put(0x13, '!'); put(0x14, '?')
        put(0x15, '('); put(0x16, ')'); put(0x17, '+'); put(0x18, '-'); put(0x19, '=')
        put(0x54, '*'); put(0x5B, '&'); put(0x69, '#'); put(0x6A, ';'); put(0x6B, '$')
        for (i in 0..25) put(0x20 + i, 'A' + i)
        for (i in 0..25) put(0x3A + i, 'a' + i)
        val accented = "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc" +
            "\u00cd\u00ce\u00cf\u0110\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d8\u00d9\u00da\u00db" +
            "\u00dc\u00dd\u00a1\u00bf\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9" +
            "\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f8" +
            "\u00f9\u00fa\u00fb\u00fc\u00fd"
        accented.forEachIndexed { index, char -> put(0x80 + index, char) }
    }
    private val codes: Map<Char, Int> = glyphs.entries.associate { (code, char) -> char to code }

    override fun decode(codes: List<Int>): String = buildString {
        codes.forEach { code -> append(glyphs[code] ?: '\uFFFD') }
    }

    override fun encode(text: String, maxChars: Int): NdsStoryNameEncoding {
        val out = mutableListOf<Int>()
        var fallback = false
        for (char in text) {
            if (out.size == maxChars) break
            val code = codes[char] ?: codes[char.uppercaseChar()]
            if (code == null) fallback = true else out += code
        }
        return NdsStoryNameEncoding(out, fallback, text.length > maxChars)
    }
}
