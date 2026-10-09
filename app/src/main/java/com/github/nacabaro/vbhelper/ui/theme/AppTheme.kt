package com.github.nacabaro.vbhelper.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.github.nacabaro.vbhelper.R

enum class AppTheme(val preferenceValue: String, val displayName: String, val nativeThemeResource: Int) {
    VB_HELPER("vb_helper", "VB Helper", R.style.Theme_VBHelper),
    VB_LAB("vb_lab", "VB Lab", R.style.Theme_VBHelper_Lab),
    VB_ARENA("vb_arena", "VB Arena", R.style.Theme_VBHelper_Arena);

    val palette: AppPalette
        get() = when (this) {
            VB_HELPER -> HelperPalette
            VB_LAB -> LabPalette
            VB_ARENA -> ArenaPalette
        }

    companion object {
        fun fromPreference(value: String?): AppTheme =
            entries.firstOrNull { it.preferenceValue == value } ?: VB_HELPER
    }
}

/** Color-only variants: geometry, typography and motion are shared by every theme. */
@Immutable
data class AppPalette(
    val isDark: Boolean = true,
    val base: Color = Color(0xFF0A0812),
    val background: Color = Color(0xFF120F1F),
    val backgroundAlt: Color = Color(0xFF171327),
    val surface: Color = Color(0xFF1C1730),
    val surfaceElevated: Color = Color(0xFF251E3D),
    val surfaceHighlight: Color = Color(0xFF2F2650),
    val stroke: Color = Color(0xFF3C3260),
    val primary: Color = Color(0xFF8B5CF6),
    val primaryBright: Color = Color(0xFFA78BFA),
    val primaryDim: Color = Color(0xFF4C3A82),
    val onPrimary: Color = Color(0xFF0A0812),
    val accent: Color = Color(0xFF2DE1FC),
    val accentDim: Color = Color(0xFF1A8FA6),
    val onAccent: Color = Color(0xFF0A0812),
    val tertiary: Color = Color.White,
    val yellow: Color = Color(0xFFFFD447),
    val green: Color = Color(0xFF35D48B),
    val greenDim: Color = Color(0xFF1E6E4C),
    val blue: Color = Color(0xFF4C8DFF),
    val blueDim: Color = Color(0xFF25417D),
    val red: Color = Color(0xFFFF4D6A),
    val redDim: Color = Color(0xFF7A2436),
    val statusYellow: Color = Color(0xFFFFC93C),
    val yellowDim: Color = Color(0xFF7A631B),
    val textPrimary: Color = Color(0xFFF4F1FF),
    val textSecondary: Color = Color(0xFFB7AFD6),
    val textMuted: Color = Color(0xFF8A82AC),
    val compass: Color = Color(0xFFB0B0B0),
    val follower: Color = Color(0xFF4FC3F7),
    val onStatus: Color = Color(0xFF0A0812),
    val signalHint: Color = Color(0xFF9FE7EE),
    val battlePanel: Color = Color(0xFF161024),
    val battleBackdrop: Color = Color(0xFF100D1A),
    val battleErrorSurface: Color = Color(0xFF2A1426),
    val battleErrorOutline: Color = Color(0xFFFF7C96),
    val battleErrorHint: Color = Color(0xFFFF98AB),
    val battleDestructive: Color = Color(0xFFB34363),
    val battleEnemyHealth: Color = Color(0xFFFF7899),
    val battleTrack: Color = Color(0xFF45394C),
) {
    val colorScheme: ColorScheme
        get() {
            val scheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
                primary = primaryBright, onPrimary = onPrimary,
                primaryContainer = primaryDim, onPrimaryContainer = textPrimary,
                secondary = accent, onSecondary = onAccent,
                secondaryContainer = accentDim, onSecondaryContainer = textPrimary,
                tertiary = tertiary, onTertiary = onPrimary,
                tertiaryContainer = surfaceHighlight, onTertiaryContainer = tertiary,
                // Preserve the global device pattern behind transparent scaffolds.
                background = Color.Transparent, onBackground = textPrimary,
                surface = surface, onSurface = textPrimary,
                surfaceVariant = surfaceElevated, onSurfaceVariant = textSecondary,
                surfaceContainerLowest = base, surfaceContainerLow = backgroundAlt,
                surfaceContainer = surface, surfaceContainerHigh = surfaceElevated,
                surfaceContainerHighest = surfaceHighlight,
                outline = stroke, outlineVariant = primaryDim,
                error = red, onError = if (isDark) textPrimary else Color.White,
                errorContainer = redDim, onErrorContainer = textPrimary,
            )
            // Helper retains every inherited Material default from its existing scheme.
            // Other variants must also recolor tonal elevation and inverse surfaces.
            return if (this == HelperPalette) scheme else scheme.copy(
                surfaceTint = primaryBright,
                surfaceDim = backgroundAlt,
                surfaceBright = surfaceHighlight,
                inverseSurface = textPrimary,
                inverseOnSurface = background,
                inversePrimary = if (isDark) primaryDim else primary,
            )
        }
}

private val HelperPalette = AppPalette()

// VB Lab references: neutral charcoal panels, white text and electric cyan signals.
private val LabPalette = HelperPalette.copy(
    base = Color(0xFF111111), background = Color(0xFF272727),
    backgroundAlt = Color(0xFF202020), surface = Color(0xFF303030),
    surfaceElevated = Color(0xFF383838), surfaceHighlight = Color(0xFF424242),
    stroke = Color(0xFF707070), primary = Color(0xFF00B9DF),
    primaryBright = Color(0xFF43D6F3), primaryDim = Color(0xFF19556A),
    onPrimary = Color(0xFF111111), accent = Color(0xFF00BDE3),
    accentDim = Color(0xFF23627A), onAccent = Color(0xFF111111),
    textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFD0D0D0),
    textMuted = Color(0xFFAAAAAA), follower = Color(0xFF43D6F3),
    signalHint = Color(0xFFA6ECF5), battlePanel = Color(0xFF303030),
    battleBackdrop = Color(0xFF202020), battleErrorSurface = Color(0xFF40272D),
    battleTrack = Color(0xFF555555),
)

// VB Arena references: white/silver surfaces, vivid mint green and charcoal lettering.
// Signals used as small text have a deeper green for legibility on the light surfaces.
private val ArenaPalette = HelperPalette.copy(
    isDark = false, base = Color(0xFFFFFFFF), background = Color(0xFFFAFCFA),
    backgroundAlt = Color(0xFFF1F4F2), surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE9EEEB), surfaceHighlight = Color(0xFFDDE5DF),
    stroke = Color(0xFF78847D), primary = Color(0xFF24E88A),
    primaryBright = Color(0xFF006D39), primaryDim = Color(0xFFA7F3C8),
    onPrimary = Color.White, accent = Color(0xFF006D39),
    accentDim = Color(0xFFB9F4D2), onAccent = Color.White,
    tertiary = Color(0xFF3152CC), yellow = Color(0xFF806000),
    green = Color(0xFF006D39), greenDim = Color(0xFFCBF4DC),
    blue = Color(0xFF3152CC), blueDim = Color(0xFFDDE4FF),
    red = Color(0xFFBA2144), redDim = Color(0xFFFFDFE5),
    statusYellow = Color(0xFF806000), yellowDim = Color(0xFFFFF0AA),
    textPrimary = Color(0xFF151A17), textSecondary = Color(0xFF46554C),
    textMuted = Color(0xFF59675E), compass = Color(0xFF59675E),
    follower = Color(0xFF007648),
    onStatus = Color.White, signalHint = Color(0xFF006D39),
    battlePanel = Color(0xFFFFFFFF), battleBackdrop = Color(0xFFF1F4F2),
    battleErrorSurface = Color(0xFFFFDFE5), battleErrorOutline = Color(0xFFBA2144),
    battleErrorHint = Color(0xFFBA2144), battleDestructive = Color(0xFFBA2144),
    battleEnemyHealth = Color(0xFFBA2144), battleTrack = Color(0xFFDDE5DF),
)

internal val LocalAppPalette = staticCompositionLocalOf { AppTheme.VB_HELPER.palette }
