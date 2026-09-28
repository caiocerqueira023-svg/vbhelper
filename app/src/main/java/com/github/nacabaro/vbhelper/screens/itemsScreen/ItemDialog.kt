package com.github.nacabaro.vbhelper.screens.itemsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.items.ItemType
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan



@Composable
fun ItemDialog(
    item: ItemDtos.ItemsWithQuantities,
    onClickCancel: () -> Unit,
    onClickUse: (() -> Unit)? = null,
    onClickPurchase: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onClickCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        CyberPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.size(96.dp)) {
                    Icon(
                        painter = painterResource(id = getIconResource(item.itemIcon)),
                        contentDescription = null,
                        tint = VitalCyan,
                        modifier = Modifier.size(88.dp).align(Alignment.Center)
                    )
                    Icon(
                        painter = painterResource(id = getLengthResource(item.itemLength)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(44.dp).align(Alignment.BottomEnd)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimaryOnDark,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.item_dialog_you_have_quantity, item.quantity),
                        style = MaterialTheme.typography.labelMedium,
                        color = VitalCyan,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            if (onClickPurchase != null) {
                Text(
                    text = stringResource(R.string.item_dialog_costs_credits, item.price),
                    style = MaterialTheme.typography.labelLarge,
                    color = VitalCyan,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                if (onClickUse != null) {
                    VitalButton(
                        onClick = onClickUse,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.item_dialog_use))
                    }
                }

                if (onClickPurchase != null) {
                    VitalButton(
                        onClick = onClickPurchase,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.item_dialog_purchase))
                    }
                }

                VitalButton(
                    onClick = onClickCancel,
                    modifier = Modifier.weight(1f)
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
    VBHelperTheme {
        ItemDialog(
            item = ItemDtos.ItemsWithQuantities(
                name = "AP Training x3 (60 min)",
                description = "Boosts AP during training (for 60 minutes)",
                itemIcon = R.drawable.baseline_attack_24,
                itemLength = R.drawable.baseline_60_min_timer,
                quantity = 19,
                id = 1,
                price = 500,
                itemType = ItemType.BEITEM
            ),
            onClickUse = {  },
            onClickCancel = {  }
        )
    }
}
