package com.tamerslab.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import androidx.compose.ui.res.stringResource
import com.tamerslab.R

@Composable
fun CompanionConfirmation(
    prompt: String,
    onResult: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = prompt, modifier = Modifier.padding(bottom = 24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VitalButton(onClick = { onResult(true) }, style = VitalButtonStyle.PRIMARY) {
                Text(stringResource(R.string.ui_ok))
            }
            VitalButton(onClick = { onResult(false) }) {
                Text(stringResource(R.string.ui_cancel))
            }
        }
    }
}
