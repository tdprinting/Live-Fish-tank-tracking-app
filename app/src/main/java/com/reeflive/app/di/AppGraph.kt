package com.reeflive.app.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.reeflive.app.data.db.ReefDatabase
import com.reeflive.app.data.export.BackupManager
import com.reeflive.app.data.media.PhotoStore
import com.reeflive.app.data.repo.AlertRepository
import com.reeflive.app.data.repo.Clock
import com.reeflive.app.data.repo.EquipmentRepository
import com.reeflive.app.data.repo.EventRepository
import com.reeflive.app.data.repo.ExtrasRepository
import com.reeflive.app.data.repo.JournalRepository
import com.reeflive.app.data.repo.LightScheduleRepository
import com.reeflive.app.data.repo.LivestockRepository
import com.reeflive.app.data.repo.MeasurementRepository
import com.reeflive.app.data.repo.SettingsRepository
import com.reeflive.app.data.repo.TankRepository
import com.reeflive.app.data.repo.TargetsRepository
import com.reeflive.app.data.repo.TaskRepository
import com.reeflive.app.data.seed.SeedInstaller
import com.reeflive.app.live.SimulationHolder
import com.reeflive.app.notif.Notifier
import com.reeflive.app.update.UpdateManager
import com.reeflive.app.work.AlertEvaluator
import com.reeflive.app.work.ReminderScheduler
import com.reeflive.core.species.SpeciesDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.reefDataStore: DataStore<Preferences> by preferencesDataStore(name = "reeflive_settings")

/** Manual DI (ARCHITECTURE §5.1): lazy singletons for the whole app. */
class AppGraph(val app: Application) {
    val clock: Clock = Clock.SYSTEM

    /** Long-lived scope for fire-and-forget work (receivers, post-save alert evaluation). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: ReefDatabase by lazy { ReefDatabase.build(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app.reefDataStore) }
    val speciesDb: SpeciesDb by lazy { SpeciesDb.load(SpeciesDb::class.java.classLoader) }

    val tank: TankRepository by lazy { TankRepository(db.tankDao()) }
    val targets: TargetsRepository by lazy { TargetsRepository(db.overrideDao(), tank) }
    val measurements: MeasurementRepository by lazy { MeasurementRepository(db, db.measurementDao(), targets, clock) }
    val events: EventRepository by lazy { EventRepository(db.eventDao(), clock) }
    val tasks: TaskRepository by lazy { TaskRepository(db.taskDao(), events, tank, clock) }
    val alerts: AlertRepository by lazy { AlertRepository(db.alertDao(), clock) }
    val livestock: LivestockRepository by lazy { LivestockRepository(db.livestockDao(), events, speciesDb, clock) }
    val equipment: EquipmentRepository by lazy { EquipmentRepository(db.equipmentDao(), tasks, clock) }
    val journal: JournalRepository by lazy { JournalRepository(db, db.journalDao(), events) }
    val lights: LightScheduleRepository by lazy { LightScheduleRepository(db.lightDao(), events) }

    val simulationHolder: SimulationHolder by lazy { SimulationHolder(
        measurements, livestock, lights, assets = app.assets, clock = clock,
        customStore = com.reeflive.app.data.repo.CustomTankStore(java.io.File(app.filesDir, com.reeflive.app.data.repo.CustomTankStore.FILE_NAME)),
    ) }

    val seedInstaller: SeedInstaller by lazy { SeedInstaller(db, clock) }
    val extras: ExtrasRepository by lazy { ExtrasRepository(java.io.File(app.filesDir, ExtrasRepository.FILE_NAME), appScope, clock) }
    val backup: BackupManager by lazy { BackupManager(db, clock, extras) }
    val photos: PhotoStore by lazy { PhotoStore(app, clock) }

    val notifier: Notifier by lazy { Notifier(app) }
    val alertEvaluator: AlertEvaluator by lazy { AlertEvaluator(measurements, targets, alerts, settings, notifier, clock) }
    val reminders: ReminderScheduler by lazy { ReminderScheduler(app, tasks, settings, clock) }
    val updates: UpdateManager by lazy { UpdateManager(app, appScope) }
}
