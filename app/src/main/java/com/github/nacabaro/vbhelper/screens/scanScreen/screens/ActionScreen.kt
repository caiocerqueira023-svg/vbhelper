package com.github.nacabaro.vbhelper.screens.scanScreen.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
fun ActionScreen(
    topBannerText: String,
    onClickCancel: () -> Unit,
) {
    Scaffold (
        topBar = {
            TopBanner(
                text = topBannerText,
                onBackClick = onClickCancel
            )
        }
    ) { innerPadding ->
        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            CyberPanel(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                active = true
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    tint = VitalCyan,
                    modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally)
                )
                Text(
                    text = stringResource(R.string.action_place_near_reader),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryOnDark
                )
                Text(
                    text = stringResource(R.string.scan_keep_still),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark
                )
            }
            VitalButton(
                onClick = onClickCancel,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
