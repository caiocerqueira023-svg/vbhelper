package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NicknameDisplay(nickname: String?) {
    if (!nickname.isNullOrBlank()) {
        Text(
            text = nickname,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
            fontSize = 22.sp
        )
    }
}
