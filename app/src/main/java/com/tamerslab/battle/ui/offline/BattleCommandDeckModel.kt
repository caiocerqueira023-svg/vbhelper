package com.tamerslab.battle.ui.offline

/**
 * Stable positions for the lower battle command surface.
 *
 * The deck inherits Re:Digitize Decode's fixed command grammar: the partner's three
 * equipped techniques on the first row, Items / Special / Strategy on the second,
 * and Focus / Defend / Move on the third. Unavailable actions never disappear, so
 * muscle memory survives every combat state and team formation.
 */
internal enum class BattleCommandSlot {
    TECHNIQUE_1,
    TECHNIQUE_2,
    TECHNIQUE_3,
    ITEMS,
    SPECIAL,
    STRATEGY,
    FOCUS,
    DEFEND,
    MOVE
}

internal data class BattleCommandAvailability(
    val partnerReady: Boolean,
    val targetReady: Boolean,
    /** Exactly three entries; false/hidden techniques keep their position but stay inert. */
    val techniqueReady: List<Boolean>,
    val specialReady: Boolean,
    /** Highlights the Move key while the player is choosing an arena destination. */
    val moveArmed: Boolean = false,
)

internal data class BattleCommandDeckItem(
    val slot: BattleCommandSlot,
    val enabled: Boolean
)

internal fun battleCommandDeck(
    availability: BattleCommandAvailability
): List<BattleCommandDeckItem> = listOf(
    techniqueSlot(BattleCommandSlot.TECHNIQUE_1, availability, 0),
    techniqueSlot(BattleCommandSlot.TECHNIQUE_2, availability, 1),
    techniqueSlot(BattleCommandSlot.TECHNIQUE_3, availability, 2),
    BattleCommandDeckItem(BattleCommandSlot.ITEMS, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.SPECIAL, availability.partnerReady && availability.specialReady),
    BattleCommandDeckItem(BattleCommandSlot.STRATEGY, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.FOCUS, availability.partnerReady && availability.targetReady),
    BattleCommandDeckItem(BattleCommandSlot.DEFEND, availability.partnerReady),
    BattleCommandDeckItem(BattleCommandSlot.MOVE, availability.partnerReady)
)

private fun techniqueSlot(
    slot: BattleCommandSlot,
    availability: BattleCommandAvailability,
    index: Int
): BattleCommandDeckItem = BattleCommandDeckItem(
    slot = slot,
    enabled = availability.partnerReady && availability.techniqueReady.getOrElse(index) { false }
)
