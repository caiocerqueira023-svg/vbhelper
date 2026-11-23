package com.tamerslab.gamesave

/**
 * Copy authority for every registered NDS Story layout: either an exact user review or the
 * game-written newest copy. Profile identity comes from [NdsStoryProfiles], so a second layout
 * inherits the same rule; [WorldDsCopySelection] is the World DS facade.
 */
object NdsStoryCopySelection {
    /**
     * The copy the game wrote last, from the save counter at the profile's sequence offset. A copy
     * whose integrity header does not match its bytes is ignored, matching the game's fallback
     * policy. Observations without a registered profile return null.
     *
     * A tie is not automatically ambiguous: the engine itself can produce one. Writing into an
     * erased slot stores counter 1 (the erased counter wraps), so a save edited by VBHelper to
     * counter 1 and then re-saved by the game into its empty slot ends with **both** slots at 1.
     * When every tied copy holds the same individuals, either choice is safe; the lowest copy is
     * returned so a writer can bump it into a unique newest slot again. Tied copies that disagree
     * still yield null: there is no defensible newest copy then.
     */
    fun latestCopy(observation: SaveObservation): Int? {
        if (!NdsStoryProfiles.isNdsStory(observation)) return null
        val sequences = observation.records.mapNotNull { record ->
            val parameters = record.nativeParameters ?: return@mapNotNull null
            if (observation.copyIntegrity[parameters.saveBlock] == false) return@mapNotNull null
            val sequence = parameters.saveSequence ?: return@mapNotNull null
            parameters.saveBlock to sequence
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.max() }
        if (sequences.isEmpty()) return null
        val newest = sequences.values.max()
        val ties = sequences.filterValues { it == newest }.keys
        if (ties.size == 1) return ties.single()
        if (NdsStoryProfiles.forObservation(observation)?.resolveEquivalentCopyTie != true) return null
        val chosen = ties.min()
        return chosen.takeIf { candidate -> ties.all { equivalentCopies(observation, candidate, it) } }
    }

    /**
     * Whether two copies describe the same ownership state: same individuals with the same native
     * bytes. Party order, positions, save counters and record keys are deliberately ignored — the
     * engine rotates the party ring when it saves, which must not make two identical copies
     * "different".
     */
    fun equivalentCopies(observation: SaveObservation, first: Int, second: Int): Boolean {
        fun signature(copy: Int): Set<Triple<String?, String?, Long>> = observation.records
            .filter { it.nativeParameters?.saveBlock == copy }
            .map { Triple(it.nativeIdentity, it.nativeRecordBase64, it.nativeSpeciesId) }
            .toSet()
        val left = signature(first)
        val right = signature(second)
        return left.isNotEmpty() && left == right
    }

    /**
     * The copy moves and admissions must obey. A reviewed copy wins for that exact revision;
     * otherwise the unique newest valid copy is authoritative. Null means no defensible choice.
     */
    fun authoritativeCopy(observation: SaveObservation): Int? {
        if (!NdsStoryProfiles.isNdsStory(observation)) return null
        if (ObservationNotice.SAVE_COPY_USER_CONFIRMED in observation.notices) {
            return observation.records.filterNot { it.issues.contains(RecordIssue.AMBIGUOUS_SAVE_BLOCK) }
                .mapNotNull { it.nativeParameters?.saveBlock }.distinct().singleOrNull()
        }
        return latestCopy(observation)
    }

    fun recordsForCopy(observation: SaveObservation, copy: Int): List<ObservedDigimon> =
        observation.records.filter { it.nativeParameters?.saveBlock == copy }

    fun apply(observation: SaveObservation, reviewedRevision: String?, copy: Int?): SaveObservation {
        if (!NdsStoryProfiles.isNdsStory(observation) || reviewedRevision != observation.contentRevision || copy == null) {
            return observation
        }
        return select(observation, copy, ObservationNotice.SAVE_COPY_USER_CONFIRMED)
    }

    /**
     * Uses the newest valid copy without claiming that a human reviewed it. The automatic choice is
     * recomputed on every read and never survives as stored review authority.
     */
    fun applyAutomatic(observation: SaveObservation, copy: Int? = latestCopy(observation)): SaveObservation {
        if (!NdsStoryProfiles.isNdsStory(observation) || copy == null ||
            ObservationNotice.SAVE_COPY_USER_CONFIRMED in observation.notices) return observation
        val populated = observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct()
        if (populated.size < 2) return observation
        return select(observation, copy, ObservationNotice.SAVE_COPY_AUTO_SELECTED)
    }

    private fun select(observation: SaveObservation, copy: Int, notice: ObservationNotice): SaveObservation {
        require(copy in observation.records.mapNotNull { it.nativeParameters?.saveBlock }.toSet())
        val records = observation.records.map { record ->
            val issues = record.issues.filter { it != RecordIssue.AMBIGUOUS_SAVE_BLOCK }.toMutableList()
            if (record.nativeParameters?.saveBlock != copy) issues += RecordIssue.AMBIGUOUS_SAVE_BLOCK
            record.copy(issues = issues.toList(), membership = if (issues.isEmpty()) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW)
        }
        return observation.copy(records = records, notices = observation.notices.filter {
            it != ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED &&
                it != ObservationNotice.SAVE_COPY_AUTO_SELECTED &&
                it != ObservationNotice.SAVE_COPY_USER_CONFIRMED
        } + notice)
    }
}
