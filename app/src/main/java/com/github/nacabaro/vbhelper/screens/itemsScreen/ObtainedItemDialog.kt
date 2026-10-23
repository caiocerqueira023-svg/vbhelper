package com.github.nacabaro.vbhelper.screens.itemsScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.editorDialogBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ObtainedItemDialog(
    obtainedItem: ItemDtos.PurchasedItem,
    obtainedCurrency: Int,
    onClickDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onClickDismiss
    ) {
        Card(modifier = Modifier.editorDialogBounds()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Column (
                    modifier = Modifier
                        .padding(0.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row {
                        Box(modifier = Modifier) {
                            Icon(
                                painter = painterResource(id = getIconResource(obtainedItem.itemIcon)),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(96.dp)
                                    .align(Alignment.Center)
                            )
                            Icon(
                                painter = painterResource(id = getLengthResource(obtainedItem.itemLength)),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(64.dp)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                        Column (
                            modifier = Modifier
                                .padding(16.dp)
                        ) {
                            Text(
                                style = MaterialTheme.typography.titleMedium,
                                text = obtainedItem.itemName,
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }
                    Text(
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        text = obtainedItem.itemDescription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)

                    )
                    Text(
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        text = stringResource(
                            R.string.obtained_item_you_have,
                            obtainedItem.itemAmount
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                    Text(
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        text = stringResource(
                            R.string.obtained_item_you_also_got_credits,
                            obtainedCurrency
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    VitalButton(
                        onClick = onClickDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.obtained_item_dismiss))
                    }
                }
            }
        }
    }
}
