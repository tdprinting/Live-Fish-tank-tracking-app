package com.reeflive.core.extras

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Serializable
enum class SupplyCategory(val label: String) {
    SALT("Salt"), FOOD("Food"), FILTER("Filter media"), RODI("RO/DI"), REAGENT("Test kit"),
    ADDITIVE("Additive"), MEDICATION("Medication"), OTHER("Other"),
}

/** A consumable on the shelf. [lowAt] triggers the low-stock warning; [expiresAt] matters for reagents and meds. */
@Serializable
data class Supply(
    val id: String,
    val name: String,
    val category: SupplyCategory = SupplyCategory.OTHER,
    val quantity: Double = 1.0,
    val unit: String = "",
    val lowAt: Double = 0.0,
    val expiresAt: Long? = null,
    /** What the last restock cost (for the spending total). */
    val cost: Double? = null,
    val boughtAt: Long? = null,
    val notes: String = "",
)

enum class SupplyState(val label: String) { OK("In stock"), LOW("Running low"), OUT("Out"), EXPIRING("Expires soon"), EXPIRED("Expired") }

@Serializable
data class Feeding(val id: String, val at: Long, val food: String, val amount: String = "", val note: String = "")

@Serializable
data class FeedingPlan(
    val feedsPerDay: Int = 2,
    val foods: List<String> = listOf("Mysis", "Pellets", "Nori", "Flake", "Brine shrimp", "Coral food"),
)

@Serializable
enum class TreatmentKind(val label: String, val defaultDays: Int) {
    OBSERVATION("Observation only", 28),
    COPPER("Copper", 28),
    PRAZIQUANTEL("Praziquantel", 7),
    HYPOSALINITY("Hyposalinity", 42),
    TANK_TRANSFER("Tank transfer", 12),
    ANTIBIOTIC("Antibiotic", 10),
    OTHER("Other", 14),
}

/** A quarantine / medication course (research 01 §3.8, §4.3): what, who, dose, how long, and every dose given. */
@Serializable
data class TreatmentCase(
    val id: String,
    val title: String,
    val kind: TreatmentKind = TreatmentKind.OBSERVATION,
    val livestockIds: List<String> = emptyList(),
    val dose: String = "",
    val startAt: Long,
    val days: Int = kind.defaultDays,
    /** 0 = no scheduled doses (observation); 1 = daily; 3 = every third day … */
    val doseEveryDays: Int = 1,
    val doses: List<Long> = emptyList(),
    val endedAt: Long? = null,
    val outcome: String = "",
    val notes: String = "",
)

/** Everything kept outside Room (versioned JSON file; see ExtrasStore in the app). */
@Serializable
data class ReefExtras(
    val version: Int = 1,
    val supplies: List<Supply> = emptyList(),
    val feedings: List<Feeding> = emptyList(),
    val feedingPlan: FeedingPlan = FeedingPlan(),
    val cases: List<TreatmentCase> = emptyList(),
) {
    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        fun decode(text: String): ReefExtras = JSON.decodeFromString(serializer(), text)
        fun encode(e: ReefExtras): String = JSON.encodeToString(serializer(), e)
    }
}

/** Pure rules for supplies, feeding and treatments. */
object ExtrasLogic {
    private const val DAY = 86_400_000L
    const val EXPIRY_WARN_DAYS = 30

    fun supplyState(s: Supply, nowMs: Long): SupplyState {
        val exp = s.expiresAt
        return when {
            exp != null && exp <= nowMs -> SupplyState.EXPIRED
            s.quantity <= 0.0 -> SupplyState.OUT
            s.quantity <= s.lowAt -> SupplyState.LOW
            exp != null && exp - nowMs <= EXPIRY_WARN_DAYS * DAY -> SupplyState.EXPIRING
            else -> SupplyState.OK
        }
    }

    fun needsAttention(supplies: List<Supply>, nowMs: Long): List<Supply> =
        supplies.filter { supplyState(it, nowMs) != SupplyState.OK }

    /** Restock spending since [fromMs]. */
    fun spendingSince(supplies: List<Supply>, fromMs: Long): Double =
        supplies.filter { (it.boughtAt ?: 0L) >= fromMs }.sumOf { it.cost ?: 0.0 }

    private fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun feedingsOn(feedings: List<Feeding>, date: LocalDate, zone: ZoneId): List<Feeding> =
        feedings.filter { day(it.at, zone) == date }.sortedBy { it.at }

    /** Consecutive days (ending today, or yesterday if nothing yet today) that met the plan. */
    fun feedingStreak(feedings: List<Feeding>, plan: FeedingPlan, nowMs: Long, zone: ZoneId): Int {
        val perDay = feedings.groupingBy { day(it.at, zone) }.eachCount()
        val need = plan.feedsPerDay.coerceAtLeast(1)
        var d = day(nowMs, zone)
        if ((perDay[d] ?: 0) < need) d = d.minusDays(1)
        var n = 0
        while ((perDay[d] ?: 0) >= need) { n++; d = d.minusDays(1) }
        return n
    }

    fun isActive(c: TreatmentCase): Boolean = c.endedAt == null

    /** 1-based day of the course (day 1 = start day). */
    fun dayOf(c: TreatmentCase, nowMs: Long, zone: ZoneId): Int =
        (ChronoUnit.DAYS.between(day(c.startAt, zone), day(nowMs, zone)) + 1).toInt().coerceAtLeast(1)

    fun daysLeft(c: TreatmentCase, nowMs: Long, zone: ZoneId): Int = (c.days - dayOf(c, nowMs, zone)).coerceAtLeast(0)

    fun dosedToday(c: TreatmentCase, nowMs: Long, zone: ZoneId): Boolean = c.doses.any { day(it, zone) == day(nowMs, zone) }

    /** A dose is due on schedule days (day 1, 1+n, 1+2n …) of an active course that has not been dosed today. */
    fun doseDue(c: TreatmentCase, nowMs: Long, zone: ZoneId): Boolean {
        if (!isActive(c) || c.doseEveryDays <= 0) return false
        val d = dayOf(c, nowMs, zone)
        if (d > c.days) return false
        return (d - 1) % c.doseEveryDays == 0 && !dosedToday(c, nowMs, zone)
    }

    fun progress(c: TreatmentCase, nowMs: Long, zone: ZoneId): Float =
        if (c.days <= 0) 1f else (dayOf(c, nowMs, zone).toFloat() / c.days).coerceIn(0f, 1f)
}
