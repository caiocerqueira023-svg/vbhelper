package com.github.nacabaro.vbhelper.screens.offlineBattle

/**
 * Stable positions for the lower battle command surface.
 *
 * Decode's two-screen layout is useful here because unavailable actions do not
 * disappear: muscle memory survives every combat state and team formation.
 */
internal enum class BattleCommandSlot {
    TECHNIQUES,
    ITEMS,
    DEFEND,
    SUPPORT,
    SPECIAL,
    FOCUS,
    STRATEGY,
    MOVE_CLOSER,
    KEEP_DISTANCE
}

internal data class BattleCommandAvailability(
    val partnerReady: Boolean,
    val targetReady: Boolean,
    val supportReady: Boolean,
    val specialReady: Boolean
)

internal data class BattleCommandDeckItem(
    val slot: BattleCommandSlot,
    val enabled: Boolean
)

internal fun battleCommandDeck(
    availability: BattleCommandAvailability
): List<BattleCommandDeckItem> = listOf(
    BattleCommandDeckItem(BattleCommandSlot.TECHNIQUES, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.ITEMS, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.DEFEND, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.SUPPORT, availability.supportReady),
    BattleCommandDeckItem(BattleCommandSlot.SPECIAL, availability.specialReady),
    BattleCommandDeckItem(
        BattleCommandSlot.FOCUS,
        availability.partnerReady && availability.targetReady
    ),
    BattleCommandDeckItem(BattleCommandSlot.STRATEGY, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.MOVE_CLOSER, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.KEEP_DISTANCE, availability.partnerReady)
)
