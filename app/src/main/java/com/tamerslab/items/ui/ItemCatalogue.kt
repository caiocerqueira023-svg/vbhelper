package com.tamerslab.items.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.VitalSectionBar
import com.tamerslab.components.cyberFrame
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * Functional families the catalogue groups by. The authored `itemIcon` index already carries the
 * family: 1-5 train a stat, 6-7 run an evolution/limit timer, 8 raises vitals, 9-16 are special
 * missions with a step/vital/battle/win goal.
 */
internal enum class ItemFamily(@StringRes val label: Int) {
    TRAINING(R.string.items_category_training),
    EVOLUTION(R.string.items_category_evolution),
    VITALS(R.string.items_category_vitals),
    MISSIONS(R.string.items_category_missions),
}

internal fun itemFamily(itemIcon: Int): ItemFamily = when (itemIcon) {
    in 1..5 -> ItemFamily.TRAINING
    6, 7 -> ItemFamily.EVOLUTION
    8 -> ItemFamily.VITALS
    else -> ItemFamily.MISSIONS
}

/**
 * One catalogue entry. Catalogue rows follow the tamer-rank reading row: a small framed glyph, a
 * single title with the functional meta under it, one value on the trailing edge, and a hairline —
 * no boxed tile around every item.
 */
internal data class CatalogueTile(
    val key: String,
    val title: String,
    @DrawableRes val iconRes: Int,
    @StringRes val lengthRes: Int? = null,
    val trailing: String? = null,
    val trailingIsWarning: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun CatalogueTrailing(tile: CatalogueTile) {
    tile.trailing?.let { trailing ->
        Text(
            text = trailing,
            style = MaterialTheme.typography.titleMedium,
            color = if (tile.trailingIsWarning) MaterialTheme.colorScheme.error else VitalCyan,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CatalogueCard(
    tile: CatalogueTile,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .let { base ->
                    if (tile.onClick != null) base.clickable(onClick = requireNotNull(tile.onClick)) else base
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceElevatedPurple)
                    .cyberFrame(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tile.iconRes),
                    contentDescription = null,
                    tint = VitalCyan,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimaryOnDark,
                    minLines = 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                tile.lengthRes?.let { length ->
                    Icon(
                        painter = painterResource(length),
                        contentDescription = null,
                        tint = TextSecondaryOnDark,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            CatalogueTrailing(tile)
        }
        HorizontalDivider(color = SurfaceStroke.copy(alpha = 0.4f), thickness = 1.dp)
    }
}

/**
 * The name as the catalogue shows it. A mission's authored name carries a bracketed type prefix the
 * section already says, and spells its window out in full; the row abbreviates that window so the
 * two-line name never truncates.
 */
internal fun catalogueTitle(item: com.tamerslab.dtos.ItemDtos.ItemsWithQuantities): String {
    if (itemFamily(item.itemIcon) != ItemFamily.MISSIONS) return item.name
    return item.name.substringAfter("] ", item.name)
        .replace(" hours)", " h)")
        .replace(" hour)", " h)")
        .replace(" minutes)", " min)")
}

/** Section heading shared by every catalogue list: the cyan bar with no count. */
@Composable
internal fun CatalogueSectionHeader(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
) {
    VitalSectionBar(
        title = stringResource(title),
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

/** The store's wallet, as a plain summary row above the catalogue. */
@Composable
internal fun StoreWalletStrip(
    bits: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.items_available_balance),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark,
                )
                Text(
                    text = stringResource(R.string.items_store_credits, bits),
                    style = MaterialTheme.typography.titleLarge,
                    color = VitalCyan,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        HorizontalDivider(color = SurfaceStroke.copy(alpha = 0.4f), thickness = 1.dp)
    }
}
