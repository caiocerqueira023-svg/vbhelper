package com.github.nacabaro.vbhelper.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyRolesTest {
    @Test fun everyRoleUsesTheSelectedFamilyIncludingSecondaryCopy() {
        val family = FontFamily.Monospace
        val type = appTypography(family)
        val roles = listOf(type.displayLarge, type.displayMedium, type.displaySmall,
            type.headlineLarge, type.headlineMedium, type.headlineSmall,
            type.titleLarge, type.titleMedium, type.titleSmall,
            type.bodyLarge, type.bodyMedium, type.bodySmall,
            type.labelLarge, type.labelMedium, type.labelSmall)
        roles.forEach { assertEquals(family, it.fontFamily) }
        assertTrue(type.labelSmall.fontSize.value >= 11f)
        assertTrue(type.bodySmall.lineHeight.value > type.bodySmall.fontSize.value)
    }
}
