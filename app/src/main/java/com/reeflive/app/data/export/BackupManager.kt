package com.reeflive.app.data.export

import androidx.room.withTransaction
import com.reeflive.app.data.db.ReefDatabase
import com.reeflive.app.data.db.toEntity
import com.reeflive.app.data.db.toModel
import com.reeflive.app.data.repo.Clock
import com.reeflive.app.data.repo.ExtrasRepository
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.io.Backup
import com.reeflive.core.io.BackupCodec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full JSON backup/restore of every table and the "Clear all data" wipe (DESIGN §6.7). */
class BackupManager(
    private val db: ReefDatabase,
    private val clock: Clock,
    private val extras: ExtrasRepository? = null,
) {

    suspend fun snapshot(): Backup = Backup(
        exportedAt = clock.now(),
        tanks = db.tankDao().all().map { it.toModel() },
        sessions = db.measurementDao().allSessions().map { it.toModel() },
        measurements = db.measurementDao().allMeasurements().map { it.toModel() },
        overrides = db.overrideDao().all().map { it.toModel() },
        alerts = db.alertDao().all().map { it.toModel() },
        tasks = db.taskDao().all().map { it.toModel() },
        completions = db.taskDao().allCompletions().map { it.toModel() },
        events = db.eventDao().all().map { it.toModel() },
        livestock = db.livestockDao().allOnce().map { it.toModel() },
        healthLogs = db.livestockDao().allHealth().map { it.toModel() },
        equipment = db.equipmentDao().allOnce().map { it.toModel() },
        journal = db.journalDao().allOnce().map { it.toModel() },
        photos = db.journalDao().allPhotos().map { it.toModel() },
        lightSchedules = db.lightDao().all().map { it.toModel() },
        extras = extras?.data?.value,
    )

    suspend fun exportJson(): String = BackupCodec.encode(snapshot())

    /** Replaces every table with the backup's content. Throws on an unreadable or newer backup. */
    suspend fun restoreJson(text: String): Backup {
        val b = BackupCodec.decode(text)
        db.withTransaction {
            wipeInTransaction()
            b.tanks.forEach { db.tankDao().upsert(it.toEntity()) }
            db.measurementDao().insertSessionsIgnore(b.sessions.map { it.toEntity() })
            db.measurementDao().insertMeasurementsIgnore(b.measurements.map { it.toEntity() })
            db.overrideDao().upsertAll(b.overrides.map { it.toEntity() })
            db.alertDao().insertAll(b.alerts.map { it.toEntity() })
            db.taskDao().upsertAll(b.tasks.map { it.toEntity() })
            db.taskDao().insertCompletions(b.completions.map { it.toEntity() })
            db.eventDao().insertAll(b.events.map { it.toEntity() })
            db.livestockDao().upsertAll(b.livestock.map { it.toEntity() })
            db.livestockDao().insertHealthAll(b.healthLogs.map { it.toEntity() })
            db.equipmentDao().upsertAll(b.equipment.map { it.toEntity() })
            db.journalDao().insertAll(b.journal.map { it.toEntity() })
            db.journalDao().insertPhotos(b.photos.map { it.toEntity() })
            db.lightDao().upsertAll(b.lightSchedules.map { it.toEntity() })
        }
        extras?.replaceAll(b.extras ?: ReefExtras())
        return b
    }

    /** Deletes every row in every table. */
    suspend fun wipeAll() {
        db.withTransaction { wipeInTransaction() }
        extras?.replaceAll(ReefExtras())
    }

    private suspend fun wipeInTransaction() {
        val w = db.wipeDao()
        w.measurements(); w.sessions(); w.overrides(); w.alerts(); w.completions(); w.tasks(); w.events()
        w.healthLogs(); w.livestock(); w.equipment(); w.photos(); w.journal(); w.lightSchedules(); w.tanks()
    }

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")

        fun csvName(nowMs: Long): String = "reeflive_measurements_${day(nowMs)}.csv"
        fun backupName(nowMs: Long): String = "reeflive_backup_${day(nowMs)}.json"
        private fun day(ms: Long) = DAY.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }
}
