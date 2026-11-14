package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.DemandDto
import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.NegotiationEffectsDto
import com.tamerslab.digiline.model.NegotiationNodeDto
import com.tamerslab.digiline.model.NegotiationOptionDto
import com.tamerslab.digiline.model.NegotiationPhase
import kotlin.random.Random

/** Why a demand/payment is or is not acceptable to the player (diagnostics + UI wording hooks). */
enum class DemandReason {
    NO_DEMAND,
    AFFORDABLE,
    INSUFFICIENT_BITS,
    OVER_FAIR_CAP,
    MISSING_ITEM,
    LAST_ITEM_PROTECTED,
    SESSION_CAP,
    INVALID,
}

/** Result of checking one authored [DemandDto] against the player's wallet (plan §7.4). */
data class DemandCheck(
    val demand: DemandDto? = null,
    val affordable: Boolean = true,
    val reason: DemandReason = DemandReason.NO_DEMAND,
    val cappedAmount: Long = 0,
    val quantity: Int = 0,
    val shortfall: Long = 0,
)

/** Result of checking one option's [NegotiationEffectsDto] payment against wallet + session loss caps. */
data class PaymentCheck(
    val affordable: Boolean,
    val reason: DemandReason,
    val bitsCost: Long = 0,
    val itemId: String? = null,
    val itemQuantity: Int = 0,
)

/**
 * Demand derivation, affordability and fair-play caps for wild negotiations (plan §7.3–§7.5).
 *
 * Hard rules implemented here:
 * - bits demands are capped at ≤20 % of the player's current bits and at a per-tier session loss
 *   budget; they are never negative;
 * - item demands only use allowed item ids and never consume the last item of a stack;
 * - a non-paying branch is always discoverable from a demand node (fallback helper
 *   [nonPaymentOptions]) so an unaffordable demand can never soft-lock the conversation;
 * - everything is derived from the persisted negotiation seed, so a resumed session sees the same
 *   demand (no reroll).
 */
object DemandResolver {
    const val BITS_KIND: String = DemandDto.KIND_BITS
    const val ITEM_KIND: String = DemandDto.KIND_ITEM
    const val MAX_ITEM_QUANTITY: Int = 2
    const val FAIR_BITS_DENOMINATOR: Long = 5L
    const val MIN_DEMAND_BITS: Long = 10L

    private val TIER_MIN_TYPE = 1
    private val TIER_MAX_TYPE = 4

    // Base authored demand bands per tier (plan §7.3: higher tiers ask for more, not impossible).
    private val TIER_BITS_MIN = mapOf(1 to 40L, 2 to 100L, 3 to 250L, 4 to 500L)
    private val TIER_BITS_MAX = mapOf(1 to 150L, 2 to 350L, 3 to 700L, 4 to 1200L)

    // Per-session fair-play loss budgets (plan §7.5): a single negotiation can never drain more.
    private val TIER_LOSS_BITS = mapOf(1 to 300L, 2 to 700L, 3 to 1200L, 4 to 2000L)
    private val TIER_LOSS_ITEMS = mapOf(1 to 1, 2 to 2, 3 to 3, 4 to 4)

    fun normalizeTier(tier: Int): Int = tier.coerceIn(TIER_MIN_TYPE, TIER_MAX_TYPE)

    /** Maximum bits a demand may ask for: 20 % of what the player currently holds. */
    fun bitsCap(playerBits: Long): Long = playerBits.coerceAtLeast(0L) / FAIR_BITS_DENOMINATOR

    fun maxLossBits(tier: Int): Long = TIER_LOSS_BITS.getValue(normalizeTier(tier))

    fun maxLossItems(tier: Int): Int = TIER_LOSS_ITEMS.getValue(normalizeTier(tier))

    /** Authored bits band for a tier (inclusive). */
    fun tierBits(tier: Int): LongRange {
        val normalized = normalizeTier(tier)
        val minimum = TIER_BITS_MIN.getValue(normalized)
        val maximum = TIER_BITS_MAX.getValue(normalized)
        return minimum..maximum
    }

    /**
     * Largest quantity of [count] owned items that may be demanded without consuming the stack's
     * last unit. `0` means "do not demand this item".
     */
    fun itemQuantityCap(count: Int): Int {
        if (count <= 1) return 0
        return minOf(MAX_ITEM_QUANTITY, count - 1)
    }

    /**
     * Derives the demand for a negotiation node deterministically from `(tier, personality, phase,
     * seed, wallet)`. Returns `null` when the player cannot support any fair demand (the node then
     * has to use a non-paying branch, which the validator/content guarantees).
     */
    fun derive(
        tier: Int,
        personalityType: Int,
        phase: NegotiationPhase? = null,
        seed: Long,
        playerBits: Long,
        playerItems: Map<String, Int> = emptyMap(),
        allowedItemIds: Set<String>? = null,
        preferItem: Boolean = false,
    ): DemandDto? {
        val normalizedTier = normalizeTier(tier)
        val random = Random(
            DigilineSeed.combine(
                seed,
                normalizedTier.toLong(),
                personalityType.toLong(),
                (phase ?: NegotiationPhase.APPROACH).ordinal.toLong(),
            ),
        )
        val band = tierBits(tier = normalizedTier)
        val span = band.last - band.first + 1L
        val rawBits = band.first + random.nextLong(span)
        val fairCap = bitsCap(playerBits)
        val bits = minOf(rawBits, fairCap)
        val itemDemand = pickItemDemand(random, playerItems, allowedItemIds)
        if ((preferItem || bits < MIN_DEMAND_BITS) && itemDemand != null) return itemDemand
        if (bits >= MIN_DEMAND_BITS) {
            return DemandDto(kind = BITS_KIND, amount = bits.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        return itemDemand
    }

    /**
     * Checks an authored [demand] against the wallet and the tier's fair-play budget.
     * `bitsPaid`/`itemsPaid` are the amounts already paid in this negotiation session.
     */
    fun check(
        demand: DemandDto?,
        playerBits: Long,
        playerItems: Map<String, Int>,
        tier: Int,
        bitsPaid: Long = 0,
        itemsPaid: Int = 0,
    ): DemandCheck {
        if (demand == null) return DemandCheck(null, affordable = true, reason = DemandReason.NO_DEMAND)
        val normalizedTier = normalizeTier(tier)
        val safeBits = playerBits.coerceAtLeast(0L)
        return when (demand.kind.trim().lowercase()) {
            BITS_KIND -> checkBitsDemand(demand, safeBits, normalizedTier, bitsPaid)
            ITEM_KIND -> checkItemDemand(demand, playerItems, normalizedTier, itemsPaid)
            else -> DemandCheck(demand, affordable = false, reason = DemandReason.INVALID)
        }
    }

    /**
     * Checks the payment wrapped in one option's effects. `playerBits`/`playerItems` may be `null`
     * when the caller cannot observe them yet — session caps are still enforced because they only
     * depend on the negotiation state.
     */
    fun checkPayment(
        effects: NegotiationEffectsDto,
        playerBits: Long?,
        playerItems: Map<String, Int>?,
        tier: Int,
        bitsPaid: Long = 0,
        itemsPaid: Int = 0,
    ): PaymentCheck {
        val bitsCost = effects.bitsCost
        val itemId = effects.itemId?.takeIf { it.isNotBlank() }
        val quantity = effects.itemQuantity.coerceAtLeast(0)
        if (effects.itemQuantity > 0 && itemId == null) {
            return PaymentCheck(false, DemandReason.INVALID, bitsCost, null, quantity)
        }
        if (bitsPaid + bitsCost > maxLossBits(tier)) {
            return PaymentCheck(false, DemandReason.SESSION_CAP, bitsCost, itemId, quantity)
        }
        if (itemsPaid + quantity > maxLossItems(tier)) {
            return PaymentCheck(false, DemandReason.SESSION_CAP, bitsCost, itemId, quantity)
        }
        if (playerBits != null && bitsCost > playerBits.coerceAtLeast(0L)) {
            return PaymentCheck(false, DemandReason.INSUFFICIENT_BITS, bitsCost, itemId, quantity)
        }
        if (quantity > 0 && itemId != null && playerItems != null) {
            val owned = playerItems[itemId] ?: 0
            if (owned <= 0) {
                return PaymentCheck(false, DemandReason.MISSING_ITEM, bitsCost, itemId, quantity)
            }
            if (owned - quantity < 1) {
                return PaymentCheck(false, DemandReason.LAST_ITEM_PROTECTED, bitsCost, itemId, quantity)
            }
        }
        return PaymentCheck(true, DemandReason.AFFORDABLE, bitsCost, itemId, quantity)
    }

    /** Options that do not cost the player anything (the guaranteed exit/non-payment branch). */
    fun nonPaymentOptions(node: NegotiationNodeDto): List<NegotiationOptionDto> =
        node.options.filter { !it.effects.hasPayment }

    /** Options payable with the current wallet and session budget; empty when none are payable. */
    fun affordableOptions(
        node: NegotiationNodeDto,
        playerBits: Long?,
        playerItems: Map<String, Int>?,
        tier: Int,
        bitsPaid: Long = 0,
        itemsPaid: Int = 0,
    ): List<NegotiationOptionDto> = node.options.filter { option ->
        checkPayment(option.effects, playerBits, playerItems, tier, bitsPaid, itemsPaid).affordable
    }

    private fun checkBitsDemand(
        demand: DemandDto,
        playerBits: Long,
        tier: Int,
        bitsPaid: Long,
    ): DemandCheck {
        val amount = demand.amount.toLong()
        if (amount <= 0L) return DemandCheck(demand, affordable = true, reason = DemandReason.AFFORDABLE)
        val fairCap = bitsCap(playerBits)
        val sessionBudget = maxLossBits(tier)
        if (bitsPaid + amount > sessionBudget) {
            return DemandCheck(
                demand = demand,
                affordable = false,
                reason = DemandReason.SESSION_CAP,
                cappedAmount = minOf(amount, fairCap),
                shortfall = bitsPaid + amount - sessionBudget,
            )
        }
        if (amount > fairCap) {
            return DemandCheck(
                demand = demand,
                affordable = false,
                reason = DemandReason.OVER_FAIR_CAP,
                cappedAmount = fairCap,
                shortfall = amount - fairCap,
            )
        }
        if (amount > playerBits) {
            return DemandCheck(
                demand = demand,
                affordable = false,
                reason = DemandReason.INSUFFICIENT_BITS,
                cappedAmount = fairCap,
                shortfall = amount - playerBits,
            )
        }
        return DemandCheck(demand, affordable = true, reason = DemandReason.AFFORDABLE, cappedAmount = amount)
    }

    private fun checkItemDemand(
        demand: DemandDto,
        playerItems: Map<String, Int>,
        tier: Int,
        itemsPaid: Int,
    ): DemandCheck {
        val itemId = demand.itemId?.takeIf { it.isNotBlank() }
        val quantity = demand.amount
        if (itemId == null || quantity <= 0) {
            return DemandCheck(demand, affordable = false, reason = DemandReason.INVALID)
        }
        if (itemsPaid + quantity > maxLossItems(tier)) {
            return DemandCheck(demand, affordable = false, reason = DemandReason.SESSION_CAP, quantity = quantity)
        }
        val owned = playerItems[itemId] ?: 0
        if (owned <= 0) {
            return DemandCheck(demand, affordable = false, reason = DemandReason.MISSING_ITEM, quantity = quantity)
        }
        if (owned - quantity < 1) {
            return DemandCheck(
                demand = demand,
                affordable = false,
                reason = DemandReason.LAST_ITEM_PROTECTED,
                quantity = itemQuantityCap(owned).coerceAtMost(quantity),
            )
        }
        return DemandCheck(demand, affordable = true, reason = DemandReason.AFFORDABLE, quantity = quantity)
    }

    private fun pickItemDemand(
        random: Random,
        playerItems: Map<String, Int>,
        allowedItemIds: Set<String>?,
    ): DemandDto? {
        if (allowedItemIds == null) return null
        val candidates = playerItems.keys
            .filter { it in allowedItemIds }
            .sorted()
        if (candidates.isEmpty()) return null
        val itemId = candidates[random.nextInt(candidates.size)]
        val owned = playerItems[itemId] ?: 0
        val cap = itemQuantityCap(owned)
        if (cap <= 0) return null
        val quantity = 1 + random.nextInt(minOf(MAX_ITEM_QUANTITY, cap))
        return DemandDto(kind = ITEM_KIND, amount = quantity, itemId = itemId)
    }
}
