package com.reeflive.app.data.db

import com.reeflive.core.model.Alert
import com.reeflive.core.model.AlertKind
import com.reeflive.core.model.CareTask
import com.reeflive.core.model.Equipment
import com.reeflive.core.model.EquipmentType
import com.reeflive.core.model.EventType
import com.reeflive.core.model.HealthLog
import com.reeflive.core.model.JournalEntry
import com.reeflive.core.model.LightPoint
import com.reeflive.core.model.LightSchedule
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.LivestockStatus
import com.reeflive.core.model.Measurement
import com.reeflive.core.model.ParameterOverride
import com.reeflive.core.model.Photo
import com.reeflive.core.model.ReefEvent
import com.reeflive.core.model.ReefType
import com.reeflive.core.model.Tank
import com.reeflive.core.model.TaskCompletion
import com.reeflive.core.model.TestSession
import com.reeflive.core.units.ParamUnit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Entity ↔ core model mapping. Unknown enum names fall back to safe defaults. */
internal val DbJson = Json { ignoreUnknownKeys = true }

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

fun TankEntity.toModel() = Tank(id, name, lengthCm, widthCm, heightCm, displayGallons, sumpGallons, enumOr(reefType, ReefType.SOFT), sandDepthCm, createdAt)
fun Tank.toEntity() = TankEntity(id, name, lengthCm, widthCm, heightCm, displayGallons, sumpGallons, reefType.name, sandDepthCm, createdAt)

fun TestSessionEntity.toModel() = TestSession(id, tankId, takenAt, kitBrand, note, createdAt)
fun TestSession.toEntity() = TestSessionEntity(id, tankId, takenAt, kitBrand, note, createdAt)

fun MeasurementEntity.toModel() = Measurement(id, sessionId, tankId, paramId, value, enumOr(enteredUnit, ParamUnit.NONE), takenAt, createdAt)
fun Measurement.toEntity() = MeasurementEntity(id, sessionId, tankId, paramId, value, enteredUnit.name, takenAt, createdAt)

fun ParameterOverrideEntity.toModel() = ParameterOverride(
    paramId, idealLo, idealHi, accLo, accHi, dangerLo, dangerHi, maxChange, cadenceDays, hidden, favorite, sortOrder,
    displayUnit?.let { enumOr(it, ParamUnit.NONE) }?.takeIf { it != ParamUnit.NONE },
)
fun ParameterOverride.toEntity() = ParameterOverrideEntity(
    paramId, idealLo, idealHi, accLo, accHi, dangerLo, dangerHi, maxChange, cadenceDays, hidden, favorite, order, displayUnit?.name,
)

fun AlertEntity.toModel() = Alert(id, tankId, paramId, enumOr(kind, AlertKind.CAUTION), message, createdAt, acknowledgedAt, dayKey)
fun Alert.toEntity() = AlertEntity(id, tankId, paramId, kind.name, message, createdAt, acknowledgedAt, dayKey)

fun TaskEntity.toModel() = CareTask(id, tankId, title, intervalDays, weekdaysMask, reminderHour, reminderMinute, equipmentId, notes, active, createdAt, snoozedUntil)
fun CareTask.toEntity() = TaskEntity(id, tankId, title, intervalDays, weekdaysMask, reminderHour, reminderMinute, equipmentId, notes, active, createdAt, snoozedUntil)

fun TaskCompletionEntity.toModel() = TaskCompletion(id, taskId, completedAt, note, amount)
fun TaskCompletion.toEntity() = TaskCompletionEntity(id, taskId, completedAt, note, amount)

fun EventEntity.toModel() = ReefEvent(id, tankId, enumOr(type, EventType.NOTE), at, title, detailJson, journalId)
fun ReefEvent.toEntity() = EventEntity(id, tankId, type.name, at, title, detailJson, journalId)

fun LivestockEntity.toModel() = Livestock(
    id, tankId, speciesId, nickname, variant, role, lifeStage, sizeCm, quantity, acquiredAt, priceCents, source,
    enumOr(status, LivestockStatus.ALIVE), statusChangedAt, placement, verified, notes, photoPath, createdAt,
)
fun Livestock.toEntity() = LivestockEntity(
    id, tankId, speciesId, nickname, variant, role, lifeStage, sizeCm, quantity, acquiredAt, priceCents, source,
    status.name, statusChangedAt, placement, verified, notes, photoPath, createdAt,
)

fun HealthLogEntity.toModel() = HealthLog(id, livestockId, at, symptom, treatment, dose, outcome, note)
fun HealthLog.toEntity() = HealthLogEntity(id, livestockId, at, symptom, treatment, dose, outcome, note)

fun EquipmentEntity.toModel() = Equipment(id, tankId, enumOr(type, EquipmentType.OTHER), name, brand, model, installedAt, serviceIntervalDays, lastServicedAt, notes, taskId)
fun Equipment.toEntity() = EquipmentEntity(id, tankId, type.name, name, brand, model, installedAt, serviceIntervalDays, lastServicedAt, notes, taskId)

private val TagsSer = ListSerializer(String.serializer())
fun JournalEntity.toModel() = JournalEntry(id, tankId, at, text, runCatching { DbJson.decodeFromString(TagsSer, tagsJson) }.getOrDefault(emptyList()))
fun JournalEntry.toEntity() = JournalEntity(id, tankId, at, text, DbJson.encodeToString(TagsSer, tags))

fun PhotoEntity.toModel() = Photo(id, journalId, livestockId, path, takenAt, width, height)
fun Photo.toEntity() = PhotoEntity(id, journalId, livestockId, path, takenAt, width, height)

private val PointsSer = ListSerializer(LightPoint.serializer())
fun LightScheduleEntity.toModel() = LightSchedule(id, tankId, name, runCatching { DbJson.decodeFromString(PointsSer, pointsJson) }.getOrDefault(emptyList()), active)
fun LightSchedule.toEntity() = LightScheduleEntity(id, tankId, name, DbJson.encodeToString(PointsSer, points), active)
