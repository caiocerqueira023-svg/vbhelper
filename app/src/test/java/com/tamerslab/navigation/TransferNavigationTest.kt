package com.tamerslab.navigation

import org.junit.Assert.*
import org.junit.Test

class TransferNavigationTest {
    @Test fun homeAndContextualTransfersShareOneDestination() {
        assertEquals(AppRoute.Transfer("saves", null, null), TransferNavigation.route())
        assertEquals(AppRoute.Transfer("devices", 42, null), TransferNavigation.route(TransferSection.DEVICES, 42))
        assertEquals(AppRoute.Transfer("storage", null, null), TransferNavigation.route(TransferSection.STORAGE))
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

