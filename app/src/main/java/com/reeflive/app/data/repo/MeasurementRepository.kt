package com.reeflive.app.data.repo

import androidx.room.withTransaction
import com.reeflive.app.data.db.MeasurementDao
import com.reeflive.app.data.db.MeasurementWithKit
import com.reeflive.app.data.db.ReefDatabase
import com.reeflive.app.data.db.SessionWithMeasurements
import com.reeflive.app.data.db.toEntity
import com.reeflive.app.data.db.toModel
import com.reeflive.core.io.CsvCodec
import com.reeflive.core.io.CsvRow
import com.reeflive.core.model.Ids
import com.reeflive.core.model.Measurement
import com.reeflive.core.model.TestSession
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.status.Evaluation
import com.reeflive.core.status.Reading
import com.reeflive.core.status.StatusEngine
import com.reeflive.core.units.ParamUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** One parameter with its effective definition, full history and current evaluation. */
data class ParamEval(
    val def: ParameterDef,
    val eval: Evaluation,
    val history: List<Reading>,
) {
    val id: String get() = def.id
    val latest: Reading? get() = eval.latest
}

/** A test session and its values (History list, TestSessionScreen). */
data class SessionView(val session: TestSession, val measurements: List<Measurement>)

/** One value to save, already converted to the parameter's base unit. */
data class LogEntry(val paramId: String, val baseValue: Double, val enteredUnit: ParamUnit)

data class ImportSummary(val inserted: Int, val skipped: Int, val sessions: Int)

class MeasurementRepository(
    private val db: ReefDatabase,
    private val dao: MeasurementDao,
    private val targets: TargetsRepository,
    private val clock: Clock,
) {
    private fun MeasurementWithKit.toReading() = Reading(m.paramId, m.value, m.takenAt, kitBrand)

    /** Latest value per parameter in base units. Used by the Live Tank to initialise reality mode. */
    fun latestValues(): Flow<Map<String, Double>> =
        dao.latestPerParam(TANK_ID).map { list -> list.associate { it.paramId to it.value } }.distinctUntilChanged()

    /** Latest measurement per parameter. */
    fun latest(): Flow<Map<String, Measurement>> =
        dao.latestPerParam(TANK_ID).map { list -> list.associate { it.paramId to it.toModel() } }

    /** Every reading since [fromMs], grouped by parameter and sorted by time. */
    fun readings(fromMs: Long = 0L): Flow<Map<String, List<Reading>>> =
        dao.sinceWithKit(TANK_ID, fromMs).map { list -> list.map { it.toReading() }.groupBy { it.paramId } }

    /** History for one parameter (with kit brand for tooltips). */
    fun history(paramId: String, fromMs: Long = 0L): Flow<List<Reading>> =
        dao.historyWithKit(TANK_ID, paramId, fromMs).map { list -> list.map { it.toReading() } }

    /** Evaluations for every visible parameter (logged or not), in display order. */
    fun latestEvaluations(): Flow<List<ParamEval>> =
        combine(targets.effective, readings()) { defs, byParam ->
            val now = clock.now()
            defs.map { def ->
                val h = byParam[def.id].orEmpty()
                ParamEval(def, StatusEngine.evaluate(def, h, now), h)
            }
        }.flowOn(Dispatchers.Default)

    suspend fun historyOnce(paramId: String, fromMs: Long): List<Reading> =
        dao.historyOnce(TANK_ID, paramId, fromMs).map { Reading(it.paramId, it.value, it.takenAt) }

    /** Saves one session with N measurements atomically. Returns the session id. */
    suspend fun saveSession(takenAt: Long, kitBrand: String?, note: String?, entries: List<LogEntry>): String {
        require(entries.isNotEmpty()) { "Nothing to save" }
        val now = clock.now()
        val session = TestSession(Ids.new(), TANK_ID, takenAt, kitBrand?.trim()?.ifBlank { null }, note?.trim()?.ifBlank { null }, now)
        val ms = entries.distinctBy { it.paramId }.map { e ->
            Measurement(Ids.new(), session.id, TANK_ID, e.paramId, e.baseValue, e.enteredUnit, takenAt, now)
        }
        db.withTransaction {
            dao.insertSession(session.toEntity())
            dao.insertMeasurements(ms.map { it.toEntity() })
        }
        return session.id
    }

    /** Replaces a session's values (TestSessionScreen edit). */
    suspend fun updateSession(session: TestSession, entries: List<LogEntry>) {
        val now = clock.now()
        db.withTransaction {
            dao.deleteSession(session.id)
            dao.insertSession(session.toEntity())
            dao.insertMeasurements(
                entries.distinctBy { it.paramId }.map { e ->
                    Measurement(Ids.new(), session.id, TANK_ID, e.paramId, e.baseValue, e.enteredUnit, session.takenAt, now).toEntity()
                },
            )
        }
    }

    fun sessions(limit: Int = 200): Flow<List<SessionView>> = dao.sessions(TANK_ID, limit).map { list -> list.map { it.toView() } }

    fun session(id: String): Flow<SessionView?> = dao.session(id).map { it?.toView() }

    private fun SessionWithMeasurements.toView() =
        SessionView(session.toModel(), measurements.map { it.toModel() }.sortedBy { it.paramId })

    /** Deletes a session (cascade removes its values) and returns a snapshot for undo. */
    suspend fun deleteSession(id: String): SessionView? {
        val snap = dao.sessionOnce(id)?.toView() ?: return null
        dao.deleteSession(id)
        return snap
    }

    suspend fun restore(snapshot: SessionView) {
        db.withTransaction {
            dao.insertSession(snapshot.session.toEntity())
            dao.insertMeasurements(snapshot.measurements.map { it.toEntity() })
        }
    }

    suspend fun deleteMeasurement(m: Measurement) = dao.deleteMeasurement(m.id)

    fun recentKits(): Flow<List<String>> = dao.recentKits()

    fun count(paramId: String): Flow<Int> = dao.count(TANK_ID, paramId)

    suspend fun totalCount(): Int = dao.totalCount()

    /** CSV of every measurement (base units, full precision). */
    suspend fun exportCsv(): String {
        val sessions = dao.allSessions().map { it.toModel() }
        val ms = dao.allMeasurements().map { it.toModel() }
        return CsvCodec.export(sessions, ms)
    }

    /**
     * Inserts imported rows. Rows sharing a session id (or, without one, the same timestamp and kit) become
     * one session; rows that clash with an existing (parameter, timestamp) are skipped.
     */
    suspend fun importRows(rows: List<CsvRow>): ImportSummary {
        if (rows.isEmpty()) return ImportSummary(0, 0, 0)
        val now = clock.now()
        val groups = rows.groupBy { it.sessionId ?: "t${it.takenAt}|${it.kit.orEmpty()}" }
        var inserted = 0
        db.withTransaction {
            val sessions = groups.map { (key, list) ->
                val first = list.first()
                val id = first.sessionId ?: ("import-" + key.hashCode().toUInt().toString(16) + "-" + first.takenAt)
                TestSession(id, TANK_ID, list.minOf { it.takenAt }, first.kit, list.firstNotNullOfOrNull { it.note }, now) to list
            }
            dao.insertSessionsIgnore(sessions.map { it.first.toEntity() })
            val ms = sessions.flatMap { (s, list) ->
                list.map { r -> Measurement(Ids.new(), s.id, TANK_ID, r.paramId, r.value, r.unit, r.takenAt, now).toEntity() }
            }
            inserted = dao.insertMeasurementsIgnore(ms).count { it != -1L }
        }
        return ImportSummary(inserted, rows.size - inserted, groups.size)
    }
}
