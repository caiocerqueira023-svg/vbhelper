package com.github.nacabaro.vbhelper.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSurfaceContrastTest {
    @Test fun infoCardAndTonalSurfacesStayReadable() {
        for (theme in AppTheme.entries) {
            val scheme = theme.palette.colorScheme
            val foreground = scheme.onSurface.luminance()
            for (background in listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)) {
                val luminance = background.luminance()
                val contrast = (maxOf(foreground, luminance) + .05f) / (minOf(foreground, luminance) + .05f)
                assertTrue("${theme.displayName}: tonal card contrast $contrast must be at least 4.5:1", contrast >= 4.5f)
            }
        }
    }
}
