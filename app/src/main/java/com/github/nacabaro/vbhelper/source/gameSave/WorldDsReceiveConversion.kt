package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.WorldDsStatConversion
import com.github.nacabaro.vbhelper.utils.DeviceType

/** App-side values shared by the send and receive conversion paths. */
internal data class WorldDsAppReading(
    val appStats: WorldDsStatConversion.AppStats,
    val stage: Int,
    val baseHp: Int,
    val baseBp: Int,
    val baseAp: Int,
    /** Canonical species reference from the partner's card; used when the individual has no species name. */
    val speciesId: String?,
    val displayName: String?,
)

internal object WorldDsAppStatsReader {
    suspend fun read(db: AppDatabase, individualId: String): WorldDsAppReading? {
        val characters = db.userCharacterDao().getByIndividualId(individualId)
        val character = characters.firstOrNull { it.characterType == DeviceType.APP } ?: characters.firstOrNull()
            ?: return null
        val assigned = character.exportCardCharacterId
        val card = db.characterDao().getById(assigned ?: character.charId) ?: return null
        val progress = db.appPartnerStateDao().state(individualId)
        val be = db.userCharacterDao().getBeDataOrNull(character.id)
        val trainingHp = progress?.trainingHp ?: be?.trainingHp ?: 0
        val trainingBp = progress?.trainingBp ?: be?.trainingBp ?: 0
        val trainingAp = progress?.trainingAp ?: be?.trainingAp ?: 0
        val stats = WorldDsStatConversion.AppStats(
            stage = card.stage.coerceIn(0, 5),
            baseHp = card.baseHp,
            baseBp = card.baseBp,
            baseAp = card.baseAp,
            trainingHp = if (assigned != null) 0 else trainingHp,
            trainingBp = if (assigned != null) 0 else trainingBp,
            trainingAp = if (assigned != null) 0 else trainingAp,
            vitalPoints = character.vitalPoints,
            mood = character.mood,
            trophies = character.trophies,
            wins = character.totalBattlesWon,
            losses = character.totalBattlesLost,
            ageInDays = character.ageInDays,
            activityLevel = character.activityLevel,
            generation = progress?.generation ?: 1,
            rank = progress?.rank ?: 0,
        )
        return WorldDsAppReading(stats, card.stage.coerceIn(0, 5), card.baseHp, card.baseBp, card.baseAp,
            card.catalogSpeciesId ?: card.speciesId, card.speciesId ?: card.catalogSpeciesId)
    }
}

internal object WorldDsReceiveConversion {
    /** Null when the archived record has no complete native stat set (for example an egg record). */
    fun nativeStats(record: ObservedDigimon): WorldDsStatConversion.NativeStats? {
        val parameters = record.nativeParameters ?: return null
        if (parameters.expCounters.size != WorldDsStatConversion.EXP_CHANNELS) return null
        return runCatching {
            WorldDsStatConversion.NativeStats(record.level, parameters.aptitude, parameters.hp, parameters.maxHp,
                parameters.mp, parameters.maxMp, parameters.attack, parameters.defense, parameters.spirit, parameters.speed,
                parameters.expCounters)
        }.getOrNull()
    }
}