package com.github.nacabaro.vbhelper.screens.adventureScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import com.github.nacabaro.vbhelper.R

@Composable
fun CancelAdventureDialog(
    characterSprite: BitmapData,
    onDismissRequest: () -> Unit,
    onClickConfirm: () -> Unit
) {
    val bitmap = remember (characterSprite) { characterSprite.getBitmap() }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density: Float = LocalContext.current.resources.displayMetrics.density
    val dpSize = (characterSprite.width * 4 / density).dp

    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        CyberPanel(modifier = Modifier.fillMaxWidth()) {
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
                        bitmap = imageBitmap,
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.padding(8.dp).size(dpSize)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.adventure_cancel_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryOnDark
                    )
                    Text(
                        text = stringResource(R.string.adventure_cancel_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryOnDark,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VitalButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.adventure_keep))
                }
                VitalButton(
                    onClick = onClickConfirm,
                    modifier = Modifier.weight(1f),
                    borderColor = StatusRed,
                    contentColor = StatusRed
                ) {
                    Text(text = stringResource(R.string.adventure_cancel_action))
                }
            }
        }
    }
}
