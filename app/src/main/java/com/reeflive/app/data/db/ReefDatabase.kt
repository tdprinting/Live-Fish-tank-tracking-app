package com.reeflive.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TankEntity::class, TestSessionEntity::class, MeasurementEntity::class, ParameterOverrideEntity::class,
        AlertEntity::class, TaskEntity::class, TaskCompletionEntity::class, EventEntity::class, LivestockEntity::class,
        HealthLogEntity::class, EquipmentEntity::class, JournalEntity::class, PhotoEntity::class, LightScheduleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ReefDatabase : RoomDatabase() {
    abstract fun tankDao(): TankDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun overrideDao(): OverrideDao
    abstract fun alertDao(): AlertDao
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao
    abstract fun livestockDao(): LivestockDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun journalDao(): JournalDao
    abstract fun lightDao(): LightDao
    abstract fun wipeDao(): WipeDao

    companion object {
        const val NAME = "reeflive.db"

        fun build(context: Context): ReefDatabase =
            Room.databaseBuilder(context.applicationContext, ReefDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/** Schema migrations. v1 is the first shipped schema; add `Migration(1, 2)` objects here. */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
