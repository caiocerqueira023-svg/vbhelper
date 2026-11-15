package com.tamerslab.transfer.ui.scan.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.DimLogo
import com.tamerslab.components.VitalButton
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.card.Card
import com.tamerslab.species.data.ScanRepository
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getImageBitmap
import com.tamerslab.R
import com.tamerslab.ui.theme.DeepPurpleBgAlt
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/** Second write step: the record was checked and is about to be written to the bracelet. */
@Composable
fun WriteCharacterScreen(
    characterId: Long,
    onClickCancel: () -> Unit,
    onClickConfirm: () -> Unit,
    showHeader: Boolean = true,
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val database = application.container.db
    val scanRepository = remember { ScanRepository(database) }
    val cardDetails by scanRepository.getCardDetails(characterId).collectAsStateWithLifecycle(Card(
        id = 0,
        cardId = 0,
        name = "",
        logo = byteArrayOf(),
        logoHeight = 0,
        logoWidth = 0,
        stageCount = 0,
        isBEm = false
    ))

    ScanStepScaffold(
        title = stringResource(R.string.write_character_title),
        showHeader = showHeader,
        onBack = onClickCancel,
    ) {
        CyberPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
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
                        DimLogo(
                            bitmap = charaImageBitmapData.imageBitmap,
                            contentDescription = stringResource(R.string.write_character_icon_description),
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
                        stringResource(R.string.write_character_success),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryOnDark
                    )
                    Text(
                        stringResource(R.string.write_character_wait_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryOnDark
                    )
                    Text(
                        stringResource(R.string.scan_nfc_link_ready),
                        style = MaterialTheme.typography.labelSmall,
                        color = VitalCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        VitalButton(
            onClick = onClickConfirm,
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
        ) {
            Text(stringResource(R.string.write_character_confirm))
        }
        // Inside the Transfer hub this step has no header of its own, so it must carry its own exit.
        if (!showHeader) {
            TextButton(onClick = onClickCancel) { Text(stringResource(R.string.ui_cancel)) }
        }
    }
}
