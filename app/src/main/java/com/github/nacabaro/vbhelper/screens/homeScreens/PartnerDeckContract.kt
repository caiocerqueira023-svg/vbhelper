package com.github.nacabaro.vbhelper.screens.homeScreens

import androidx.compose.ui.unit.dp

/**
 * Contract between the farm-home shell (implemented in `screens/farmHome/`) and the partner deck
 * (slab + three-detent overview sheet, implemented in `PartnerDeck.kt`).
 *
 * The detent is hoisted by the shell so it can hide the place dock when the sheet is expanded and so
 * the back handler owns the collapse ladder (Expanded -> Half -> Peek -> system back).
 */
enum class PartnerDetent { Peek, Half, Expanded }

/** Height of the always-visible partner slab, including its drag handle. The dock sits above it. */
val PartnerPeekHeight = 96.dp

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
 * )
 */
