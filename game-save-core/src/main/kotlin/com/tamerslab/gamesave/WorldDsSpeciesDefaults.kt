package com.tamerslab.gamesave

/** Species-derived values copied from the ROM's 108-byte SpeciesData records. */
data class WorldDsRecordDefaults(
    val unk1A: Int,
    val unk50: Int,
    val ability: Int,
    val skills: List<Int>,
    val resistances: List<Int>,
    val historyUnk50: Int = 0,
)

object WorldDsSpeciesStages {
    const val ARMOR = 5
    const val ULTIMATE = 3

    fun nativeStage(speciesId: Long): Int = when (speciesId) {
        in 0L..11L -> 0
        in 12L..65L -> 1
        in 66L..129L -> 2
        in 130L..143L -> ARMOR
        in 144L..227L -> ULTIMATE
        in 228L..WorldDsSaveReader.SPECIES_COUNT.toLong() -> 4
        else -> 0
    }

    /** The game stores armor as ultimate in the individual record's stage/history windows. */
    fun recordStage(speciesId: Long): Int = nativeStage(speciesId).let { if (it == ARMOR) ULTIMATE else it }

    fun expCurve(speciesId: Long): Int = when (nativeStage(speciesId)) {
        0, 1 -> 0
        2, ARMOR -> 1
        else -> 2
    }

    fun growth(speciesId: Long): Int = when (nativeStage(speciesId)) {
        0 -> 3
        1, 2 -> 2
        else -> 1
    }
}

/** The game's three level-up EXP curves, copied from ARM9 0x02135b40. */
object WorldDsExpCurves {
    private val curves = arrayOf(
        intArrayOf(
            0, 2, 4, 6, 15, 28, 46, 68, 96, 128, 169, 201, 256, 297, 335, 366, 429, 466, 517, 559,
            646, 696, 765, 821, 940, 1006, 1099, 1174, 1334, 1421, 1544, 1658, 1853, 1986, 2212, 2344,
            2627, 2779, 3046, 3279, 3584, 3850, 4198, 4462, 4852, 5103, 5537, 5869, 6353, 6665, 7200,
            8337, 8133, 8513, 9159, 9576, 10284, 10741, 11514, 12013, 12857, 12857, 12949, 13273, 13597,
            13920, 14244, 14568, 14892, 15215, 15539, 15572, 15890, 16207, 16525, 16843, 17161, 17478,
            17796, 17945, 18260, 18224, 18533, 19151, 19768, 20386, 21004, 21622, 22548, 23475, 24402,
            25391, 26614, 27838, 30285, 33956, 37627, 41910, 48028,
        ),
        intArrayOf(
            0, 2, 5, 7, 20, 38, 61, 90, 125, 166, 215, 255, 316, 367, 403, 441, 505, 548, 594, 642,
            726, 782, 841, 902, 1011, 1082, 1157, 1235, 1375, 1465, 1559, 1658, 1835, 1947, 2148, 2276,
            2502, 2647, 2901, 3064, 3350, 3533, 3851, 4056, 4411, 4639, 5034, 5287, 5724, 6005, 6487,
            7510, 7327, 7669, 8252, 8627, 9265, 9676, 10373, 10823, 11583, 11583, 11880, 12177, 12474,
            12771, 13068, 13365, 13662, 13959, 14256, 14553, 14850, 15147, 15444, 15741, 16038, 16335,
            16632, 16929, 17226, 17523, 17820, 18414, 19008, 19602, 20196, 20790, 21681, 22572, 23463,
            24651, 25839, 27027, 29403, 32967, 36531, 40689, 46629,
        ),
        intArrayOf(
            0, 3, 6, 9, 25, 47, 76, 113, 156, 208, 264, 313, 382, 444, 480, 525, 590, 641, 683, 739,
            820, 883, 933, 1001, 1102, 1180, 1238, 1297, 1416, 1509, 1575, 1658, 1835, 1928, 2126, 2230,
            2452, 2567, 2814, 2942, 3216, 3321, 3620, 3813, 4058, 4268, 4631, 4811, 5209, 5464, 5838,
            6759, 6595, 6825, 7344, 7678, 8153, 8515, 9129, 9416, 10077, 10077, 10217, 10472, 10728,
            10855, 11108, 11360, 11476, 11726, 12118, 12370, 12771, 13026, 13282, 13537, 13953, 14211,
            14470, 14728, 15159, 15420, 15860, 16388, 17107, 17642, 18176, 18711, 19730, 20766, 21821,
            23172, 24547, 25946, 28227, 31978, 35800, 40282, 46629,
        ),
    )

    fun expToNext(curve: Int, level: Int, maxLevel: Int): Int {
        if (level < 1 || level >= 99 || maxLevel <= level) return 0
        return curves[curve.coerceIn(0, curves.lastIndex)][level]
    }
}
