package com.tamerslab.transfer.data

import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.domain.transfer.StoryProgressDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Story-domain repository semantics: provenance sticks to the first seed and clamping applies. */
class ProgressDomainRepositoryTest {
    private class FakeDao : ProgressDomainDao {
        var storyRow: StoryProgressDomain? = null
        private val storyFlow = MutableStateFlow<StoryProgressDomain?>(null)

        override suspend fun story(individualId: String): StoryProgressDomain? = storyRow
        override fun observeStory(individualId: String): Flow<StoryProgressDomain?> = storyFlow
        override suspend fun saveStory(progress: StoryProgressDomain) {
            storyRow = progress
            storyFlow.value = progress
        }
        override suspend fun forgetStory(individualId: String) { storyRow = null; storyFlow.value = null }
        override suspend fun storyWithoutSeed(): List<String> = emptyList()
    }

    private fun story(level: Int = 20, seed: ProgressSeedSource = ProgressSeedSource.IMPORT) = StoryProgressDomain(
        individualId = "i1", level = level, expTotal = 10L, hp = 10, maxHp = 20, mp = 1, maxMp = 2,
        attack = 3, defense = 4, spirit = 5, speed = 6, aptitude = null,
        seededAt = 100L, seedSource = seed, revision = 0L, updatedAt = 100L,
    )

    @Test
    fun `story observations keep their seed and clamp values`() = runBlocking {
        val dao = FakeDao()
        val repo = ProgressDomainRepository(dao)
        repo.recordStory(story(level = 500))
        assertEquals(99, dao.storyRow?.level)
        assertEquals(ProgressSeedSource.IMPORT, dao.storyRow?.seedSource)
        repo.recordStory(story(level = 5))
        assertEquals(5, dao.storyRow?.level)
        assertEquals(ProgressSeedSource.IMPORT, dao.storyRow?.seedSource)
        assertEquals(2L, dao.storyRow?.revision)
    }

    @Test
    fun `first import stamp is preserved on later observations`() = runBlocking {
        val dao = FakeDao()
        val repo = ProgressDomainRepository(dao)
        assertTrue(repo.needsStorySeed("i1"))
        repo.recordStory(story(seed = ProgressSeedSource.VB))
        assertEquals(ProgressSeedSource.VB, dao.storyRow?.seedSource)
        repo.recordStory(story(level = 30).copy(seedSource = ProgressSeedSource.IMPORT))
        assertEquals(ProgressSeedSource.VB, dao.storyRow?.seedSource)
        assertEquals(30, dao.storyRow?.level)
    }
}
