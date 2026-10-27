package com.reeflive.core.tracker

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** One test taken while a new tank cycles (research 01 §3.9). Epoch ms; a null value was not tested. */
data class CycleReading(val t: Double, val nh3: Double? = null, val no2: Double? = null, val no3: Double? = null)

enum class CycleStage(val label: String) {
    /** Nothing to process yet: no ammonia, nitrite or nitrate seen. */
    WAITING("Waiting for ammonia"),
    /** Ammonia-oxidizing bacteria are getting going: ammonia up, little nitrite. */
    AMMONIA("Ammonia spike"),
    /** Ammonia is being turned into nitrite; nitrite peaks, then falls. */
    NITRITE("Nitrite spike"),
    /** Ammonia and nitrite read 0, but not yet for [CycleTracker.CHECK_DAYS] days in a row. */
    CLEARING("Almost cycled"),
    /** [CycleTracker.CHECK_DAYS] days of 0 ammonia and 0 nitrite after the cycle got going. */
    CYCLED("Cycled"),
}

/**
 * Where a cycling tank stands. [daysLeft] is the best guess at days until it can be called cycled, inside the typical
 * window [daysLeftLo]..[daysLeftHi]. [zeroDays] counts the days in a row (latest first) with 0 ammonia and 0 nitrite.
 */
data class CycleStatus(
    val stage: CycleStage,
    val day: Int,
    val daysLeft: Int,
    val daysLeftLo: Int,
    val daysLeftHi: Int,
    val zeroDays: Int,
    val peakNh3: Double,
    val peakNo2: Double,
    val nh3: Double?,
    val no2: Double?,
    val no3: Double?,
    val advice: String,
)

/**
 * Cycle stage detection and a days-to-cycled estimate for a new tank (research 01 §3.9): ammonia spike → nitrite
 * spike → ammonia and nitrite at 0 for three days in a row. Typical total is 4 to 8 weeks fishless, 2 to 4 weeks with
 * cured live rock or bottled bacteria ([boosted]). Pure Kotlin: the web tracker calls it through ReefLive.cycleStatus.
 */
object CycleTracker {
    /** What a hobby kit reads as zero (ppm). */
    const val ZERO = 0.05
    /** Days in a row of 0 ammonia and 0 nitrite before the tank counts as cycled. */
    const val CHECK_DAYS = 3
    /** Nitrate (ppm) that on its own shows the bacteria have been at work (live rock cycles can skip a visible spike). */
    const val NO3_EVIDENCE = 1.0
    const val DAY_MS = 86_400_000.0
    /** Slack when readings are taken at slightly different times each day. */
    private const val SLACK_MS = 6 * 3_600_000.0

    fun assess(readings: List<CycleReading>, startT: Double, nowT: Double, boosted: Boolean = false): CycleStatus {
        val elapsed = max(0.0, (nowT - startT) / DAY_MS)
        val day = floor(elapsed).toInt() + 1
        val totalLo = if (boosted) 14 else 28
        val totalHi = if (boosted) 28 else 56

        // carry each value forward: a kit for ammonia and one for nitrite are often logged as separate entries
        val sorted = readings.sortedBy { it.t }
        val states = ArrayList<CycleReading>(sorted.size)
        var nh3: Double? = null; var no2: Double? = null; var no3: Double? = null
        for (r in sorted) {
            r.nh3?.let { nh3 = it }; r.no2?.let { no2 = it }; r.no3?.let { no3 = it }
            states += CycleReading(r.t, nh3, no2, no3)
        }
        val peakNh3 = sorted.maxOfOrNull { it.nh3 ?: 0.0 } ?: 0.0
        val peakNo2 = sorted.maxOfOrNull { it.no2 ?: 0.0 } ?: 0.0
        val peakNo3 = sorted.maxOfOrNull { it.no3 ?: 0.0 } ?: 0.0
        val cur = states.lastOrNull()

        fun clear(s: CycleReading) = s.nh3 != null && s.no2 != null && s.nh3 <= ZERO && s.no2 <= ZERO
        fun status(stage: CycleStage, lo: Int, hi: Int, best: Int, zero: Int, advice: String) = CycleStatus(
            stage, day, best.coerceIn(lo, hi), lo, hi, zero, peakNh3, peakNo2, cur?.nh3, cur?.no2, cur?.no3, advice,
        )

        if (cur == null) return status(CycleStage.WAITING, totalLo, totalHi, (totalLo + totalHi) / 2, 0,
            "Add an ammonia source (a pinch of fish food or bottled ammonia to about 1 to 2 ppm), then test ammonia, nitrite and nitrate every day or two.")

        // trailing run of clear tests
        var runStart = -1
        for (i in states.indices.reversed()) { if (clear(states[i])) runStart = i else break }
        val zeroDays = if (runStart < 0) 0 else
            minOf(CHECK_DAYS, floor((cur.t - states[runStart].t + SLACK_MS) / DAY_MS).toInt() + 1)
        val sawCycle = peakNh3 > ZERO || peakNo2 > ZERO || peakNo3 >= NO3_EVIDENCE

        if (runStart >= 0) {
            if (!sawCycle) return status(CycleStage.WAITING, totalLo, totalHi, (totalLo + totalHi) / 2, zeroDays,
                "Ammonia, nitrite and nitrate all read 0, so the bacteria have nothing to eat yet. Add an ammonia source (fish food or bottled ammonia).")
            if (zeroDays >= CHECK_DAYS) return status(CycleStage.CYCLED, 0, 0, 0, zeroDays,
                "Cycled: $CHECK_DAYS days of 0 ammonia and 0 nitrite. Do a water change to bring nitrate down, then add livestock slowly, a few at a time.")
            val n = CHECK_DAYS - zeroDays
            return status(CycleStage.CLEARING, n, n, n, zeroDays,
                "Ammonia and nitrite read 0. Keep dosing your ammonia source and test daily: $CHECK_DAYS days in a row at 0 means it is cycled.")
        }

        val a = cur.nh3 ?: 0.0
        val n = cur.no2 ?: 0.0
        val nitriteStage = n > ZERO && (a <= ZERO || n >= a || a < peakNh3 * 0.5)
        return if (nitriteStage) {
            val lo = max(if (boosted) 4 else 7, totalLo - elapsed.roundToInt())
            val hi = max(lo + (if (boosted) 5 else 10), totalHi - elapsed.roundToInt())
            // nitrite falling since its peak: project the slope down to 0, then the 3-day check
            val iPeak = states.indexOfLast { (it.no2 ?: 0.0) >= peakNo2 }
            val tPeak = states[iPeak].t
            val dropDays = (cur.t - tPeak) / DAY_MS
            val rate = if (dropDays >= 1.0) (peakNo2 - n) / dropDays else 0.0
            val best = if (rate > 0) ceil(n / rate).toInt() + CHECK_DAYS else (lo + hi) / 2
            status(CycleStage.NITRITE, lo, hi, best, 0,
                "Ammonia is turning into nitrite: the second half of the cycle. Keep dosing a little ammonia so the bacteria stay fed. Nitrite usually takes the longest to clear.")
        } else {
            val lo = max(if (boosted) 7 else 14, totalLo - elapsed.roundToInt())
            val hi = max(lo + (if (boosted) 7 else 14), totalHi - elapsed.roundToInt())
            status(CycleStage.AMMONIA, lo, hi, (lo + hi) / 2, 0,
                "The first bacteria are starting on the ammonia. Keep ammonia around 1 to 2 ppm (dose again if it reads 0) and watch for nitrite to show up.")
        }
    }
}
