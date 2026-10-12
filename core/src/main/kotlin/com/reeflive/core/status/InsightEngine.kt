package com.reeflive.core.status

import com.reeflive.core.chem.Dosing
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.util.Fmt
import kotlin.math.abs

/**
 * One plain sentence per parameter from its history (ARCHITECTURE §7): consumption (alk/ca/mg),
 * drift (salinity), stale, or a stability note. Pure functions.
 */
object InsightEngine {
    const val WINDOW_DAYS = 14

    /**
     * Consumption per day (positive = being used up) from readings in the last [WINDOW_DAYS],
     * restricted to the span after the most recent dose / water change in [resetTimes].
     */
    fun consumptionPerDay(history: List<Reading>, nowMs: Long, resetTimes: List<Long> = emptyList()): Double? {
        val from = nowMs - WINDOW_DAYS * TrendCalc.DAY_MS
        val lastReset = resetTimes.filter { it <= nowMs }.maxOrNull() ?: Long.MIN_VALUE
        val pts = history.filter { it.epochMs >= from && it.epochMs >= lastReset }.sortedBy { it.epochMs }
        if (pts.size < 2) return null
        if ((pts.last().epochMs - pts.first().epochMs) < TrendCalc.DAY_MS / 2) return null
        val slope = TrendCalc.leastSquares(pts) ?: return null
        return -slope
    }

    fun insight(
        def: ParameterDef,
        history: List<Reading>,
        nowMs: Long,
        systemGallons: Double,
        resetTimes: List<Long> = emptyList(),
    ): String {
        val eval = StatusEngine.evaluate(def, history, nowMs)
        if (eval.status == Status.UNKNOWN) return "Nothing logged yet — tap + to add your first ${def.shortName} test."
        eval.reasons.filterIsInstance<Reason.Stale>().firstOrNull()?.let {
            return "Not tested in ${it.days.toInt()} days — ${def.shortName} is usually checked every ${it.cadence} d."
        }
        val u = AlertRules.unit(def)
        when (def.id) {
            "alk", "ca", "mg" -> {
                val c = consumptionPerDay(history, nowMs, resetTimes)
                if (c != null && c > TrendCalc.flatThreshold(def) * 0.5) {
                    val product = when (def.id) { "alk" -> "nahco3"; "ca" -> "cacl2"; else -> "mgcl2_6h2o" }
                    val p = Dosing.product(product)!!
                    val hold = eval.latest!!.value.coerceIn(def.ideal.lo, def.ideal.hi)
                    val g = Dosing.grams(p, c, systemGallons)
                    val name = p.name.substringBefore(" (").substringBefore(",").lowercase()
                    return "${def.shortName} consumption ${Fmt.num(c, def.precision + 1)}$u/day — dose ${Fmt.num(g, 1)} g $name daily to hold ${Fmt.trim(hold, def.precision)}."
                }
            }
            "sal" -> {
                val s = eval.slopePerDay
                if (s != null && abs(s) >= 0.0003) {
                    return if (s > 0) "Salinity creeping up ${Fmt.num(s, 4)}/day — check your top-off."
                    else "Salinity dropping ${Fmt.num(abs(s), 4)}/day — check for overfilled top-off."
                }
            }
        }
        eval.fastChange?.let { return AlertRules.message(def, it) + " — change more slowly." }
        return when (eval.status) {
            Status.GOOD -> when (eval.trend) {
                Trend.FLAT, Trend.UNKNOWN -> "${def.shortName} is steady and in range."
                Trend.UP -> "${def.shortName} is in range and rising."
                Trend.DOWN -> "${def.shortName} is in range and falling."
            }
            Status.CAUTION -> AlertRules.shortVerdict(def, eval) + "."
            Status.DANGER -> AlertRules.shortVerdict(def, eval) + " — act now."
            Status.UNKNOWN -> ""
        }
    }

    /** NO3:PO4 mass ratio (Redfield ~100:1); flag < 50 or > 150. */
    fun nutrientRatio(no3: Double?, po4: Double?): Double? =
        if (no3 == null || po4 == null || po4 <= 0.0) null else no3 / po4

    fun ratioInsight(ratio: Double?): String? = when {
        ratio == null -> null
        ratio < 50 -> "NO3:PO4 is ${ratio.toInt()}:1 — phosphate is high relative to nitrate."
        ratio > 150 -> "NO3:PO4 is ${ratio.toInt()}:1 — phosphate is low relative to nitrate."
        else -> "NO3:PO4 is ${ratio.toInt()}:1 — near the Redfield sweet spot."
    }

    /** Mg/Ca ppm ratio, target 3.0–3.4. */
    fun mgCaRatio(mg: Double?, ca: Double?): Double? = if (mg == null || ca == null || ca <= 0) null else mg / ca
}
