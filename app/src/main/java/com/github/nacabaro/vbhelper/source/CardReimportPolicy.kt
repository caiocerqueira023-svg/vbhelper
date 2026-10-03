package com.github.nacabaro.vbhelper.source

data class CardReimportCandidate(val id: Long, val name: String, val attackMatches: Boolean, val missingArt: Boolean)

/** One intended card may be refreshed; ambiguous custom variants never get bulk updates. */
object CardReimportPolicy {
    fun select(candidates: List<CardReimportCandidate>, importedName: String): Long? {
        val named = candidates.filter { it.name.equals(importedName, ignoreCase = true) }
        if (named.isNotEmpty()) return (named.singleOrNull() ?: named.filter { it.attackMatches }.singleOrNull())?.id
        return candidates.filter { it.attackMatches }.singleOrNull()?.id
            ?: candidates.singleOrNull()?.takeIf { it.missingArt }?.id
    }
}
