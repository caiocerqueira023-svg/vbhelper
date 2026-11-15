package com.tamerslab.items.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalSectionHeader
import com.tamerslab.components.cyberFrame
import com.tamerslab.ui.theme.DeepPurpleBgAlt
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
 * One catalogue entry. A single compact row renders owned items, store stock and quest battle
 * stock, so every list keeps the same rhythm: a framed glyph well, a two-line name that reserves both
 * lines, a functional meta line, and the count or price that matters for that list.
 */
internal data class CatalogueTile(
    val key: String,
    val title: String,
    @StringRes val iconRes: Int,
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
            style = MaterialTheme.typography.labelSmall,
            color = if (tile.trailingIsWarning) MaterialTheme.colorScheme.error else VitalCyan,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun CatalogueCard(
    tile: CatalogueTile,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    CyberPanel(
        onClick = tile.onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, RectangleShape)
                    .cyberFrame(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tile.iconRes),
                    contentDescription = null,
                    tint = VitalCyan,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimaryOnDark,
                    fontWeight = FontWeight.SemiBold,
                    minLines = if (wide) 1 else 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tile.lengthRes?.let { length ->
                        Icon(
                            painter = painterResource(length),
                            contentDescription = null,
                            tint = TextSecondaryOnDark,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (!wide) {
                        Spacer(Modifier.weight(1f))
                        CatalogueTrailing(tile)
                    }
                }
            }
            if (wide) {
                Column(
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 6.dp),
                ) {
                    CatalogueTrailing(tile)
                }
            }
        }
    }
}

/**
 * The name as the catalogue shows it. A mission's authored name carries a bracketed type prefix the
 * section already says, and spells its window out in full; the card abbreviates that window so the
 * two-line name never truncates.
 */
internal fun catalogueTitle(item: com.tamerslab.dtos.ItemDtos.ItemsWithQuantities): String {
    if (itemFamily(item.itemIcon) != ItemFamily.MISSIONS) return item.name
    return item.name.substringAfter("] ", item.name)
        .replace(" hours)", " h)")
        .replace(" hour)", " h)")
        .replace(" minutes)", " min)")
}

/** Section heading shared by every catalogue list. */
@Composable
internal fun CatalogueSectionHeader(
    @StringRes title: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    VitalSectionHeader(
        title = stringResource(title),
        detail = pluralStringResource(R.plurals.items_kind_count, count, count),
        modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp),
    )
}

/** The store's wallet and how much of the catalogue it covers, as one instrument strip. */
@Composable
internal fun StoreWalletStrip(
    bits: Int,
    affordable: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    CyberPanel(modifier = modifier.fillMaxWidth(), active = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.items_available_balance).uppercase(),
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
            Text(
                text = stringResource(R.string.items_store_affordable, affordable, total),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryOnDark,
            )
        }
    }
}
