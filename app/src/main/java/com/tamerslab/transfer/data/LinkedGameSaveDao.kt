package com.tamerslab.transfer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tamerslab.domain.transfer.LinkedGameSave
import com.tamerslab.domain.transfer.LinkedSaveObservation
import com.tamerslab.domain.transfer.LinkedSaveRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkedGameSaveDao {
    @Transaction @Query("SELECT * FROM LinkedGameSave ORDER BY linkedAt, sourceId")
    fun observe(): Flow<List<LinkedSaveRecord>>

    @Query("SELECT * FROM LinkedGameSave ORDER BY linkedAt, sourceId")
    suspend fun all(): List<LinkedGameSave>

    @Query("SELECT * FROM LinkedGameSave WHERE sourceId = :id")
    suspend fun get(id: String): LinkedGameSave?

    @Query("SELECT * FROM LinkedGameSave WHERE documentKey = :key")
    suspend fun byDocument(key: String): LinkedGameSave?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: LinkedGameSave): Long

    @Upsert suspend fun saveObservation(observation: LinkedSaveObservation)

    @Query("UPDATE LinkedGameSave SET reviewedCopyRevision=:revision, reviewedSaveCopy=:copy WHERE sourceId=:id")
    suspend fun selectCopy(id: String, revision: String, copy: Int)

    @Query("UPDATE LinkedGameSave SET lastAttemptAt = :time WHERE sourceId = :id")
    suspend fun markAttempt(id: String, time: Long)

    @Query("UPDATE LinkedGameSave SET lastSuccessAt = :time, lastError = NULL WHERE sourceId = :id")
    suspend fun markSuccess(id: String, time: Long)

    @Query("UPDATE LinkedGameSave SET lastError = :code WHERE sourceId = :id")
    suspend fun markFailure(id: String, code: String)

    @Query("UPDATE LinkedGameSave SET label = :label WHERE sourceId = :id")
    suspend fun rename(id: String, label: String)

    @Query("UPDATE LinkedGameSave SET documentUri = :uri, grantUri = :grant, displayName = :name, lastError = 'REFRESH_NEEDED' WHERE sourceId = :id")
    suspend fun updateAccess(id: String, uri: String, grant: String, name: String)

    @Query("DELETE FROM LinkedGameSave WHERE sourceId = :id")
    suspend fun unlink(id: String)
}
