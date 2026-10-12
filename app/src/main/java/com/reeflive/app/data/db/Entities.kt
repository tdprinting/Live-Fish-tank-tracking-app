package com.reeflive.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room schema v1 (ARCHITECTURE §6). Enums are stored as their names and JSON blobs as strings,
 * so no TypeConverters are needed. Timestamps are epoch millis UTC; values are in base units.
 */

@Entity(tableName = "tank")
data class TankEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
    val displayGallons: Double,
    val sumpGallons: Double,
    val reefType: String,
    val sandDepthCm: Double,
    val createdAt: Long,
)

@Entity(tableName = "test_session", indices = [Index(value = ["tankId", "takenAt"])])
data class TestSessionEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val takenAt: Long,
    val kitBrand: String?,
    val note: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "measurement",
    foreignKeys = [ForeignKey(entity = TestSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["tankId", "paramId", "takenAt"]),
        Index(value = ["paramId", "takenAt"], unique = true),
        Index(value = ["sessionId"]),
    ],
)
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val tankId: String,
    val paramId: String,
    val value: Double,
    val enteredUnit: String,
    val takenAt: Long,
    val createdAt: Long,
)

@Entity(tableName = "parameter_override")
data class ParameterOverrideEntity(
    @PrimaryKey val paramId: String,
    val idealLo: Double?,
    val idealHi: Double?,
    val accLo: Double?,
    val accHi: Double?,
    val dangerLo: Double?,
    val dangerHi: Double?,
    val maxChange: Double?,
    val cadenceDays: Int?,
    val hidden: Boolean,
    val favorite: Boolean?,
    val sortOrder: Int?,
    val displayUnit: String?,
)

@Entity(tableName = "alert", indices = [Index(value = ["paramId", "kind", "dayKey"], unique = true), Index(value = ["createdAt"])])
data class AlertEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val paramId: String,
    val kind: String,
    val message: String,
    val createdAt: Long,
    val acknowledgedAt: Long?,
    val dayKey: Int,
)

@Entity(tableName = "task", indices = [Index(value = ["tankId"])])
data class TaskEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val title: String,
    val intervalDays: Int?,
    val weekdaysMask: Int?,
    val reminderHour: Int,
    val reminderMinute: Int,
    val equipmentId: String?,
    val notes: String?,
    val active: Boolean,
    val createdAt: Long,
    val snoozedUntil: Long?,
)

@Entity(
    tableName = "task_completion",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["taskId", "completedAt"])],
)
data class TaskCompletionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val completedAt: Long,
    val note: String?,
    val amount: String?,
)

@Entity(tableName = "event", indices = [Index(value = ["tankId", "at"]), Index(value = ["type"])])
data class EventEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val type: String,
    val at: Long,
    val title: String,
    val detailJson: String,
    val journalId: String?,
)

@Entity(tableName = "livestock", indices = [Index(value = ["tankId", "status"])])
data class LivestockEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val speciesId: String,
    val nickname: String,
    val variant: String?,
    val role: String?,
    val lifeStage: String?,
    val sizeCm: Double?,
    val quantity: Int,
    val acquiredAt: Long?,
    val priceCents: Long?,
    val source: String?,
    val status: String,
    val statusChangedAt: Long?,
    val placement: String?,
    val verified: Boolean,
    val notes: String?,
    val photoPath: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "health_log",
    foreignKeys = [ForeignKey(entity = LivestockEntity::class, parentColumns = ["id"], childColumns = ["livestockId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["livestockId", "at"])],
)
data class HealthLogEntity(
    @PrimaryKey val id: String,
    val livestockId: String,
    val at: Long,
    val symptom: String,
    val treatment: String?,
    val dose: String?,
    val outcome: String?,
    val note: String?,
)

@Entity(tableName = "equipment", indices = [Index(value = ["tankId"])])
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val type: String,
    val name: String,
    val brand: String?,
    val model: String?,
    val installedAt: Long?,
    val serviceIntervalDays: Int?,
    val lastServicedAt: Long?,
    val notes: String?,
    val taskId: String?,
)

@Entity(tableName = "journal", indices = [Index(value = ["tankId", "at"])])
data class JournalEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val at: Long,
    val text: String,
    val tagsJson: String,
)

@Entity(tableName = "photo", indices = [Index(value = ["journalId"]), Index(value = ["livestockId"])])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val journalId: String?,
    val livestockId: String?,
    val path: String,
    val takenAt: Long,
    val width: Int,
    val height: Int,
)

@Entity(tableName = "light_schedule", indices = [Index(value = ["tankId"])])
data class LightScheduleEntity(
    @PrimaryKey val id: String,
    val tankId: String,
    val name: String,
    val pointsJson: String,
    val active: Boolean,
)
