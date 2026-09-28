package com.github.nacabaro.vbhelper.screens.scanScreen.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.source.ScanRepository
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getImageBitmap
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
fun WriteCardScreen(
    characterId: Long,
    onClickCancel: () -> Unit,
    onClickConfirm: () -> Unit
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val database = application.container.db
    val scanRepository = remember { ScanRepository(database) }
    val cardDetails by scanRepository.getCardDetails(characterId).collectAsState(Card(
        id = 0,
        cardId = 0,
        name = "",
        logo = byteArrayOf(),
        logoHeight = 0,
        logoWidth = 0,
        stageCount = 0,
        isBEm = false
    ))

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.write_card_title),
                onBackClick = onClickCancel
            )
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            CyberPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                active = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (cardDetails.logoHeight > 0 && cardDetails.logoWidth > 0) {
                        val charaBitmapData = BitmapData(
                            bitmap = cardDetails.logo,
                            width = cardDetails.logoWidth,
                            height = cardDetails.logoHeight
                        )
                        val charaImageBitmapData = charaBitmapData.getImageBitmap(
                            context = LocalContext.current,
                            multiplier = 4,
                            obscure = false
                        )

                        Surface(
                            color = DeepPurpleBgAlt,
                            shape = RectangleShape,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Image(
                                bitmap = charaImageBitmapData.imageBitmap,
                                contentDescription =  stringResource(
                                    R.string.write_card_icon_description
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                filterQuality = FilterQuality.None
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            stringResource(R.string.write_card_device_ready),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryOnDark
                        )
                        Text(
                            stringResource(
                                R.string.write_card_required_card,
                                cardDetails.name
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryOnDark
                        )
                        Text(
                            stringResource(R.string.scan_card_link_ready),
                            style = MaterialTheme.typography.labelSmall,
                            color = VitalCyan
                        )
                    }
                }
            }

            VitalButton(
                onClick = onClickConfirm,
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.write_card_confirm))
            }
        }
    }
}
