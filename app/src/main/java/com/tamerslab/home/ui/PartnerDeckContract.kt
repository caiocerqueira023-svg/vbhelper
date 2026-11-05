package com.tamerslab.home.ui

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
