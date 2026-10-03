package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan


@Composable
fun CardEntry(
    name: String,
    logo: BitmapData,
    obtainedCharacters: Int,
    totalCharacters: Int,
    officialStatus: OfficialStatus,
    onClick: () -> Unit,
    displayModify: Boolean,
    onClickModify: () -> Unit,
    onClickDelete: () -> Unit,
    onClickSetOrigin: () -> Unit,
    onClickRetrySpeciesMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember (logo.bitmap) { logo.getBitmap() }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    CyberPanel(
        modifier = modifier,
        active = officialStatus == OfficialStatus.OFFICIAL || officialStatus == OfficialStatus.CUSTOM,
        onClick = onClick.takeUnless { displayModify }
    ) {
        Row (
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.Surface(
                color = DeepPurpleBgAlt,
                shape = RectangleShape,
                modifier = Modifier.size(76.dp)
            ) {
                Image (
                    bitmap = imageBitmap,
                    contentDescription = name,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.padding(8.dp)
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
                    color = TextPrimaryOnDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (officialStatus) {
                        OfficialStatus.OFFICIAL -> stringResource(R.string.card_status_official)
                        OfficialStatus.CUSTOM -> stringResource(R.string.card_status_custom)
                        OfficialStatus.UNKNOWN -> stringResource(R.string.card_status_unknown)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (officialStatus == OfficialStatus.OFFICIAL) VitalCyan else TextSecondaryOnDark
                )
                LinearProgressIndicator(
                    progress = {
                        if (totalCharacters <= 0) 0f
                        else obtainedCharacters.toFloat() / totalCharacters.toFloat()
                    },
                    color = VitalCyan,
                    trackColor = DeepPurpleBgAlt,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    text = stringResource(
                        R.string.card_entry_characters_obtained,
                        obtainedCharacters,
                        totalCharacters
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (officialStatus == OfficialStatus.UNKNOWN) {
                    TextButton(onClick = onClickSetOrigin) {
                        Text(
                            text = stringResource(R.string.card_entry_set_origin),
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }
                if (displayModify && officialStatus != OfficialStatus.CUSTOM) {
                    TextButton(onClick = onClickRetrySpeciesMatch) {
                        Text(
                            text = stringResource(R.string.card_entry_retry_species_match),
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }
            }
            if (displayModify) {
                Row (
                    modifier = Modifier,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onClickSetOrigin) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.card_entry_set_origin)
                        )
                    }
                    IconButton(
                        onClick = onClickModify
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.card_entry_edit)
                        )
                    }
                    IconButton(
                        onClick = onClickDelete
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.card_entry_delete)
                        )
                    }
                }
            }
        }
    }
}
