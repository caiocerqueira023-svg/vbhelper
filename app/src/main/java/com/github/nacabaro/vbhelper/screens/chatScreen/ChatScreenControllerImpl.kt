package com.github.nacabaro.vbhelper.screens.chatScreen

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatScreenControllerImpl(
    private val componentActivity: ComponentActivity
) : ChatScreenController {
    private val application = componentActivity.applicationContext as VBHelper
    private val chatRepository = ChatRepository(
        database = application.container.db,
        llmSettingsRepository = application.container.llmSettingsRepository
    )

    override fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        chatRepository.getHistory(characterId)

    override fun sendMessage(characterId: Long, text: String, onResult: (Result<String>) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { chatRepository.sendMessage(characterId, text) }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }
}