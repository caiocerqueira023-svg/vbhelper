package com.github.nacabaro.vbhelper.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark

internal object BattleAuthenticationPromptTags {
    const val NacaBattleButton = "nacabattle-auth-button"
}

@Composable
internal fun BattleAuthenticationPrompt(
    isCheckingAuth: Boolean,
    onOpenNacaBattle: () -> Unit,
    modifier: Modifier = Modifier
) {
    CyberPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (isCheckingAuth) R.string.ui_battle_auth_checking
                    else R.string.ui_battle_auth_required_title
                ),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark,
                textAlign = TextAlign.Center
            )

            if (!isCheckingAuth) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ui_battle_auth_required_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                VitalButton(
                    onClick = onOpenNacaBattle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(BattleAuthenticationPromptTags.NacaBattleButton)
                ) {
                    Text(stringResource(R.string.ui_open_nacabattle))
                }
            }
        }
    }
}
