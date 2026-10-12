package com.reeflive.core.status

import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.Range
import kotlin.math.abs
import kotlin.math.max

enum class Status { GOOD, CAUTION, DANGER, UNKNOWN }

sealed class Reason {
    data class OutOfIdeal(val value: Double, val range: Range, val low: Boolean) : Reason()
    data class OutOfAcceptable(val value: Double, val range: Range, val low: Boolean) : Reason()
    data class Danger(val value: Double, val threshold: Double, val low: Boolean) : Reason()
    /** [delta] is signed (latest − previous); [days] the elapsed time between them. */
    data class FastChange(val delta: Double, val days: Double, val cap: Double) : Reason()
    data class Stale(val days: Double, val cadence: Int) : Reason()
}

data class Evaluation(
    val status: Status,
    val reasons: List<Reason>,
    val trend: Trend,
    /** Age of the latest reading in days, null when never logged. */
    val staleDays: Double?,
    val latest: Reading? = null,
    val previous: Reading? = null,
    val slopePerDay: Double? = null,
) {
    val isStale: Boolean get() = reasons.any { it is Reason.Stale }
    val fastChange: Reason.FastChange? get() = reasons.filterIsInstance<Reason.FastChange>().firstOrNull()
    val delta: Double? get() = if (latest != null && previous != null) latest.value - previous.value else null

    companion object {
        val UNKNOWN = Evaluation(Status.UNKNOWN, emptyList(), Trend.UNKNOWN, null)
    }
}

/**
 * Status rules (ARCHITECTURE §4.3):
 * - latest inside ideal → GOOD; inside acceptable → CAUTION; past a danger threshold → DANGER;
 *   between acceptable and danger (or outside acceptable on a side with no danger threshold) → CAUTION.
 * - fast change |v_n − v_{n−1}| > cap × max(1, Δdays) → DANGER.
 * - stale (age > 1.5 × cadence) adds a Stale reason but never changes the status color.
 */
object StatusEngine {
    const val STALE_FACTOR = 1.5

    fun valueStatus(def: ParameterDef, v: Double): Status = when {
        def.dangerLow != null && v < def.dangerLow - Range.EPS -> Status.DANGER
        def.dangerHigh != null && v > def.dangerHigh + Range.EPS -> Status.DANGER
        v in def.ideal -> Status.GOOD
        else -> Status.CAUTION
    }

    fun evaluate(def: ParameterDef, history: List<Reading>, nowMs: Long): Evaluation {
        if (history.isEmpty()) return Evaluation.UNKNOWN
        val sorted = if (history.zipWithNext().all { (a, b) -> a.epochMs <= b.epochMs }) history else history.sortedBy { it.epochMs }
        val latest = sorted.last()
        val previous = if (sorted.size >= 2) sorted[sorted.size - 2] else null
        val v = latest.value
        val reasons = mutableListOf<Reason>()
        var status = Status.GOOD

        when {
            def.dangerLow != null && v < def.dangerLow - Range.EPS -> {
                status = Status.DANGER; reasons += Reason.Danger(v, def.dangerLow, low = true)
            }
            def.dangerHigh != null && v > def.dangerHigh + Range.EPS -> {
                status = Status.DANGER; reasons += Reason.Danger(v, def.dangerHigh, low = false)
            }
            v !in def.acceptable -> {
                status = Status.CAUTION; reasons += Reason.OutOfAcceptable(v, def.acceptable, low = v < def.acceptable.lo)
            }
            v !in def.ideal -> {
                status = Status.CAUTION; reasons += Reason.OutOfIdeal(v, def.ideal, low = v < def.ideal.lo)
            }
        }

        val cap = def.maxChangePerDay
        if (cap != null && previous != null) {
            val delta = v - previous.value
            val days = (latest.epochMs - previous.epochMs) / TrendCalc.DAY_MS.toDouble()
            if (abs(delta) > cap * max(1.0, days) + Range.EPS) {
                status = Status.DANGER
                reasons += Reason.FastChange(delta, days, cap)
            }
        }

        val ageDays = (nowMs - latest.epochMs).coerceAtLeast(0L) / TrendCalc.DAY_MS.toDouble()
        val cadence = def.cadenceDays
        if (cadence != null && ageDays > STALE_FACTOR * cadence) reasons += Reason.Stale(ageDays, cadence)

        val slope = TrendCalc.slopePerDay(sorted)
        return Evaluation(status, reasons, TrendCalc.classify(def, slope), ageDays, latest, previous, slope)
    }

    /** Status of each historical point by its own value (chart point colors). */
    fun pointStatuses(def: ParameterDef, history: List<Reading>): List<Status> = history.map { valueStatus(def, it.value) }

    /** Worst first: DANGER > CAUTION > GOOD > UNKNOWN. */
    fun severity(s: Status): Int = when (s) {
        Status.DANGER -> 3; Status.CAUTION -> 2; Status.GOOD -> 1; Status.UNKNOWN -> 0
    }

    /** Signed distance from the ideal mid-point in units of half the ideal span (for "worst z-score"). */
    fun zScore(def: ParameterDef, v: Double): Double {
        val half = if (def.ideal.span > 0) def.ideal.span / 2 else max(def.acceptable.span / 2, 1e-6)
        return abs(v - def.ideal.mid) / half
    }
}
