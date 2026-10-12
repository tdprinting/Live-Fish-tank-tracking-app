package com.reeflive.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class SessionWithMeasurements(
    @Embedded val session: TestSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val measurements: List<MeasurementEntity>,
)

/** A measurement joined with its session's kit brand (chart tooltips show the kit). */
data class MeasurementWithKit(
    @Embedded val m: MeasurementEntity,
    val kitBrand: String?,
)

@Dao
interface TankDao {
    @Query("SELECT * FROM tank ORDER BY createdAt LIMIT 1")
    fun observe(): Flow<TankEntity?>

    @Query("SELECT * FROM tank ORDER BY createdAt LIMIT 1")
    suspend fun get(): TankEntity?

    @Upsert
    suspend fun upsert(tank: TankEntity)

    @Query("SELECT * FROM tank")
    suspend fun all(): List<TankEntity>
}

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TestSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurements(items: List<MeasurementEntity>)

    /** Import path: rows that clash on (paramId, takenAt) are skipped. Returns -1 for each skipped row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeasurementsIgnore(items: List<MeasurementEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionsIgnore(items: List<TestSessionEntity>): List<Long>

    @Query("SELECT * FROM measurement WHERE tankId = :tankId AND paramId = :paramId AND takenAt >= :fromMs ORDER BY takenAt")
    fun history(tankId: String, paramId: String, fromMs: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE tankId = :tankId AND paramId = :paramId AND takenAt >= :fromMs ORDER BY takenAt")
    suspend fun historyOnce(tankId: String, paramId: String, fromMs: Long): List<MeasurementEntity>

    @Query(
        "SELECT measurement.*, test_session.kitBrand AS kitBrand FROM measurement " +
            "LEFT JOIN test_session ON measurement.sessionId = test_session.id " +
            "WHERE measurement.tankId = :tankId AND measurement.takenAt >= :fromMs ORDER BY measurement.takenAt",
    )
    fun sinceWithKit(tankId: String, fromMs: Long): Flow<List<MeasurementWithKit>>

    @Query(
        "SELECT measurement.*, test_session.kitBrand AS kitBrand FROM measurement " +
            "LEFT JOIN test_session ON measurement.sessionId = test_session.id " +
            "WHERE measurement.tankId = :tankId AND measurement.paramId = :paramId AND measurement.takenAt >= :fromMs " +
            "ORDER BY measurement.takenAt",
    )
    fun historyWithKit(tankId: String, paramId: String, fromMs: Long): Flow<List<MeasurementWithKit>>

    @Query("SELECT * FROM measurement WHERE tankId = :tankId AND takenAt >= :fromMs ORDER BY takenAt")
    fun since(tankId: String, fromMs: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE tankId = :tankId AND takenAt >= :fromMs ORDER BY takenAt")
    suspend fun sinceOnce(tankId: String, fromMs: Long): List<MeasurementEntity>

    @Query(
        "SELECT m.* FROM measurement m INNER JOIN " +
            "(SELECT paramId AS pid, MAX(takenAt) AS mt FROM measurement WHERE tankId = :tankId GROUP BY paramId) x " +
            "ON m.paramId = x.pid AND m.takenAt = x.mt WHERE m.tankId = :tankId",
    )
    fun latestPerParam(tankId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE tankId = :tankId AND takenAt BETWEEN :fromMs AND :toMs ORDER BY takenAt")
    suspend fun inWindow(tankId: String, fromMs: Long, toMs: Long): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurement WHERE tankId = :tankId AND paramId = :paramId")
    fun count(tankId: String, paramId: String): Flow<Int>

    @Transaction
    @Query("SELECT * FROM test_session WHERE tankId = :tankId ORDER BY takenAt DESC LIMIT :limit")
    fun sessions(tankId: String, limit: Int): Flow<List<SessionWithMeasurements>>

    @Transaction
    @Query("SELECT * FROM test_session WHERE id = :id")
    fun session(id: String): Flow<SessionWithMeasurements?>

    @Transaction
    @Query("SELECT * FROM test_session WHERE id = :id")
    suspend fun sessionOnce(id: String): SessionWithMeasurements?

    @Query("SELECT kitBrand FROM test_session WHERE id = :sessionId")
    suspend fun kitFor(sessionId: String): String?

    @Query("DELETE FROM test_session WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM measurement WHERE id = :id")
    suspend fun deleteMeasurement(id: String)

    @Query("SELECT * FROM test_session")
    suspend fun allSessions(): List<TestSessionEntity>

    @Query("SELECT * FROM measurement ORDER BY takenAt")
    suspend fun allMeasurements(): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurement")
    suspend fun totalCount(): Int

    @Query("SELECT DISTINCT kitBrand FROM test_session WHERE kitBrand IS NOT NULL AND kitBrand != '' ORDER BY createdAt DESC LIMIT 12")
    fun recentKits(): Flow<List<String>>
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM parameter_override")
    fun observe(): Flow<List<ParameterOverrideEntity>>

    @Query("SELECT * FROM parameter_override")
    suspend fun all(): List<ParameterOverrideEntity>

    @Upsert
    suspend fun upsert(o: ParameterOverrideEntity)

    @Upsert
    suspend fun upsertAll(o: List<ParameterOverrideEntity>)

    @Query("DELETE FROM parameter_override WHERE paramId = :paramId")
    suspend fun delete(paramId: String)
}

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(a: AlertEntity): Long

    @Query("SELECT * FROM alert WHERE acknowledgedAt IS NULL AND createdAt >= :sinceMs ORDER BY createdAt DESC")
    fun active(sinceMs: Long): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alert ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<AlertEntity>>

    @Query("UPDATE alert SET acknowledgedAt = :at WHERE id = :id")
    suspend fun acknowledge(id: String, at: Long)

    @Query("UPDATE alert SET acknowledgedAt = :at WHERE paramId = :paramId AND acknowledgedAt IS NULL")
    suspend fun acknowledgeParam(paramId: String, at: Long)

    @Query("SELECT * FROM alert")
    suspend fun all(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AlertEntity>)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task WHERE tankId = :tankId ORDER BY title")
    fun tasks(tankId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE active = 1")
    suspend fun activeOnce(): List<TaskEntity>

    @Query("SELECT * FROM task WHERE id = :id")
    fun task(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun taskOnce(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(t: TaskEntity)

    @Upsert
    suspend fun upsertAll(t: List<TaskEntity>)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(c: TaskCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletions(c: List<TaskCompletionEntity>)

    @Query("DELETE FROM task_completion WHERE id = :id")
    suspend fun deleteCompletion(id: String)

    @Query("SELECT * FROM task_completion WHERE taskId = :taskId ORDER BY completedAt DESC")
    fun completions(taskId: String): Flow<List<TaskCompletionEntity>>

    /** Latest completion per task. */
    @Query("SELECT * FROM task_completion c WHERE completedAt = (SELECT MAX(completedAt) FROM task_completion WHERE taskId = c.taskId)")
    fun lastCompletions(): Flow<List<TaskCompletionEntity>>

    @Query("SELECT MAX(completedAt) FROM task_completion WHERE taskId = :taskId")
    suspend fun lastCompletedAt(taskId: String): Long?

    @Query("SELECT * FROM task")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM task_completion")
    suspend fun allCompletions(): List<TaskCompletionEntity>
}

@Dao
interface EventDao {
    @Upsert
    suspend fun upsert(e: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(e: List<EventEntity>)

    @Query("SELECT * FROM event WHERE tankId = :tankId ORDER BY at DESC LIMIT :limit")
    fun recent(tankId: String, limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE tankId = :tankId AND at >= :fromMs ORDER BY at")
    fun since(tankId: String, fromMs: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE tankId = :tankId AND type IN (:types) AND at >= :fromMs ORDER BY at")
    suspend fun ofTypesSince(tankId: String, types: List<String>, fromMs: Long): List<EventEntity>

    @Query("DELETE FROM event WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM event")
    suspend fun all(): List<EventEntity>
}

@Dao
interface LivestockDao {
    @Query("SELECT * FROM livestock WHERE tankId = :tankId ORDER BY createdAt, nickname")
    fun all(tankId: String): Flow<List<LivestockEntity>>

    @Query("SELECT * FROM livestock WHERE tankId = :tankId AND status = 'ALIVE' ORDER BY createdAt, nickname")
    fun alive(tankId: String): Flow<List<LivestockEntity>>

    @Query("SELECT * FROM livestock WHERE id = :id")
    fun byId(id: String): Flow<LivestockEntity?>

    @Upsert
    suspend fun upsert(l: LivestockEntity)

    @Upsert
    suspend fun upsertAll(l: List<LivestockEntity>)

    @Query("DELETE FROM livestock WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM health_log WHERE livestockId = :id ORDER BY at DESC")
    fun healthLogs(id: String): Flow<List<HealthLogEntity>>

    @Upsert
    suspend fun upsertHealth(h: HealthLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthAll(h: List<HealthLogEntity>)

    @Query("DELETE FROM health_log WHERE id = :id")
    suspend fun deleteHealth(id: String)

    @Query("SELECT * FROM livestock")
    suspend fun allOnce(): List<LivestockEntity>

    @Query("SELECT * FROM health_log")
    suspend fun allHealth(): List<HealthLogEntity>
}

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment WHERE tankId = :tankId ORDER BY type, name")
    fun all(tankId: String): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id")
    fun byId(id: String): Flow<EquipmentEntity?>

    @Upsert
    suspend fun upsert(e: EquipmentEntity)

    @Upsert
    suspend fun upsertAll(e: List<EquipmentEntity>)

    @Query("DELETE FROM equipment WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM equipment")
    suspend fun allOnce(): List<EquipmentEntity>
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal WHERE tankId = :tankId ORDER BY at DESC")
    fun all(tankId: String): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal WHERE id = :id")
    fun byId(id: String): Flow<JournalEntity?>

    @Upsert
    suspend fun upsert(j: JournalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(j: List<JournalEntity>)

    @Query("DELETE FROM journal WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM photo ORDER BY takenAt DESC")
    fun photos(): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(p: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(p: List<PhotoEntity>)

    @Query("SELECT * FROM photo WHERE journalId = :journalId")
    suspend fun photosFor(journalId: String): List<PhotoEntity>

    @Delete
    suspend fun deletePhoto(p: PhotoEntity)

    @Query("SELECT * FROM journal")
    suspend fun allOnce(): List<JournalEntity>

    @Query("SELECT * FROM photo")
    suspend fun allPhotos(): List<PhotoEntity>
}

@Dao
interface LightDao {
    @Query("SELECT * FROM light_schedule WHERE tankId = :tankId AND active = 1 LIMIT 1")
    fun active(tankId: String): Flow<LightScheduleEntity?>

    @Upsert
    suspend fun upsert(l: LightScheduleEntity)

    @Upsert
    suspend fun upsertAll(l: List<LightScheduleEntity>)

    @Query("SELECT * FROM light_schedule")
    suspend fun all(): List<LightScheduleEntity>
}

/** Bulk deletes for "Reset to seed", "Clear all data" and backup restore. Call inside `withTransaction`. */
@Dao
interface WipeDao {
    @Query("DELETE FROM measurement")
    suspend fun measurements()

    @Query("DELETE FROM test_session")
    suspend fun sessions()

    @Query("DELETE FROM parameter_override")
    suspend fun overrides()

    @Query("DELETE FROM alert")
    suspend fun alerts()

    @Query("DELETE FROM task_completion")
    suspend fun completions()

    @Query("DELETE FROM task")
    suspend fun tasks()

    @Query("DELETE FROM event")
    suspend fun events()

    @Query("DELETE FROM health_log")
    suspend fun healthLogs()

    @Query("DELETE FROM livestock")
    suspend fun livestock()

    @Query("DELETE FROM equipment")
    suspend fun equipment()

    @Query("DELETE FROM photo")
    suspend fun photos()

    @Query("DELETE FROM journal")
    suspend fun journal()

    @Query("DELETE FROM light_schedule")
    suspend fun lightSchedules()

    @Query("DELETE FROM tank")
    suspend fun tanks()
}
