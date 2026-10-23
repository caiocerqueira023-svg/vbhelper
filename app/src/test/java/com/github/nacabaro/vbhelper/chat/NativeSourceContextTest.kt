package com.github.nacabaro.vbhelper.chat

import org.junit.Assert.*
import org.junit.Test

class NativeSourceContextTest {
    @Test fun sourceFactsRemainScopedAndDoNotBecomeInventedGameplayOrMemories() {
        val context = NativeSourceContext.format(NativeSourceFacts("Game", 123, "Name", 10, 42, 999999, 16, 1234, 2.5f, listOf(11), null), "en")
        assertTrue(context.contains("\"nativeLevel\":42"))
        assertTrue(context.contains("not app stage/Vital Points"))
        assertTrue(context.contains("partial undated evidence"))
        assertTrue(context.contains("Do not invent battle memories"))
        assertFalse(context.contains("password"))
    }
}
