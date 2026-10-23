package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.chat.NativeSourceContext
import com.github.nacabaro.vbhelper.chat.NativeSourceFacts
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.TimeStrangerSaveReader
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IndividualContinuityRepository(private val db: AppDatabase) {
    fun history(id: String) = db.transferContinuityDao().history(id)
    fun deltas(id: String) = db.transferContinuityDao().deltas(id)
    suspend fun projection(id: String) = db.transferContinuityDao().projection(id)
    suspend fun sourceContext(id: String, language: String): String = withContext(Dispatchers.IO) {
        UniversalJourneyRepository(db).synchronizeAppChanges(id)
        val snapshotId = db.nativeMaterializationDao().latestSnapshotId(id) ?: return@withContext ""
        val snapshot = db.nativeMaterializationDao().snapshot(snapshotId) ?: return@withContext ""
        val binding = db.nativeMaterializationDao().binding(snapshot.bindingId) ?: return@withContext ""
        val record = Gson().fromJson(snapshot.recordJson, ObservedDigimon::class.java)
        val game = if (binding.gameId == TimeStrangerSaveReader.GAME_ID) "Digimon Story Time Stranger" else binding.gameId
        NativeSourceContext.format(NativeSourceFacts(game, snapshot.importedAt, record.storedName, record.nativeSpeciesId,
            record.level, record.totalExp, record.personalityId, record.talentRaw, record.bondRaw, record.previousSpeciesIds,
            db.nativeMaterializationDao().mapping(snapshotId)?.canonicalSpeciesId), language)
    }
}
