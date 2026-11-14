package com.tamerslab.digiline

import com.tamerslab.digiline.engine.DemandReason
import com.tamerslab.digiline.engine.DemandResolver
import com.tamerslab.digiline.model.DemandDto
import com.tamerslab.digiline.model.NegotiationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemandResolverTest {
    private fun bitsDemand(amount: Int): DemandDto = DemandDto(kind = DemandDto.KIND_BITS, amount = amount)

    private fun itemDemand(itemId: String, quantity: Int): DemandDto =
        DemandDto(kind = DemandDto.KIND_ITEM, amount = quantity, itemId = itemId)

    @Test
    fun bitsCapIsTwentyPercentOfTheWallet() {
        assertEquals(200L, DemandResolver.bitsCap(1000L))
        assertEquals(0L, DemandResolver.bitsCap(0L))
        assertEquals(0L, DemandResolver.bitsCap(-50L))
    }

    @Test
    fun affordableBitsDemandIsAccepted() {
        val check = DemandResolver.check(bitsDemand(150), playerBits = 1000L, playerItems = emptyMap(), tier = 1)
        assertTrue(check.affordable)
        assertEquals(DemandReason.AFFORDABLE, check.reason)
        assertEquals(150L, check.cappedAmount)
    }

    @Test
    fun demandsAboveTheFairCapAreRejectedWithTheCappedAmount() {
        val check = DemandResolver.check(bitsDemand(250), playerBits = 1000L, playerItems = emptyMap(), tier = 2)
        assertFalse(check.affordable)
        assertEquals(DemandReason.OVER_FAIR_CAP, check.reason)
        assertEquals(200L, check.cappedAmount)
        assertEquals(50L, check.shortfall)

        // A demand over 20 % is rejected even when the player technically holds the bits.
        val poor = DemandResolver.check(bitsDemand(60), playerBits = 100L, playerItems = emptyMap(), tier = 1)
        assertFalse(poor.affordable)
        assertEquals(DemandReason.OVER_FAIR_CAP, poor.reason)
        assertEquals(20L, poor.cappedAmount)
    }

    @Test
    fun sessionLossBudgetCapsTheWholeNegotiation() {
        val check = DemandResolver.check(
            bitsDemand(100),
            playerBits = 10_000L,
            playerItems = emptyMap(),
            tier = 1,
            bitsPaid = 250L,
        )
        assertFalse(check.affordable)
        assertEquals(DemandReason.SESSION_CAP, check.reason)
        assertEquals(50L, check.shortfall)
        assertEquals(300L, DemandResolver.maxLossBits(1))
        assertEquals(2000L, DemandResolver.maxLossBits(4))
    }

    @Test
    fun itemDemandsNeverConsumeTheLastUnit() {
        assertFalse(DemandResolver.check(itemDemand("potion", 1), 0L, mapOf("potion" to 1), 2).affordable)
        assertEquals(
            DemandReason.LAST_ITEM_PROTECTED,
            DemandResolver.check(itemDemand("potion", 1), 0L, mapOf("potion" to 1), 2).reason,
        )
        assertFalse(DemandResolver.check(itemDemand("potion", 2), 0L, mapOf("potion" to 2), 2).affordable)
        assertTrue(DemandResolver.check(itemDemand("potion", 1), 0L, mapOf("potion" to 2), 2).affordable)
        assertTrue(DemandResolver.check(itemDemand("potion", 2), 0L, mapOf("potion" to 3), 2).affordable)
    }

    @Test
    fun missingItemsAndInvalidDemandsAreReported() {
        assertEquals(
            DemandReason.MISSING_ITEM,
            DemandResolver.check(itemDemand("potion", 1), 0L, emptyMap(), 2).reason,
        )
        assertEquals(
            DemandReason.INVALID,
            DemandResolver.check(itemDemand("potion", 0), 0L, mapOf("potion" to 3), 2).reason,
        )
        assertEquals(
            DemandReason.INVALID,
            DemandResolver.check(DemandDto(kind = "macca", amount = 10), 1000L, emptyMap(), 2).reason,
        )
        assertTrue(DemandResolver.check(null, 0L, emptyMap(), 2).affordable)
    }

    @Test
    fun itemQuantityCapProtectsTheStackTail() {
        assertEquals(0, DemandResolver.itemQuantityCap(0))
        assertEquals(0, DemandResolver.itemQuantityCap(1))
        assertEquals(1, DemandResolver.itemQuantityCap(2))
        assertEquals(2, DemandResolver.itemQuantityCap(3))
        assertEquals(2, DemandResolver.itemQuantityCap(10))
    }

    @Test
    fun derivedDemandsAreDeterministicAndRespectTheBitsCap() {
        val first = DemandResolver.derive(
            tier = 2,
            personalityType = 6,
            phase = NegotiationPhase.BARGAIN,
            seed = 424242L,
            playerBits = 1000L,
        )
        val second = DemandResolver.derive(
            tier = 2,
            personalityType = 6,
            phase = NegotiationPhase.BARGAIN,
            seed = 424242L,
            playerBits = 1000L,
        )
        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(DemandDto.KIND_BITS, first!!.kind)
        assertTrue(first.amount.toLong() <= DemandResolver.bitsCap(1000L))
        assertTrue(first.amount.toLong() <= DemandResolver.tierBits(2).last)
    }

    @Test
    fun derivedItemDemandsAreOnlyUsedWhenThePlayerCanSpareTheItem() {
        val demand = DemandResolver.derive(
            tier = 1,
            personalityType = 10,
            phase = NegotiationPhase.BARGAIN,
            seed = 7L,
            playerBits = 0L,
            playerItems = mapOf("potion" to 3),
            allowedItemIds = setOf("potion"),
        )
        assertNotNull(demand)
        assertEquals(DemandDto.KIND_ITEM, demand!!.kind)
        assertEquals("potion", demand.itemId)
        assertTrue(demand.amount in 1..2)
        assertTrue(3 - demand.amount >= 1)
    }

    @Test
    fun deriveReturnsNullWhenNothingIsFairlyDemandable() {
        assertNull(
            DemandResolver.derive(
                tier = 3,
                personalityType = 6,
                seed = 9L,
                playerBits = 0L,
                playerItems = emptyMap(),
            ),
        )
        assertNull(
            DemandResolver.derive(
                tier = 3,
                personalityType = 6,
                seed = 9L,
                playerBits = 0L,
                playerItems = mapOf("potion" to 1),
                allowedItemIds = setOf("potion"),
            ),
        )
    }

    @Test
    fun paymentChecksEnforceWalletSessionCapsAndLastItem() {
        val affordable = DemandResolver.checkPayment(effects(bits = -50), 100L, emptyMap(), 2)
        assertTrue(affordable.affordable)
        assertEquals(50L, affordable.bitsCost)
        assertEquals(DemandReason.AFFORDABLE, affordable.reason)

        val poor = DemandResolver.checkPayment(effects(bits = -50), 10L, emptyMap(), 2)
        assertFalse(poor.affordable)
        assertEquals(DemandReason.INSUFFICIENT_BITS, poor.reason)

        val capped = DemandResolver.checkPayment(
            effects = effects(bits = -500),
            playerBits = 10_000L,
            playerItems = emptyMap(),
            tier = 1,
            bitsPaid = 100L,
        )
        assertFalse(capped.affordable)
        assertEquals(DemandReason.SESSION_CAP, capped.reason)

        val lastItem = DemandResolver.checkPayment(
            effects = effects(itemId = "potion", itemQuantity = 1),
            playerBits = 0L,
            playerItems = mapOf("potion" to 1),
            tier = 2,
        )
        assertFalse(lastItem.affordable)
        assertEquals(DemandReason.LAST_ITEM_PROTECTED, lastItem.reason)
    }

    @Test
    fun nonPaymentOptionsListAuthoredFreeChoices() {
        val freeNode = node(
            "demand",
            options = listOf(
                option("pay", effects = effects(bits = -60)),
                option("refuse"),
                option("beg"),
            ),
        )
        assertEquals(listOf("refuse", "beg"), DemandResolver.nonPaymentOptions(freeNode).map { it.id })
        assertEquals(
            listOf("refuse", "beg"),
            DemandResolver.affordableOptions(freeNode, playerBits = 0L, playerItems = emptyMap(), tier = 1)
                .map { it.id },
        )
    }
}
