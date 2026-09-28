package com.github.nacabaro.vbhelper.screens.itemsScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
fun ItemElement(
    item: ItemDtos.ItemsWithQuantities,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit) = {  }
) {
    CyberPanel(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(0.9f),
        contentPadding = PaddingValues(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Icon(
                painter = painterResource(id = getIconResource(item.itemIcon)),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center)
                    .padding(10.dp),
                tint = VitalCyan
            )
            Icon(
                painter = painterResource(id = getLengthResource(item.itemLength)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceTint,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
        Text(
            text = item.name,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimaryOnDark,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.price} CR",
                style = MaterialTheme.typography.labelSmall,
                color = VitalCyan
            )
            if (item.quantity > 0) {
                Text(
                    text = "×${item.quantity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark
                )
            }
        }
    }
}
