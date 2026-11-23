package com.tamerslab.ui.theme

import android.app.Activity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.tamerslab.components.LocalMotionEnabled
import com.tamerslab.components.rememberMotionPreference

@Composable
@Suppress("DEPRECATION")
fun TamersLabTheme(
    appFont: AppFont = AppFont.OXANIUM,
    appTheme: AppTheme = AppTheme.VB_HELPER,
    content: @Composable () -> Unit
) {
    // Appearance is chosen by the user, never by the phone mode or wallpaper.
    val palette = appTheme.palette
    val colorScheme = remember(appTheme) { palette.colorScheme }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            // Status bar stays transparent edge-to-edge; the system action (navigation) bar keeps the
            // palette's own solid ground so its system buttons never float over app content. The
            // light/dark icon appearance below carries the contrast in both cases.
            val transparent = Color.Transparent.toArgb()
            val barColor = palette.background.toArgb()
            val statusBarStyle = if (palette.isDark) SystemBarStyle.dark(transparent)
                else SystemBarStyle.light(transparent, transparent)
            val navigationBarStyle = if (palette.isDark) SystemBarStyle.dark(barColor)
                else SystemBarStyle.light(barColor, barColor)
            if (activity is ComponentActivity) {
                activity.enableEdgeToEdge(
                    statusBarStyle = statusBarStyle,
                    navigationBarStyle = navigationBarStyle,
                )
            }
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalMotionEnabled provides rememberMotionPreference(),
        LocalAppPalette provides palette,
        LocalAppTheme provides appTheme,
        LocalMinimumInteractiveComponentSize provides 48.dp,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(appFontFamily(appFont)),
            shapes = VitalArenaShapes,
            content = content,
        )
    }
}
