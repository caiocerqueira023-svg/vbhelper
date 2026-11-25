package com.tamerslab.transfer.ui.scan.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalSectionBar

/**
 * The shared frame of one NFC step (read/write prompt, transfer progress).
 *
 * Standalone the step owns the app's single header (`TopBanner`), which is also what the system back
 * gesture drives. Inside the Transfer hub the hub already owns the one header of the destination, so
 * the step stops rendering its own: the duplication this replaces stacked two banners into the same
 * screen, and the second one's back arrow pointed at the same place as the hub's. In that mode the
 * step title becomes an in-flow section heading and the step writes no window insets of its own.
 */
@Composable
internal fun ScanStepScaffold(
    title: String,
    showHeader: Boolean,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = if (showHeader) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        topBar = { if (showHeader) TopBanner(text = title, onBackClick = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!showHeader) {
                VitalSectionBar(
                    title = title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                    content = content,
                )
            }
        }
    }
}
