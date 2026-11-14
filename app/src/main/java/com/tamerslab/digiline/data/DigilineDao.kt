package com.tamerslab.digiline.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DigiLine v2 DAO (schema 56).
 *
 * History tables (question/quiz/negotiation-log/event records) are plain suspend reads so the
 * repositories can page them explicitly; live state the UI observes (profile badges, pending
 * question, negotiation session, persuasion arc, native sync) is exposed as a [Flow].
 *
 * All writes are upserts (`@Upsert`) so repositories can retry idempotently after a restart.
 */
@Dao
interface DigilineDao {

    // ---------------------------------------------------------------- DigilineProfile

    @Query("SELECT * FROM DigilineProfile WHERE individualId = :individualId LIMIT 1")
    suspend fun profile(individualId: String): DigilineProfileEntity?

    @Query("SELECT * FROM DigilineProfile WHERE individualId = :individualId LIMIT 1")
    fun observeProfile(individualId: String): Flow<DigilineProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: DigilineProfileEntity)

    @Query("DELETE FROM DigilineProfile WHERE individualId = :individualId")
    suspend fun deleteProfile(individualId: String)

    // --------------------------------------------------------- DigilineQuestionRecord

    @Query(
        "SELECT * FROM DigilineQuestionRecord WHERE individualId = :individualId " +
            "ORDER BY askedAt DESC, id DESC"
    )
    suspend fun questionRecords(individualId: String): List<DigilineQuestionRecordEntity>

    @Query(
        "SELECT * FROM DigilineQuestionRecord WHERE individualId = :individualId AND status = 'ASKED' " +
            "ORDER BY askedAt ASC, id ASC LIMIT 1"
    )
    suspend fun pendingQuestion(individualId: String): DigilineQuestionRecordEntity?

    /** Pending-card badge observation for the DigiLine list (only one asked question is pending). */
    @Query(
        "SELECT * FROM DigilineQuestionRecord WHERE individualId = :individualId AND status = 'ASKED' " +
            "ORDER BY askedAt ASC, id ASC LIMIT 1"
    )
    fun observePendingQuestion(individualId: String): Flow<DigilineQuestionRecordEntity?>

    @Query(
        "SELECT questionKey FROM DigilineQuestionRecord WHERE individualId = :individualId " +
            "AND askedAt >= :since"
    )
    suspend fun questionKeysSince(individualId: String, since: Long): List<String>

    /** Cross-individual anti-repeat window (plan §6.4: one species question per 24 h pool-wide). */
    @Query(
        "SELECT DISTINCT questionKey FROM DigilineQuestionRecord WHERE askedAt >= :since"
    )
    suspend fun questionKeysSinceAcrossIndividuals(since: Long): List<String>

    @Query(
        "SELECT COUNT(*) FROM DigilineQuestionRecord WHERE individualId = :individualId " +
            "AND answeredAt IS NOT NULL AND answeredAt >= :since"
    )
    suspend fun answeredQuestionCountSince(individualId: String, since: Long): Int

    @Upsert
    suspend fun upsertQuestionRecord(record: DigilineQuestionRecordEntity): Long

    @Query("DELETE FROM DigilineQuestionRecord WHERE individualId = :individualId")
    suspend fun deleteQuestionRecords(individualId: String)

    // ------------------------------------------------------------- DigilineQuizRecord

    @Query(
        "SELECT * FROM DigilineQuizRecord WHERE individualId = :individualId " +
            "ORDER BY answeredAt DESC, id DESC"
    )
    suspend fun quizRecords(individualId: String): List<DigilineQuizRecordEntity>

    @Query(
        "SELECT quizKey FROM DigilineQuizRecord WHERE individualId = :individualId " +
            "AND answeredAt >= :since"
    )
    suspend fun quizKeysSince(individualId: String, since: Long): List<String>

    @Query(
        "SELECT COUNT(*) FROM DigilineQuizRecord WHERE individualId = :individualId " +
            "AND answeredAt >= :since"
    )
    suspend fun quizCountSince(individualId: String, since: Long): Int

    @Upsert
    suspend fun upsertQuizRecord(record: DigilineQuizRecordEntity): Long

    @Query("DELETE FROM DigilineQuizRecord WHERE individualId = :individualId")
    suspend fun deleteQuizRecords(individualId: String)

    // --------------------------------------------------------- PersonalityTendency

    @Query("SELECT * FROM PersonalityTendency WHERE individualId = :individualId ORDER BY typeNumber ASC")
    suspend fun tendencies(individualId: String): List<PersonalityTendencyEntity>

    @Query("SELECT * FROM PersonalityTendency WHERE individualId = :individualId ORDER BY typeNumber ASC")
    fun observeTendencies(individualId: String): Flow<List<PersonalityTendencyEntity>>

    @Upsert
    suspend fun upsertTendencies(tendencies: List<PersonalityTendencyEntity>)

    @Query("DELETE FROM PersonalityTendency WHERE individualId = :individualId")
    suspend fun deleteTendencies(individualId: String)

    // --------------------------------------------------------- NativePersonalitySync

    @Query("SELECT * FROM NativePersonalitySync WHERE individualId = :individualId LIMIT 1")
    suspend fun nativeSync(individualId: String): NativePersonalitySyncEntity?

    @Query("SELECT * FROM NativePersonalitySync WHERE individualId = :individualId LIMIT 1")
    fun observeNativeSync(individualId: String): Flow<NativePersonalitySyncEntity?>

    @Query("SELECT * FROM NativePersonalitySync WHERE state = :state ORDER BY updatedAt ASC")
    suspend fun nativeSyncsInState(state: String): List<NativePersonalitySyncEntity>

    @Upsert
    suspend fun upsertNativeSync(sync: NativePersonalitySyncEntity)

    @Query("DELETE FROM NativePersonalitySync WHERE individualId = :individualId")
    suspend fun deleteNativeSync(individualId: String)

    // ------------------------------------------------------------ DigilineNegotiation

    @Query("SELECT * FROM DigilineNegotiation WHERE wildIndividualId = :wildIndividualId LIMIT 1")
    suspend fun negotiation(wildIndividualId: String): DigilineNegotiationEntity?

    @Query("SELECT * FROM DigilineNegotiation WHERE wildIndividualId = :wildIndividualId LIMIT 1")
    fun observeNegotiation(wildIndividualId: String): Flow<DigilineNegotiationEntity?>

    @Query("SELECT * FROM DigilineNegotiation ORDER BY updatedAt ASC")
    suspend fun negotiations(): List<DigilineNegotiationEntity>

    @Upsert
    suspend fun upsertNegotiation(negotiation: DigilineNegotiationEntity)

    @Query("DELETE FROM DigilineNegotiation WHERE wildIndividualId = :wildIndividualId")
    suspend fun deleteNegotiation(wildIndividualId: String)

    // --------------------------------------------------------- DigilineNegotiationLog

    @Query(
        "SELECT * FROM DigilineNegotiationLog WHERE wildIndividualId = :wildIndividualId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun negotiationLogs(wildIndividualId: String): List<DigilineNegotiationLogEntity>

    @Upsert
    suspend fun upsertNegotiationLog(log: DigilineNegotiationLogEntity): Long

    @Query("DELETE FROM DigilineNegotiationLog WHERE wildIndividualId = :wildIndividualId")
    suspend fun deleteNegotiationLogs(wildIndividualId: String)

    // ----------------------------------------------------------------- WildPersuasion

    @Query("SELECT * FROM WildPersuasion WHERE wildIndividualId = :wildIndividualId LIMIT 1")
    suspend fun wildPersuasion(wildIndividualId: String): WildPersuasionEntity?

    @Query("SELECT * FROM WildPersuasion WHERE wildIndividualId = :wildIndividualId LIMIT 1")
    fun observeWildPersuasion(wildIndividualId: String): Flow<WildPersuasionEntity?>

    @Query("SELECT * FROM WildPersuasion WHERE questOfferState = :questOfferState ORDER BY joinedAt ASC")
    suspend fun wildPersuasionsInQuestState(questOfferState: String): List<WildPersuasionEntity>

    @Upsert
    suspend fun upsertWildPersuasion(persuasion: WildPersuasionEntity)

    @Query("DELETE FROM WildPersuasion WHERE wildIndividualId = :wildIndividualId")
    suspend fun deleteWildPersuasion(wildIndividualId: String)

    // ---------------------------------------------------------- DigilineEventRecord

    @Query(
        "SELECT * FROM DigilineEventRecord WHERE individualId = :individualId " +
            "ORDER BY deliveredAt DESC, id DESC"
    )
    suspend fun eventRecords(individualId: String): List<DigilineEventRecordEntity>

    @Query(
        "SELECT eventKey FROM DigilineEventRecord WHERE individualId = :individualId " +
            "AND deliveredAt >= :since"
    )
    suspend fun eventKeysSince(individualId: String, since: Long): List<String>

    @Query(
        "SELECT * FROM DigilineEventRecord WHERE individualId = :individualId " +
            "AND eventKey = :eventKey ORDER BY deliveredAt DESC, id DESC LIMIT 1"
    )
    suspend fun lastEventRecord(individualId: String, eventKey: String): DigilineEventRecordEntity?

    @Upsert
    suspend fun upsertEventRecord(record: DigilineEventRecordEntity): Long

    @Query("DELETE FROM DigilineEventRecord WHERE individualId = :individualId")
    suspend fun deleteEventRecords(individualId: String)

    @Query("DELETE FROM DigilineEventRecord WHERE individualId = :individualId AND deliveredAt < :before")
    suspend fun deleteEventRecordsBefore(individualId: String, before: Long)
}
