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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.RectangleShape
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
            .fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RectangleShape)
        ) {
            Icon(
                painter = painterResource(id = getIconResource(item.itemIcon)),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .padding(10.dp),
                tint = VitalCyan
            )
            Icon(
                painter = painterResource(id = getLengthResource(item.itemLength)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceTint,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
        Text(
            text = item.name,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimaryOnDark,
            fontWeight = FontWeight.SemiBold,
            minLines = 3,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.github.nacabaro.vbhelper.R.string.items_store_credits, item.price),
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
