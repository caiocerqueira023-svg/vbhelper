package com.github.nacabaro.vbhelper.screens.scanScreen.cardSelect

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.R

@Composable
fun ScanCardEntry(
    name: String,
    logo: BitmapData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember (logo.bitmap) { logo.getBitmap() }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density: Float = LocalContext.current.resources.displayMetrics.density
    val dpSize = (logo.width * 4 / density).dp

    CyberPanel(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = DeepPurpleBgAlt,
                shape = RectangleShape,
                modifier = Modifier.size(76.dp)
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = name,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.padding(8.dp).size(dpSize)
                )
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryOnDark
                )
                Text(
                    text = stringResource(R.string.choose_card_action),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark
                )
            }
        }
    }
}
