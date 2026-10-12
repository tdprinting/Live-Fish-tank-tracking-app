package com.reeflive.core.model

import com.reeflive.core.units.ParamUnit
import kotlinx.serialization.Serializable
import java.util.UUID

/*
 * Pure domain data shared by the app (Room maps to/from these) and the simulation.
 * All timestamps are epoch millis UTC. Measurement values are in the parameter's base unit.
 */

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

@Serializable
enum class ReefType(val label: String) { FOWLR("FOWLR"), SOFT("Soft coral"), LPS("LPS"), SPS("SPS") }

@Serializable
data class Tank(
    val id: String,
    val name: String,
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
    val displayGallons: Double,
    val sumpGallons: Double = 0.0,
    val reefType: ReefType = ReefType.SOFT,
    val sandDepthCm: Double = 3.0,
    val createdAt: Long = 0L,
) {
    val systemGallons: Double get() = displayGallons + sumpGallons

    companion object {
        const val CM3_PER_GALLON = 3785.411784

        /** Gross glass volume in US gallons from inside dimensions. */
        fun grossGallons(lengthCm: Double, widthCm: Double, heightCm: Double): Double =
            lengthCm * widthCm * heightCm / CM3_PER_GALLON
    }
}

@Serializable
data class TestSession(
    val id: String,
    val tankId: String,
    val takenAt: Long,
    val kitBrand: String? = null,
    val note: String? = null,
    val createdAt: Long = takenAt,
)

@Serializable
data class Measurement(
    val id: String,
    val sessionId: String,
    val tankId: String,
    val paramId: String,
    /** Base unit of the parameter. */
    val value: Double,
    val enteredUnit: ParamUnit,
    val takenAt: Long,
    val createdAt: Long = takenAt,
)

@Serializable
data class ParameterOverride(
    val paramId: String,
    val idealLo: Double? = null,
    val idealHi: Double? = null,
    val accLo: Double? = null,
    val accHi: Double? = null,
    val dangerLo: Double? = null,
    val dangerHi: Double? = null,
    val maxChange: Double? = null,
    val cadenceDays: Int? = null,
    val hidden: Boolean = false,
    val favorite: Boolean? = null,
    val order: Int? = null,
    val displayUnit: ParamUnit? = null,
)

@Serializable
enum class AlertKind { DANGER, FAST_CHANGE, CAUTION, STALE }

@Serializable
data class Alert(
    val id: String,
    val tankId: String,
    val paramId: String,
    val kind: AlertKind,
    val message: String,
    val createdAt: Long,
    val acknowledgedAt: Long? = null,
    /** yyyyMMdd in the device zone; unique with (paramId, kind). */
    val dayKey: Int,
)

@Serializable
data class CareTask(
    val id: String,
    val tankId: String,
    val title: String,
    val intervalDays: Int? = null,
    /** Bit 0 = Monday … bit 6 = Sunday. Used when [intervalDays] is null. */
    val weekdaysMask: Int? = null,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val equipmentId: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val snoozedUntil: Long? = null,
)

@Serializable
data class TaskCompletion(
    val id: String,
    val taskId: String,
    val completedAt: Long,
    val note: String? = null,
    val amount: String? = null,
)

@Serializable
enum class EventType(val label: String) {
    FEED("Feed"), WATER_CHANGE("Water change"), DOSE("Dose"), NOTE("Note"), LIGHT_CHANGE("Light change"),
    ADD_LIVESTOCK("Added livestock"), REMOVE_LIVESTOCK("Removed livestock"), TREATMENT("Treatment"),
}

@Serializable
data class ReefEvent(
    val id: String,
    val tankId: String,
    val type: EventType,
    val at: Long,
    val title: String,
    /** Type-specific JSON: see [FeedDetail], [WaterChangeDetail], [DoseDetail]. */
    val detailJson: String = "{}",
    val journalId: String? = null,
)

@Serializable
data class FeedDetail(val food: String, val amount: String? = null)

@Serializable
data class WaterChangeDetail(val gallons: Double, val fraction: Double, val mix: Map<String, Double> = emptyMap())

@Serializable
data class DoseDetail(
    val paramId: String,
    val product: String,
    val grams: Double? = null,
    val ml: Double? = null,
    val expectedDelta: Double,
)

@Serializable
enum class LivestockStatus(val label: String) { ALIVE("In tank"), REHOMED("Rehomed"), DECEASED("Deceased") }

@Serializable
data class Livestock(
    val id: String,
    val tankId: String,
    val speciesId: String,
    val nickname: String,
    val variant: String? = null,
    /** FEMALE / MALE when known. */
    val role: String? = null,
    /** JUVENILE / ADULT when known. */
    val lifeStage: String? = null,
    val sizeCm: Double? = null,
    /** Group entries (snails, hermits) carry a count. */
    val quantity: Int = 1,
    val acquiredAt: Long? = null,
    val priceCents: Long? = null,
    val source: String? = null,
    val status: LivestockStatus = LivestockStatus.ALIVE,
    val statusChangedAt: Long? = null,
    /** Anchor name in the rockscape (ASSETS §3.4). */
    val placement: String? = null,
    val verified: Boolean = true,
    val notes: String? = null,
    val photoPath: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class HealthLog(
    val id: String,
    val livestockId: String,
    val at: Long,
    val symptom: String,
    val treatment: String? = null,
    val dose: String? = null,
    val outcome: String? = null,
    val note: String? = null,
)

@Serializable
enum class EquipmentType(val label: String) {
    LIGHT("Light"), POWERHEAD("Powerhead"), HEATER("Heater"), FILTER("Filter"), SKIMMER("Skimmer"),
    ATO("ATO"), CONTROLLER("Controller"), OTHER("Other"),
}

@Serializable
data class Equipment(
    val id: String,
    val tankId: String,
    val type: EquipmentType,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val installedAt: Long? = null,
    val serviceIntervalDays: Int? = null,
    val lastServicedAt: Long? = null,
    val notes: String? = null,
    val taskId: String? = null,
)

@Serializable
data class JournalEntry(
    val id: String,
    val tankId: String,
    val at: Long,
    val text: String,
    /** Livestock ids and event-type names. */
    val tags: List<String> = emptyList(),
)

@Serializable
data class Photo(
    val id: String,
    val journalId: String? = null,
    val livestockId: String? = null,
    /** Path relative to the app files dir. */
    val path: String,
    val takenAt: Long,
    val width: Int,
    val height: Int,
)
