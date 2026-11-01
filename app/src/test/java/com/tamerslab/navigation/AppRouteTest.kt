package com.tamerslab.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouteTest {
    @Test fun externalAuthDeepLinksResolveToBattles() {
        assertEquals(AppRoute.Battles, appRouteFromUri("vbhelper://auth"))
        assertEquals(AppRoute.Battles, appRouteFromUri("http://localhost:8080/authenticate?token=abc"))
        assertEquals(AppRoute.Battles, appRouteFromUri("http://127.0.0.1:8080/authenticate"))
        assertNull(appRouteFromUri("https://example.com/authenticate"))
        assertNull(appRouteFromUri(null))
    }

    @Test fun typedTransferArgumentsRemainValidated() {
        assertEquals(AppRoute.Transfer("devices", 42, null),
            TransferNavigation.route(TransferSection.DEVICES, 42))
        assertEquals(42L, TransferNavigation.character("42"))
        assertNull(TransferNavigation.character("-1"))
    }
}
