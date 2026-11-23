package com.tamerslab.home.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Contract between the farm-home shell (implemented in `screens/farmHome/`) and the partner deck
 * (slab + three-detent overview sheet, implemented in `PartnerDeck.kt`).
 *
 * The detent is hoisted by the shell so the back handler owns the collapse ladder
 * (Expanded -> Half -> Peek -> system back). The place row lives under the sheet's peek slab rather
 * than under the sheet itself: the sheet reports how far it is pulled ([onSheetProgress]) and yields
 * exactly [placeRowBand] of bottom space as it opens, so the row fades out while the sheet claims it
 * instead of the row popping on a detent edge.
 */
enum class PartnerDetent { Peek, Half, Expanded }

/** Height of the always-visible partner slab, including its drag handle. The dock sits above it. */
val PartnerPeekHeight = 96.dp

/**
 * Fill opacity of the partner slab. The top and bottom HUD grounds match it so the three zones read
 * as one surface tone instead of three different greys.
 */
val PartnerSlabAlpha = 0.82f

/**
 * The cut-corner shape shared by Home's modal sheets. The surface brief refuses the Material default
 * 28dp rounded sheet next to the cut-corner instrument vocabulary, so roster, menu and resident
 * sheets all pass this.
 */
val HomeSheetShape = CutCornerShape(topStart = 18.dp, topEnd = 18.dp)

/**
 * Extra factor the farm's fixed bottom chrome (peek slab, place band) may grow with the system font.
 *
 * The slab and the place band are authored at fixed dp, but their contents are sp and scale with the
 * system font setting. Capping the growth at 1.5 lets the vitals and the place captions stay inside
 * the chrome at 2.0x text scale (the sheet layout itself still measures the live detent height).
 */
@Composable
fun farmChromeScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)

/*
 * The composable implemented in `PartnerDeck.kt` and called by the shell:
 *
 * @Composable
 * fun PartnerDeck(
 *     navController: NavController,
 *     homeScreenController: HomeScreenController,
 *     storageScreenController: StorageScreenController,
 *     importingConnection: Boolean,
 *     onImportCards: () -> Unit,
 *     onImportConnection: () -> Unit,
 *     detent: PartnerDetent,
 *     onDetentChange: (PartnerDetent) -> Unit,
 *     modifier: Modifier = Modifier,
 *     placeRowBand: Dp = 0.dp,              // height of the place row under the sheet
 *     onSheetProgress: (Float) -> Unit = {}, // 0 at Peek, 1 at Half: drives the row's fade
 * )
 */
