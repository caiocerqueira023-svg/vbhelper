package com.github.nacabaro.vbhelper.domain.lorebook

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LorebookEntrySource {
    CUSTOM
}

@Entity
data class LorebookEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerKeys: List<String>,
    val content: String,
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
    val priority: Int = 0,
    val source: LorebookEntrySource = LorebookEntrySource.CUSTOM
)
