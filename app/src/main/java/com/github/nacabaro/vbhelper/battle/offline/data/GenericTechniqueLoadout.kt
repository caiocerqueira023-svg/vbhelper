package com.github.nacabaro.vbhelper.battle.offline.data

/** Pure validation shared by persistence, setup and the selection screen. */
object GenericTechniqueLoadout {
    const val REGULAR_SLOT_COUNT = 3

    private val selectableIds = GenericTechniqueCatalog.selectableEntries
        .map { it.definition.techniqueId }
        .toSet()

    fun resolve(storedIds: List<String>): List<String> {
        val resolved = storedIds.filter { it in selectableIds }.distinct().take(REGULAR_SLOT_COUNT).toMutableList()
        GenericTechniqueCatalog.defaultTechniqueIds.forEach { fallback ->
            if (resolved.size < REGULAR_SLOT_COUNT && fallback !in resolved) resolved += fallback
        }
        return resolved.take(REGULAR_SLOT_COUNT)
    }
}
