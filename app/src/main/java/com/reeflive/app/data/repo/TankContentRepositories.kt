package com.reeflive.app.data.repo

import androidx.room.withTransaction
import com.reeflive.app.data.db.EquipmentDao
import com.reeflive.app.data.db.JournalDao
import com.reeflive.app.data.db.LightDao
import com.reeflive.app.data.db.LivestockDao
import com.reeflive.app.data.db.ReefDatabase
import com.reeflive.app.data.db.toEntity
import com.reeflive.app.data.db.toModel
import com.reeflive.core.model.CareTask
import com.reeflive.core.model.Equipment
import com.reeflive.core.model.EventType
import com.reeflive.core.model.HealthLog
import com.reeflive.core.model.Ids
import com.reeflive.core.model.JournalEntry
import com.reeflive.core.model.LightCurve
import com.reeflive.core.model.LightPoint
import com.reeflive.core.model.LightSchedule
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.LivestockStatus
import com.reeflive.core.model.Photo
import com.reeflive.core.species.SpeciesDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class LivestockRepository(
    private val dao: LivestockDao,
    private val events: EventRepository,
    private val species: SpeciesDb,
    private val clock: Clock,
) {
    /** Organisms currently in the tank. Used by the Live Tank to populate the simulation. */
    fun alive(): Flow<List<Livestock>> = dao.alive(TANK_ID).map { l -> l.map { it.toModel() } }

    fun all(): Flow<List<Livestock>> = dao.all(TANK_ID).map { l -> l.map { it.toModel() } }

    fun byId(id: String): Flow<Livestock?> = dao.byId(id).map { it?.toModel() }

    fun healthLogs(id: String): Flow<List<HealthLog>> = dao.healthLogs(id).map { l -> l.map { it.toModel() } }

    /** Inserts or updates; a new organism also logs an ADD_LIVESTOCK event. */
    suspend fun save(l: Livestock, isNew: Boolean) {
        dao.upsert(l.toEntity())
        if (isNew) {
            val name = species[l.speciesId]?.common ?: l.speciesId
            events.log(EventType.ADD_LIVESTOCK, if (l.nickname.isBlank()) "Added $name" else "Added ${l.nickname} ($name)", at = l.acquiredAt ?: clock.now())
        }
    }

    /** Status change (rehomed/deceased/back in tank) with a REMOVE_LIVESTOCK event when it leaves. */
    suspend fun setStatus(l: Livestock, status: LivestockStatus) {
        val now = clock.now()
        dao.upsert(l.copy(status = status, statusChangedAt = now).toEntity())
        if (status != LivestockStatus.ALIVE && l.status == LivestockStatus.ALIVE) {
            events.log(EventType.REMOVE_LIVESTOCK, "${l.nickname.ifBlank { species[l.speciesId]?.common ?: l.speciesId }} ${status.label.lowercase()}", at = now)
        }
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun addHealth(h: HealthLog) {
        dao.upsertHealth(h.toEntity())
        if (!h.treatment.isNullOrBlank()) events.log(EventType.TREATMENT, "${h.treatment} — ${h.symptom}", at = h.at)
    }

    suspend fun deleteHealth(id: String) = dao.deleteHealth(id)
}

class EquipmentRepository(private val dao: EquipmentDao, private val tasks: TaskRepository, private val clock: Clock) {
    fun all(): Flow<List<Equipment>> = dao.all(TANK_ID).map { l -> l.map { it.toModel() } }

    fun byId(id: String): Flow<Equipment?> = dao.byId(id).map { it?.toModel() }

    /**
     * Saves equipment. A service interval creates or updates a linked care task ("Service <name>");
     * clearing the interval deactivates it.
     */
    suspend fun save(e: Equipment) {
        var item = e
        val interval = e.serviceIntervalDays
        val linked = e.taskId?.let { tasks.taskOnce(it) }
        if (interval != null && interval > 0) {
            val task = linked?.copy(intervalDays = interval, weekdaysMask = null, active = true, equipmentId = e.id)
                ?: CareTask(
                    id = Ids.new(), tankId = TANK_ID, title = "Service ${e.name}", intervalDays = interval,
                    equipmentId = e.id, createdAt = clock.now(),
                )
            tasks.save(task)
            item = e.copy(taskId = task.id)
        } else if (linked != null) {
            tasks.save(linked.copy(active = false))
        }
        dao.upsert(item.toEntity())
    }

    suspend fun markServiced(e: Equipment) = dao.upsert(e.copy(lastServicedAt = clock.now()).toEntity())

    suspend fun delete(id: String) = dao.delete(id)
}

/** A journal entry with its photos. */
data class JournalItem(val entry: JournalEntry, val photos: List<Photo>)

class JournalRepository(private val db: ReefDatabase, private val dao: JournalDao, private val events: EventRepository) {
    val entries: Flow<List<JournalItem>> = combine(dao.all(TANK_ID), dao.photos()) { entries, photos ->
        val byJournal = photos.groupBy { it.journalId }
        entries.map { e -> JournalItem(e.toModel(), byJournal[e.id].orEmpty().map { it.toModel() }) }
    }

    fun byId(id: String): Flow<JournalItem?> = entries.map { l -> l.firstOrNull { it.entry.id == id } }

    fun photos(): Flow<List<Photo>> = dao.photos().map { l -> l.map { it.toModel() } }

    /** Saves an entry with photos already copied into app storage; also logs a NOTE event for chart markers. */
    suspend fun save(entry: JournalEntry, photos: List<Photo>, isNew: Boolean) {
        db.withTransaction {
            dao.upsert(entry.toEntity())
            dao.insertPhotos(photos.map { it.copy(journalId = entry.id).toEntity() })
        }
        if (isNew) {
            val title = entry.text.lineSequence().firstOrNull().orEmpty().take(60).ifBlank { "Journal entry" }
            events.log(EventType.NOTE, title, at = entry.at, journalId = entry.id)
        }
    }

    /** Deletes an entry; returns photo paths so the caller can remove the files. */
    suspend fun delete(id: String): List<String> {
        val photos = dao.photosFor(id)
        db.withTransaction {
            photos.forEach { dao.deletePhoto(it) }
            dao.delete(id)
        }
        return photos.map { it.path }
    }
}

class LightScheduleRepository(private val dao: LightDao, private val events: EventRepository) {
    /** The active schedule; falls back to the AI soft-coral preset so the Live Tank always has light. */
    fun active(): Flow<LightSchedule> = dao.active(TANK_ID).map { e ->
        e?.toModel()?.takeIf { it.points.isNotEmpty() } ?: DEFAULT
    }

    suspend fun activeOnce(): LightSchedule = active().first()

    suspend fun save(schedule: LightSchedule, logEvent: Boolean = true) {
        dao.upsert(schedule.copy(active = true).toEntity())
        if (logEvent) events.log(EventType.LIGHT_CHANGE, "Light schedule: ${schedule.name}")
    }

    suspend fun applyPreset(name: String, points: List<LightPoint>) {
        val current = activeOnce()
        save(current.copy(id = if (current.id == DEFAULT.id) Ids.new() else current.id, name = name, points = points))
    }

    companion object {
        val DEFAULT = LightSchedule("default-light", TANK_ID, "AI Soft Coral", LightCurve.AI_SOFT_CORAL, active = true)
    }
}
