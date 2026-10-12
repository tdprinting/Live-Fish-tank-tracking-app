package com.reeflive.app.data.repo

import com.reeflive.app.data.db.AlertDao
import com.reeflive.app.data.db.EventDao
import com.reeflive.app.data.db.TaskDao
import com.reeflive.app.data.db.toEntity
import com.reeflive.app.data.db.toModel
import com.reeflive.core.model.Alert
import com.reeflive.core.model.CareTask
import com.reeflive.core.model.DoseDetail
import com.reeflive.core.model.EventType
import com.reeflive.core.model.FeedDetail
import com.reeflive.core.model.Ids
import com.reeflive.core.model.ReefEvent
import com.reeflive.core.model.TaskCompletion
import com.reeflive.core.model.WaterChangeDetail
import com.reeflive.core.status.AlertDraft
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val EventJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class EventRepository(private val dao: EventDao, private val clock: Clock) {
    fun recent(limit: Int = 20): Flow<List<ReefEvent>> = dao.recent(TANK_ID, limit).map { l -> l.map { it.toModel() } }

    fun since(fromMs: Long): Flow<List<ReefEvent>> = dao.since(TANK_ID, fromMs).map { l -> l.map { it.toModel() } }

    suspend fun ofTypesSince(types: List<EventType>, fromMs: Long): List<ReefEvent> =
        dao.ofTypesSince(TANK_ID, types.map { it.name }, fromMs).map { it.toModel() }

    suspend fun log(type: EventType, title: String, detailJson: String = "{}", at: Long = clock.now(), journalId: String? = null): ReefEvent {
        val e = ReefEvent(Ids.new(), TANK_ID, type, at, title, detailJson, journalId)
        dao.upsert(e.toEntity())
        return e
    }

    suspend fun logFeed(food: String, amount: String? = null, at: Long = clock.now()) =
        log(EventType.FEED, "Fed $food", EventJson.encodeToString(FeedDetail.serializer(), FeedDetail(food, amount)), at)

    suspend fun logWaterChange(gallons: Double, fraction: Double, mix: Map<String, Double> = emptyMap(), at: Long = clock.now()) =
        log(
            EventType.WATER_CHANGE,
            "Water change ${Fmt.trim(gallons, 1)} gal",
            EventJson.encodeToString(WaterChangeDetail.serializer(), WaterChangeDetail(gallons, fraction, mix)),
            at,
        )

    suspend fun logDose(detail: DoseDetail, title: String, at: Long = clock.now()) =
        log(EventType.DOSE, title, EventJson.encodeToString(DoseDetail.serializer(), detail), at)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun restore(e: ReefEvent) = dao.upsert(e.toEntity())

    companion object {
        fun doseDetail(e: ReefEvent): DoseDetail? =
            if (e.type != EventType.DOSE) null else runCatching { EventJson.decodeFromString(DoseDetail.serializer(), e.detailJson) }.getOrNull()

        fun waterChangeDetail(e: ReefEvent): WaterChangeDetail? =
            if (e.type != EventType.WATER_CHANGE) null
            else runCatching { EventJson.decodeFromString(WaterChangeDetail.serializer(), e.detailJson) }.getOrNull()
    }
}

/** A task with the time it was last completed (null when never). */
data class TaskWithLast(val task: CareTask, val lastDoneAt: Long?)

class TaskRepository(
    private val dao: TaskDao,
    private val events: EventRepository,
    private val tanks: TankRepository,
    private val clock: Clock,
) {
    val tasks: Flow<List<TaskWithLast>> = combine(dao.tasks(TANK_ID), dao.lastCompletions()) { tasks, last ->
        val byTask = last.groupBy { it.taskId }.mapValues { (_, l) -> l.maxOf { it.completedAt } }
        tasks.map { TaskWithLast(it.toModel(), byTask[it.id]) }
    }

    fun task(id: String): Flow<CareTask?> = dao.task(id).map { it?.toModel() }

    suspend fun taskOnce(id: String): CareTask? = dao.taskOnce(id)?.toModel()

    fun completions(taskId: String): Flow<List<TaskCompletion>> = dao.completions(taskId).map { l -> l.map { it.toModel() } }

    suspend fun lastCompletedAt(taskId: String): Long? = dao.lastCompletedAt(taskId)

    suspend fun activeWithLast(): List<TaskWithLast> = dao.activeOnce().map { TaskWithLast(it.toModel(), dao.lastCompletedAt(it.id)) }

    suspend fun save(t: CareTask) = dao.upsert(t.toEntity())

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Marks a task done: inserts a completion and clears any snooze. A water-change task also logs a
     * WATER_CHANGE event so it appears on charts.
     */
    suspend fun complete(taskId: String, note: String? = null, amount: String? = null, at: Long = clock.now()): TaskCompletion? {
        val t = dao.taskOnce(taskId) ?: return null
        val c = TaskCompletion(Ids.new(), taskId, at, note?.ifBlank { null }, amount?.ifBlank { null })
        dao.insertCompletion(c.toEntity())
        if (t.snoozedUntil != null) dao.upsert(t.copy(snoozedUntil = null))
        if (t.title.contains("water change", ignoreCase = true)) {
            val system = tanks.get()?.systemGallons ?: 0.0
            val gallons = amount?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull() ?: if (system > 0) system * 0.1 else 6.0
            events.logWaterChange(gallons, if (system > 0) (gallons / system).coerceIn(0.0, 1.0) else 0.0, at = at)
        }
        return c
    }

    /** Removes a completion and the water-change event it logged, if any. */
    suspend fun undoCompletion(c: TaskCompletion) {
        dao.deleteCompletion(c.id)
        events.ofTypesSince(listOf(EventType.WATER_CHANGE), c.completedAt)
            .filter { it.at == c.completedAt }
            .forEach { events.delete(it.id) }
    }

    suspend fun snooze(taskId: String, untilMs: Long) {
        val t = dao.taskOnce(taskId) ?: return
        dao.upsert(t.copy(snoozedUntil = untilMs))
    }
}

class AlertRepository(private val dao: AlertDao, private val clock: Clock) {
    /** Unacknowledged alerts from the last [days] days. */
    fun active(days: Int = 3): Flow<List<Alert>> =
        dao.active(clock.now() - days * 86_400_000L).map { l -> l.map { it.toModel() } }

    fun recent(limit: Int = 50): Flow<List<Alert>> = dao.recent(limit).map { l -> l.map { it.toModel() } }

    /** Inserts drafts deduped by (param, kind, day). Returns only the alerts that are new. */
    suspend fun insert(drafts: List<AlertDraft>, dayKey: Int): List<Alert> {
        val now = clock.now()
        return drafts.mapNotNull { d ->
            val a = Alert(Ids.new(), TANK_ID, d.paramId, d.kind, d.message, now, null, dayKey)
            if (dao.insertIgnore(a.toEntity()) != -1L) a else null
        }
    }

    suspend fun acknowledge(id: String) = dao.acknowledge(id, clock.now())

    suspend fun acknowledgeParam(paramId: String) = dao.acknowledgeParam(paramId, clock.now())
}
