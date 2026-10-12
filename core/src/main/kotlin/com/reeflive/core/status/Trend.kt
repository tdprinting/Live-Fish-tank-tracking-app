package com.reeflive.core.status

import com.reeflive.core.params.ParameterDef

enum class Trend { UP, DOWN, FLAT, UNKNOWN }

/** Reading in the parameter's base unit. */
data class Reading(val paramId: String, val value: Double, val epochMs: Long, val kit: String? = null)

object TrendCalc {
    const val DAY_MS = 86_400_000L
    const val MIN_SPAN_DAYS = 2.0

    /**
     * Least-squares slope (units/day) over the last 3 readings; when those span < 2 days the
     * window grows backwards until it spans 2 days (or uses everything available).
     * Returns null with fewer than two readings or a zero time span.
     */
    fun slopePerDay(sortedHistory: List<Reading>): Double? {
        if (sortedHistory.size < 2) return null
        val last = sortedHistory.last().epochMs
        var start = (sortedHistory.size - 3).coerceAtLeast(0)
        while (start > 0 && (last - sortedHistory[start].epochMs) / DAY_MS.toDouble() < MIN_SPAN_DAYS) start--
        return leastSquares(sortedHistory.subList(start, sortedHistory.size))
    }

    fun leastSquares(points: List<Reading>): Double? {
        if (points.size < 2) return null
        val t0 = points.first().epochMs
        val xs = points.map { (it.epochMs - t0) / DAY_MS.toDouble() }
        val ys = points.map { it.value }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        if (den <= 1e-12) return null
        return num / den
    }

    /** Flat threshold per day: 0.25 × cap, else 2 % of the ideal span (acceptable span when ideal is a point). */
    fun flatThreshold(def: ParameterDef): Double {
        def.maxChangePerDay?.let { return 0.25 * it }
        val span = if (def.ideal.span > 0) def.ideal.span else def.acceptable.span
        return if (span > 0) 0.02 * span else 1e-3
    }

    fun classify(def: ParameterDef, slope: Double?): Trend = when {
        slope == null -> Trend.UNKNOWN
        slope > flatThreshold(def) -> Trend.UP
        slope < -flatThreshold(def) -> Trend.DOWN
        else -> Trend.FLAT
    }
}
