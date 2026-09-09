package com.github.nacabaro.vbhelper.screens.lorebookScreen

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class LorebookScreenControllerImpl(
    private val componentActivity: ComponentActivity
) {
    private val application = componentActivity.applicationContext as VBHelper
    private val repository = application.container.lorebookRepository

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
            if (existingId == null) {
                repository.addCustomEntry(title, keys, content, priority)
            } else {
                repository.updateEntry(
                    LorebookEntry(
                        id = existingId,
                        title = title,
                        triggerKeys = keys,
                        content = content,
                        priority = priority
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
