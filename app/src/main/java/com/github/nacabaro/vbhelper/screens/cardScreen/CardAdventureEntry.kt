package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.Image
import com.github.nacabaro.vbhelper.components.DimLogo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getImageBitmap
import com.github.nacabaro.vbhelper.R

@Composable
fun CardAdventureEntry(
    cardAdventureEntry: CardDtos.CardAdventureWithSprites,
    obscure: Boolean
) {
    val charaImageBitmapData = BitmapData(
        bitmap = cardAdventureEntry.characterIdleSprite,
        width = cardAdventureEntry.characterIdleSpriteWidth,
        height = cardAdventureEntry.characterIdleSpriteHeight
    ).getImageBitmap(
        context = LocalContext.current,
        multiplier = 4,
        obscure = obscure
    )

    val nameImageBitmapData = BitmapData(
        bitmap = cardAdventureEntry.characterName,
        width = cardAdventureEntry.characterNameWidth,
        height = cardAdventureEntry.characterNameHeight
    ).getImageBitmap(
        context = LocalContext.current,
        multiplier = 3,
        obscure = obscure
    )

    CyberPanel(
        modifier = Modifier.fillMaxWidth(),
        active = !obscure
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                color = DeepPurpleBgAlt,
                shape = RectangleShape
            ) {
                Image(
                    bitmap = charaImageBitmapData.imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    colorFilter = if (obscure) ColorFilter.tint(TextSecondaryOnDark) else null,
                    filterQuality = FilterQuality.None
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (!obscure) {
                    DimLogo(
                        bitmap = nameImageBitmapData.imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .width(nameImageBitmapData.dpWidth.coerceAtMost(188.dp)),
                        filterQuality = FilterQuality.None
                    )
                    Text(
                        text = "HP ${cardAdventureEntry.characterHp}  ·  AP ${cardAdventureEntry.characterAp}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimaryOnDark,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append("DP ${cardAdventureEntry.characterDp}")
                            cardAdventureEntry.characterBp?.let { append("  ·  BP $it") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = VitalCyan
                    )
                    Text(
                        text = stringResource(R.string.card_adventure_steps, cardAdventureEntry.steps),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryOnDark
                    )
                } else {
                    Text(
                        text = stringResource(R.string.card_adventure_locked_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimaryOnDark,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.card_adventure_locked_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryOnDark
                    )
                }
            }
        }
    }
}
