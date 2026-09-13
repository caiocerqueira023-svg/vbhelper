package com.github.nacabaro.vbhelper.source

import android.util.Log
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepair
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import java.util.concurrent.Callable

/** Blocking operations: call on IO or from an existing Room transaction. */
class EvolutionHistoryRepository(private val db: AppDatabase) {
    data class Report(val checked: Int, val repaired: Int, val incomplete: List<Long>)

    fun repairAll(): Report = db.runInTransaction(Callable {
        val ids = db.evolutionHistoryDao().getStoredCharacterIds()
        val graphs = mutableMapOf<Long, Graph>()
        var repaired = 0
        val incomplete = mutableListOf<Long>()
        for (id in ids) {
            val outcome = repairInTransaction(id, graphs) ?: continue
            if (outcome.first) repaired++
            if (!outcome.second) incomplete += id
        }
        Report(ids.size, repaired, incomplete)
    })

    fun repairCharacter(characterId: Long) = db.runInTransaction(Callable {
        repairInTransaction(characterId, mutableMapOf())
    })

    private data class Graph(
        val species: List<EvolutionHistoryRepair.Species>,
        val routes: List<EvolutionHistoryRepair.Route>,
    )

    private fun repairInTransaction(characterId: Long, graphs: MutableMap<Long, Graph>): Pair<Boolean, Boolean>? {
        val dao = db.evolutionHistoryDao()
        val current = dao.getCurrentSpecies(characterId) ?: return null
        val graph = graphs.getOrPut(current.cardId) { Graph(dao.getSpecies(current.cardId), dao.getRoutes(current.cardId)) }
        val history = dao.getHistory(characterId)
        val result = EvolutionHistoryRepair.repair(current, graph.species, graph.routes, history, System.currentTimeMillis())
        val changed = history != result.entries
        if (changed) {
            // Only history rows are replaced. Identity, stats, chats and the Dex are untouched.
            dao.deleteHistory(characterId)
            db.userCharacterDao().insertTransformationHistory(*result.entries.map {
                TransformationHistory(monId = characterId, stageId = it.speciesId, transformationDate = it.date)
            }.toTypedArray())
            if (!result.complete) {
                Log.w("EvolutionHistory", "No complete Baby I route on card ${current.cardId} for character $characterId")
            }
        }
        return changed to result.complete
    }
}
