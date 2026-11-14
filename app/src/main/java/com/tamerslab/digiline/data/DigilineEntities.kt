package com.tamerslab.digiline.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DigiLine v2 persistent state (database schema 56).
 *
 * Table and column names are frozen by `DIGILINE_IMPLEMENTATION_CONTRACT.md` §4:
 * [com.tamerslab.data.database.DigilineSchema] creates the same tables with raw SQL for the
 * 55 -> 56 migration, so every change here must be mirrored there (Room validates the
 * migrated schema on open).
 */

/** Long-term owned relationship metadata + question/quiz/shift cadence per individual. */
@Entity(tableName = "DigilineProfile")
data class DigilineProfileEntity(
    @PrimaryKey val individualId: String,
    val speciesVariant: Int = 0,
    val lastAskedAt: Long = 0,
    val lastQuizAt: Long = 0,
    val lastShiftAt: Long = 0,
    val sessionCounter: Long = 0,
    val contentVersion: Int = 0,
    val nativeNoticeShown: Boolean = false,
)

/** Every question shown or answered, used for anti-repeat windows and the pending-card state. */
@Entity(tableName = "DigilineQuestionRecord", indices = [Index("individualId")])
data class DigilineQuestionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String,
    val questionKey: String,
    val kind: String,
    val askedAt: Long,
    val answeredAt: Long? = null,
    val optionId: String? = null,
    val shiftJson: String? = null,
    val moodDelta: Int = 0,
    val status: String = "ASKED",
)

/** Quiz attempts (one per individual per day in normal play; used for the 30-day no-repeat window). */
@Entity(tableName = "DigilineQuizRecord", indices = [Index("individualId")])
data class DigilineQuizRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String,
    val quizKey: String,
    val chosenIndex: Int,
    val correct: Boolean,
    val moodDelta: Int,
    val answeredAt: Long,
)

/** Shift accumulator per personality type (1..16); the active type is [com.tamerslab.domain.personality.DigimonPersonalityTraits]. */
@Entity(tableName = "PersonalityTendency", primaryKeys = ["individualId", "typeNumber"])
data class PersonalityTendencyEntity(
    val individualId: String,
    val typeNumber: Int,
    val points: Int,
    val updatedAt: Long,
)

/** App -> Time Stranger personality propagation queue (D4/R3). State is owned by the sync coordinator. */
@Entity(tableName = "NativePersonalitySync")
data class NativePersonalitySyncEntity(
    @PrimaryKey val individualId: String,
    val targetType: Int,
    val targetNativeId: Int?,
    val state: String,
    val updatedAt: Long,
)

/** Active/resumable wild negotiation session (persisted before the first line is shown). */
@Entity(tableName = "DigilineNegotiation")
data class DigilineNegotiationEntity(
    @PrimaryKey val wildIndividualId: String,
    val tier: Int,
    val nodeId: String,
    val phase: String,
    val seed: Long,
    val demandsJson: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val state: String,
    val departUsed: Boolean = false,
)

/** Negotiation audit trail / anti-scum log; payments and outcomes are appended here. */
@Entity(tableName = "DigilineNegotiationLog", indices = [Index("wildIndividualId")])
data class DigilineNegotiationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wildIndividualId: String,
    val nodeId: String,
    val optionId: String?,
    val effectsJson: String?,
    val createdAt: Long,
)

/** Trust replacement for joined contacts (D6/R7): authored persuasion stage + recruitment-quest offer state. */
@Entity(tableName = "WildPersuasion")
data class WildPersuasionEntity(
    @PrimaryKey val wildIndividualId: String,
    val stageIndex: Int = 0,
    val topicsDoneJson: String = "[]",
    val questOfferState: String = "NONE",
    val joinedAt: Long,
    val lastTalkAt: Long = 0,
    val departureState: String = "NONE",
)

/** Reaction/diary delivery dedupe record. */
@Entity(tableName = "DigilineEventRecord", indices = [Index("individualId")])
data class DigilineEventRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String,
    val eventKey: String,
    val deliveredAt: Long,
)
