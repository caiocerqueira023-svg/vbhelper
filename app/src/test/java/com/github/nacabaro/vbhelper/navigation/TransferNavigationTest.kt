package com.github.nacabaro.vbhelper.navigation

import org.junit.Assert.*
import org.junit.Test

class TransferNavigationTest {
    @Test fun homeAndContextualTransfersShareOneDestination() {
        assertEquals("Transfer?section=saves", TransferNavigation.route())
        assertEquals("Transfer?section=devices&characterId=42", TransferNavigation.route(TransferSection.DEVICES, 42))
        assertEquals("Transfer?section=storage", TransferNavigation.route(TransferSection.STORAGE))
    }
    @Test fun invalidArgumentsCannotSelectAnArbitraryPartnerOrTab() {
        assertEquals(TransferSection.SAVES, TransferNavigation.section("unknown"))
        assertEquals(TransferSection.SAVES, TransferNavigation.section(null))
        assertNull(TransferNavigation.character("-1"))
        assertNull(TransferNavigation.character("other"))
        assertEquals(42L, TransferNavigation.character("42"))
        assertThrows(IllegalArgumentException::class.java) { TransferNavigation.route(TransferSection.DEVICES, -1) }
    }
}
