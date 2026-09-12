package com.github.nacabaro.vbhelper.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.github.nacabaro.vbhelper.R

enum class AppFont(val preferenceValue: String, val displayName: String) {
    DEFAULT("default", "Default"),
    MICHROMA("michroma", "Michroma"),
    OXANIUM("oxanium", "Oxanium"),
    EUROSTILE_EXTENDED("eurostile_extended", "Eurostile Extended"),
    SQUARE_721_EXTENDED("square_721_extended", "Square 721 Extended");

    companion object {
        fun fromPreference(value: String?): AppFont =
            entries.firstOrNull { it.preferenceValue == value } ?: OXANIUM
    }
}

fun appFontFamily(appFont: AppFont): FontFamily = when (appFont) {
    AppFont.DEFAULT -> FontFamily.Default
    AppFont.MICHROMA -> FontFamily(Font(R.font.michroma_regular))
    AppFont.OXANIUM -> FontFamily(
        Font(R.font.oxanium_regular, FontWeight.Normal),
        Font(R.font.oxanium_semibold, FontWeight.SemiBold),
        Font(R.font.oxanium_bold, FontWeight.Bold)
    )
    AppFont.EUROSTILE_EXTENDED -> FontFamily(Font(R.font.eurostile_extended_black))
    AppFont.SQUARE_721_EXTENDED -> FontFamily(Font(R.font.square_721_extended))
}
