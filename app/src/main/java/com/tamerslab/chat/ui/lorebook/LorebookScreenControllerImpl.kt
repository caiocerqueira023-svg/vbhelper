package com.tamerslab.chat.ui.lorebook

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.lorebook.LorebookEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LorebookScreenControllerImpl(
    private val componentActivity: ComponentActivity
) {
    private val application = componentActivity.applicationContext as TamersLabApplication
    private val repository = com.tamerslab.di.AppServices.lorebookRepository

    fun getEntries(): Flow<List<LorebookEntry>> = repository.getCustomEntries()

    fun saveEntry(
        existingId: Long?,
        title: String,
        keys: List<String>,
        content: String,
        priority: Int,
        onSaved: () -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val existing = existingId?.let { id -> repository.getCustomEntries().first().firstOrNull { it.id == id } }
            val effectivePriority = if (com.tamerslab.di.AppServices.debugSettingsRepository.enabled) priority else existing?.priority ?: 0
            if (existingId == null) {
                repository.addCustomEntry(title, keys, content, effectivePriority)
            } else {
                if (existing == null) return@launch
                repository.updateEntry(existing.copy(
                        title = title,
                        triggerKeys = keys,
                        content = content,
                        priority = effectivePriority
                    )
                )
            }
            componentActivity.runOnUiThread(onSaved)
        }
    }

    fun setEnabled(entry: LorebookEntry, enabled: Boolean) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            repository.updateEntry(entry.copy(enabled = enabled))
        }
    }

    fun deleteEntry(entry: LorebookEntry) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            repository.deleteEntry(entry)
        }
    }
}
