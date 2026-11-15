package com.tamerslab.items.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.cyberFrame
import com.tamerslab.components.editorDialogBounds
import com.tamerslab.domain.items.ItemType
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.TamersLabTheme
import com.tamerslab.ui.theme.VitalCyan

/**
 * One item's details. It carries the same grammar as the catalogue card - framed glyph, name, family
 * and owned count - so opening an entry reads as that card expanding, not as a different screen.
 */
@Composable
fun ItemDialog(
    item: ItemDtos.ItemsWithQuantities,
    onClickCancel: () -> Unit,
    onClickUse: (() -> Unit)? = null,
    onClickPurchase: (() -> Unit)? = null,
    balance: Int? = null,
) {
    val affordable = onClickPurchase == null || balance == null || item.price <= balance
    Dialog(
        onDismissRequest = onClickCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        CyberPanel(
            modifier = Modifier
                .editorDialogBounds()
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RectangleShape)
                            .cyberFrame(),
                        contentAlignment = Alignment.Center,
                    ) {
                            Icon(
                                painter = painterResource(getIconResource(item.itemIcon)),
                                contentDescription = null,
                                tint = VitalCyan,
                                modifier = Modifier.size(44.dp),
                            )
                            getLengthIcon(item.itemLength)?.let { length ->
                                Icon(
                                    painter = painterResource(length),
                                    contentDescription = null,
                                    tint = TextSecondaryOnDark,
                                    modifier = Modifier.size(18.dp).align(Alignment.BottomEnd).padding(4.dp),
                                )
                            }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimaryOnDark,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(itemFamily(item.itemIcon).label),
                            style = MaterialTheme.typography.labelMedium,
                            color = VitalCyan,
                        )
                        Text(
                            text = stringResource(R.string.item_dialog_you_have_quantity, item.quantity),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondaryOnDark,
                        )
                    }
                }
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                if (onClickPurchase != null) {
                    Text(
                        text = stringResource(R.string.item_dialog_costs_credits, item.price),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (affordable) VitalCyan else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!affordable) {
                        Text(
                            text = stringResource(R.string.items_not_affordable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (onClickUse != null) {
                    VitalButton(
                        onClick = onClickUse,
                        style = VitalButtonStyle.PRIMARY,
                    ) {
                        Text(stringResource(R.string.item_dialog_use))
                    }
                }
                if (onClickPurchase != null) {
                    VitalButton(
                        onClick = onClickPurchase,
                        style = VitalButtonStyle.PRIMARY,
                        enabled = affordable,
                    ) {
                        Text(stringResource(R.string.item_dialog_purchase))
                    }
                }
                VitalButton(
                    onClick = onClickCancel,
                    style = VitalButtonStyle.SECONDARY,
                ) {
                    Text(stringResource(R.string.item_dialog_cancel))
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun PreviewItemDialog() {
    TamersLabTheme {
        ItemDialog(
            item = ItemDtos.ItemsWithQuantities(
                id = 8,
                name = "AP Training x3 (60 min)",
                description = "Boosts AP during training (for 60 minutes)",
                itemIcon = 3,
                itemLength = 60,
                price = 400,
                quantity = 19,
                itemType = ItemType.BEITEM,
            ),
            onClickCancel = {},
            onClickUse = {},
        )
    }
}
