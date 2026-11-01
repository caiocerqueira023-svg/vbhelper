package com.tamerslab.species

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.atomic.AtomicInteger
import com.tamerslab.quests.questParticipationAllowed
import com.tamerslab.quests.QuestAvailabilityIssue
import com.tamerslab.domain.digifarm.FarmResident
import com.tamerslab.farm.ParticipationClock

/** Database-owned gate: even short-lived repositories/importers use the same live policy. No network work. */
class PartnerParticipationGate(private val db: AppDatabase,
    private val catalogLoader: () -> CanonicalSpeciesCatalog?, private val clock: () -> Long = System::currentTimeMillis) {
    private val battles = AtomicInteger(0)

    /** Call on IO, preferably in the producer's Room transaction so ownership is consistent. */
    fun snapshot(): PartnerParticipationSnapshot {
        val sql = db.openHelper.readableDatabase
        val cards = sql.query("""SELECT c.id, COALESCE(c.speciesId, NULLIF(p.speciesName, ''), NULLIF(p.matchedName, '')),
            custom.originalKey, card.id FROM CardCharacter c LEFT JOIN Card card ON card.id = c.cardId
            LEFT JOIN SpeciesProfile p ON p.cardCharacterId = c.id
            LEFT JOIN SpeciesCatalogEntry custom ON custom.speciesKey = p.appearanceKey OR custom.originalKey = p.appearanceKey""").use { rows ->
            buildList { while (rows.moveToNext()) add(ParticipationCard(rows.getLong(0),
                if (rows.isNull(1)) null else rows.getString(1), if (rows.isNull(2)) null else rows.getString(2), !rows.isNull(3))) }
        }
        val owned = sql.query("SELECT id, individualId, charId FROM UserCharacter").use { rows ->
            buildMap { while (rows.moveToNext()) put(rows.getLong(0), rows.getString(1) to rows.getLong(2)) }
        }
        val individuals = sql.query("""SELECT individualId, cardCharacterId FROM WildRelationship
            UNION ALL SELECT individualId, cardCharacterId FROM WorldSpawn
            UNION ALL SELECT individualId, charId FROM UserCharacter""").use { rows ->
            buildMap { while (rows.moveToNext()) put(rows.getString(0), rows.getLong(1)) }
        }
        val references = sql.query("""SELECT d.individualId, COALESCE(NULLIF(d.speciesName, ''), w.speciesNameSnapshot)
            FROM DigimonIndividual d LEFT JOIN WildRelationship w ON w.individualId = d.individualId""").use { rows ->
            buildMap { while (rows.moveToNext()) put(rows.getString(0), if (rows.isNull(1)) null else rows.getString(1)) }
        }
        return PartnerParticipationSnapshot.build(catalogLoader(), cards, owned, individuals, references)
    }

    fun observe(): Flow<PartnerParticipationSnapshot> = db.invalidationTracker.createFlow(
        "Card", "CardCharacter", "SpeciesProfile", "SpeciesCatalogEntry", "UserCharacter", "WorldSpawn", "WildRelationship", "DigimonIndividual").map {
        db.withTransaction { snapshot() }
    }.flowOn(Dispatchers.IO)

    fun requireCurrent(snapshot: PartnerParticipationSnapshot) {
        check(snapshot.matches(this.snapshot())) { "The collection changed. Choose the participants again." }
    }

    /** Tracks foreground battle lifetimes independently of rendering preferences. */
    fun battleLease(): AutoCloseable {
        battles.incrementAndGet()
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) battles.decrementAndGet() }
    }

    /** Resume retired mode pauses without replaying paused time or changing the active partner. */
    fun reconcileLocked(eligible: PartnerParticipationSnapshot = snapshot()) {
        val sql = db.openHelper.writableDatabase
        val now = clock()
        val previousPauses = sql.query("SELECT individualId, pausedAt FROM ParticipationPause").use { rows ->
            buildMap { while (rows.moveToNext()) put(rows.getString(0), rows.getLong(1)) }
        }
        val residents = sql.query("""SELECT individualId, farmId, updatedAt, activityStartedAt,
            activity, energy, satiety, social, funLevel FROM FarmResident""").use { rows ->
            buildList { while (rows.moveToNext()) add(FarmResident(rows.getString(0), rows.getString(1), 0f, 0f, 0f, 0f,
                updatedAt = rows.getLong(2), activityStartedAt = rows.getLong(3), activity = rows.getString(4),
                energy = rows.getInt(5), satiety = rows.getInt(6), social = rows.getInt(7), funLevel = rows.getInt(8))) }
        }
        residents.forEach { resident ->
            val id = resident.individualId
            if (id !in previousPauses) return@forEach
            val paused = ParticipationClock.pause(resident, now, id in previousPauses)
            sql.execSQL("""UPDATE FarmResident SET updatedAt = ?, activityStartedAt = ?, energy = ?, satiety = ?,
                social = ?, funLevel = ? WHERE individualId = ?""",
                arrayOf<Any?>(paused.updatedAt, paused.activityStartedAt, paused.energy, paused.satiety, paused.social, paused.funLevel, id))
        }
        previousPauses.forEach { (id, pausedAt) ->
            val seconds = ((now - pausedAt).coerceAtLeast(0) / 1000)
            sql.execSQL("UPDATE Adventure SET finishesAdventure = finishesAdventure + ? WHERE characterId IN (SELECT id FROM UserCharacter WHERE individualId = ?)",
                arrayOf<Any?>(seconds, id))
            sql.execSQL("DELETE FROM ParticipationPause WHERE individualId = ?", arrayOf<Any?>(id))
        }
        val quests = sql.query("SELECT id FROM QuestInstance WHERE state IN ('OFFERED', 'ACTIVE', 'READY')").use { rows ->
            buildList { while (rows.moveToNext()) add(rows.getString(0)) }
        }
        quests.forEach { id ->
            val quest = db.questDao().getQuest(id) ?: return@forEach
            val usable = db.questParticipationAllowed(quest, eligible)
            val issue = when {
                !usable && quest.availabilityIssue == null -> QuestAvailabilityIssue.SOURCE_MODE_INACTIVE
                usable && quest.availabilityIssue == QuestAvailabilityIssue.SOURCE_MODE_INACTIVE -> null
                else -> quest.availabilityIssue
            }
            if (issue != quest.availabilityIssue) db.questDao().updateQuest(quest.copy(availabilityIssue = issue, revision = quest.revision + 1))
        }
    }

}
