package com.tamerslab.digiline.freechat.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Free Talk v1 persistent state (database schema 57).
 *
 * Table and column names are frozen by `FREE_CHAT_IMPLEMENTATION_CONTRACT.md` §3:
 * [com.tamerslab.digiline.freechat.data.FreeChatSchema] creates the same tables with raw SQL for
 * the 56 -> 57 migration, so every change here must be mirrored there (Room validates the migrated
 * schema on open).
 *
 * The counters live in their own tables (never in `DigilineProfile`), so authored card flows keep
 * their determinism: free-text turns can never reroll a DigiLine card.
 */

/**
 * Per-individual Free Talk cadence/credit/turn state.
 *
 * - [turnCounter] is the persisted variant seed counter (never derived from message ids).
 * - [lastTalkAt] gates the 12 h greeting window and the session gap.
 * - [lastMoodCreditDay]/[moodCreditToday] enforce the conservative +1 mood session credit.
 * - [sessionCounter] counts messages in the current Free Talk session.
 */
@Entity(tableName = "FreeChatProfile")
data class FreeChatProfileEntity(
    @PrimaryKey val individualId: String,
    val turnCounter: Long = 0,
    val lastTalkAt: Long = 0,
    val lastMoodCreditDay: Long = 0,
    val moodCreditToday: Int = 0,
    val sessionCounter: Long = 0,
    val contentVersion: Int = 0,
)

/**
 * One persisted Free Talk turn decision (anti-reroll + QA audit, contract §3).
 *
 * The row is written in the same Room transaction as the transcript rows, before the scene is
 * returned to the UI, so a process restart replays the same decision instead of rerolling it.
 *
 * Privacy (plan §10.2): for S2/S3/PII turns the player's raw text is not persisted, and
 * [normalizedHash]/[variantSeed] are written as `0` because both are offline-guessable functions of
 * the normalized input (short PII such as a phone number would otherwise be recoverable).
 */
@Entity(tableName = "FreeChatTurn", indices = [Index("individualId")])
data class FreeChatTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String,
    val turnCounter: Long,
    val normalizedHash: Long,
    val intentId: String,
    val replyId: String,
    val variantSeed: Long,
    val scoreQ16: Int,
    val fallbackLevel: Int,
    val safetyClass: String,
    val createdAt: Long,
)
