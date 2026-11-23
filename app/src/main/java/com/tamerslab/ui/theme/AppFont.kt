package com.tamerslab.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tamerslab.R

enum class AppFont(val preferenceValue: String, val displayName: String) {
    DEFAULT("default", "Default"),
    MICHROMA("michroma", "Michroma"),
    OXANIUM("oxanium", "Oxanium"),
    EUROSTILE_EXTENDED("eurostile_extended", "Eurostile Extended"),
    SQUARE_721_EXTENDED("square_721_extended", "Square 721 Extended"),
    VB_LAB("vb_lab", "VB Lab (Kafu Techno)");

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
    // FOT-KafuTechnoStd-E, extracted from the shipped VB Lab 1.4.6 APK.
    // Proprietary Fontworks face: personal use only; do not redistribute the APK publicly
    // without checking the font licence.
    AppFont.VB_LAB -> FontFamily(Font(R.font.vb_lab_kafu_techno))
}
