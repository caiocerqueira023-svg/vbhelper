package com.github.nacabaro.vbhelper.domain.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = UserCharacter::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["characterId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    val role: String, // "user" | "assistant"
    val content: String,
    val timestamp: Long
)