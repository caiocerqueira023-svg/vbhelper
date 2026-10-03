package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.dtos.CardSpecificJogressDetails
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.utils.BitmapData

@Composable
fun DexCharaFusionsDialog(currentChara: CharacterDtos.CardCharaProgress,
                         currentCharaPossibleFusions: List<CharacterDtos.FusionsWithSpritesAndObtained>,
                         obscure: Boolean, onClickDismiss: () -> Unit,
                         specificJogress: List<CardSpecificJogressDetails> = emptyList()) {
    val maximumHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .88f }
    Dialog(onDismissRequest = onClickDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(.92f).heightIn(max = maximumHeight),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.dex_chara_fusions_button), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DexSpritePortrait(BitmapData(currentChara.spriteIdle, currentChara.spriteWidth, currentChara.spriteHeight),
                            Modifier.size(72.dp), grayscale = currentChara.discoveredOn == null, obscure = obscure)
                        Column(Modifier.weight(1f)) { DexNameImage(currentChara, obscure) }
                    }
                    currentCharaPossibleFusions.forEach { route ->
                        DexRouteCard(BitmapData(route.spriteIdle, route.spriteWidth, route.spriteHeight), route.discoveredOn) {
                            Text(stringResource(R.string.ui_combine_with, route.fusionAttribute.toString()),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    specificJogress.forEach { route ->
                        DexRouteCard(BitmapData(route.spriteIdle, route.spriteWidth, route.spriteHeight), route.discoveredOn) {
                            Text(stringResource(R.string.dex_detail_jogress_partner, route.partnerCardNumber, route.partnerCharaIndex + 1),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                VitalButton(onClick = onClickDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ui_close))
                }
            }
        }
    }
}
