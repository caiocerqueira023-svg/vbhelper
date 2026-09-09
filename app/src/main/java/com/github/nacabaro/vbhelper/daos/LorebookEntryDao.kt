package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LorebookEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LorebookEntry): Long

    @Update
    suspend fun update(entry: LorebookEntry)

    @Delete
    suspend fun delete(entry: LorebookEntry)

    @Query("SELECT * FROM LorebookEntry ORDER BY priority DESC, title ASC")
    fun getAll(): Flow<List<LorebookEntry>>

    @Query("SELECT * FROM LorebookEntry WHERE enabled = 1")
    suspend fun getAllEnabled(): List<LorebookEntry>
}
