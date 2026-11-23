package com.tamerslab.transfer.data

import com.tamerslab.domain.transfer.ProgressDomainRules
import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.domain.transfer.StoryProgressDomain
import kotlinx.coroutines.flow.Flow

/**
 * Canonical progress-domain store. Answers "which progress block does this target use" without doing
 * any conversion; seeding policy lives in the transfer services because it needs game species tables.
 *
 * Phase order for this system:
 * 1. the sending/importing service asks for the domain block;
 * 2. if it is absent, that service creates it once (VB→story or story→VB bootstrap) and saves it;
 * 3. every later transfer reads the block and writes it as-is.
 */
class ProgressDomainRepository(private val dao: ProgressDomainDao) {
    suspend fun story(individualId: String): StoryProgressDomain? = dao.story(individualId)
    fun observeStory(individualId: String): Flow<StoryProgressDomain?> = dao.observeStory(individualId)
    suspend fun saveStory(progress: StoryProgressDomain) = dao.saveStory(progress)
    suspend fun forgetStory(individualId: String) = dao.forgetStory(individualId)

    suspend fun forget(individualId: String) = dao.forgetStory(individualId)

    /**
     * Records story progression observed by an import. Seeding rules are explicit: an import is an
     * authoritative observation and stamps [ProgressSeedSource.IMPORT] only when no block existed.
     * Subsequent imports update the tracked values but keep the original seed provenance.
     */
    suspend fun recordStory(observed: StoryProgressDomain) {
        val existing = dao.story(observed.individualId)
        val clamped = ProgressDomainRules.clampStory(observed)
        dao.saveStory(
            clamped.copy(
                seedSource = existing?.seedSource ?: observed.seedSource,
                seededAt = existing?.seededAt ?: observed.seededAt,
                revision = (existing?.revision ?: 0L) + 1L,
            ),
        )
    }

    /** True when this individual has never had a story block; used to show first-send provenance. */
    suspend fun needsStorySeed(individualId: String): Boolean =
        ProgressDomainRules.needsStorySeed(dao.story(individualId))

}
