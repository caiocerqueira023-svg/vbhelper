package com.tamerslab.domain.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tamerslab.domain.device_data.DigimonIndividual

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DigimonIndividual::class,
            parentColumns = ["individualId"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["individualId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val timestamp: Long,
    val isRead: Boolean = false
)
