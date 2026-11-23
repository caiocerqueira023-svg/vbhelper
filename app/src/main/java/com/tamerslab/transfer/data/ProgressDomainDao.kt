package com.tamerslab.transfer.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tamerslab.domain.transfer.StoryProgressDomain
import kotlinx.coroutines.flow.Flow

/** Persistence for the two independent progress domains. No conversion policy lives here. */
@Dao
interface ProgressDomainDao {
    @Query("SELECT * FROM story_progress WHERE individualId = :individualId")
    suspend fun story(individualId: String): StoryProgressDomain?

    @Query("SELECT * FROM story_progress WHERE individualId = :individualId")
    fun observeStory(individualId: String): Flow<StoryProgressDomain?>

    @Upsert
    suspend fun saveStory(progress: StoryProgressDomain)

    @Query("DELETE FROM story_progress WHERE individualId = :individualId")
    suspend fun forgetStory(individualId: String)

    @Query("SELECT individualId FROM story_progress WHERE seededAt = 0")
    suspend fun storyWithoutSeed(): List<String>
}
