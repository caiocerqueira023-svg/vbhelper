package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

/** Shared rhythm; artwork grids and the functional combat deck retain their own density. */
object InstrumentSpacing {
    val Screen = 16.dp
    val Panel = 12.dp
    val Detail = 16.dp
    val ControlGap = 8.dp
    val SectionGap = 20.dp
    val ActionHeight = 52.dp
}

/** Native navigation tabs use the same full-width signal line in every feature. */
@Composable
fun VitalTabs(selectedIndex: Int, labels: List<String>, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val signal = VitalCyan
    PrimaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background, indicator = {}, divider = {}) {
        labels.forEachIndexed { index, label ->
            Tab(selected = selectedIndex == index, onClick = { onSelected(index) }, enabled = enabled,
                selectedContentColor = signal, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 48.dp).drawBehind {
                    if (selectedIndex == index) drawLine(signal, Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                        Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()), 3.dp.toPx())
                }, text = { Text(label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) })
        }
    }
}

/** Collection search follows Storage's cut-corner field, without duplicating its filtering logic. */
@Composable
fun VitalSearchField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, hint: String? = null) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = hint?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (value.isEmpty()) null else { {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Default.Clear, stringResource(R.string.storage_clear_search))
            }
        } }, shape = CutCornerShape(8.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
}

/** The divided species-profile reading vocabulary, reused for dense operational details. */
@Composable
fun VitalSectionBar(title: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().cyberFrame(), color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = androidx.compose.ui.graphics.RectangleShape) {
        Text(title, Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = VitalCyan,
            textAlign = TextAlign.Center)
    }
}
