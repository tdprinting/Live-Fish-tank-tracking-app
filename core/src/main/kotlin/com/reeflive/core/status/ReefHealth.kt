package com.reeflive.core.status

import com.reeflive.core.params.ParameterDef
import kotlin.math.abs
import kotlin.math.roundToInt

/** How steady one parameter has been: 100 = flat, 0 = consecutive tests jump by half the acceptable band or more. */
data class ParamStability(val paramId: String, val score: Int, val maxSwingPerDay: Double?, val samples: Int)

data class StableWeek(val startMs: Long, val endMs: Long, val score: Int)

data class HealthPart(val label: String, val points: Int, val max: Int, val detail: String)

data class HealthScore(val score: Int, val grade: String, val parts: List<HealthPart>)

/** Tank health score and stability insights (research 01 §2.2, §5). Pure and unit-tested. */
object ReefHealth {
    private const val DAY = 86_400_000L

    /** Readings in [fromMs, toMs], oldest first. */
    private fun window(history: List<Reading>, fromMs: Long, toMs: Long) =
        history.filter { it.epochMs in fromMs..toMs }.sortedBy { it.epochMs }

    /** Largest change per day between consecutive tests (a 6 h gap counts as a quarter day, but never less). */
    fun maxSwingPerDay(history: List<Reading>, fromMs: Long, toMs: Long): Double? {
        val w = window(history, fromMs, toMs)
        if (w.size < 2) return null
        return w.zipWithNext { a, b -> abs(b.value - a.value) / maxOf(0.25, (b.epochMs - a.epochMs).toDouble() / DAY) }.max()
    }

    fun stability(def: ParameterDef, history: List<Reading>, fromMs: Long, toMs: Long): ParamStability? {
        val w = window(history, fromMs, toMs)
        if (w.size < 3) return null
        val span = def.acceptable.span.takeIf { it > 0 } ?: return null
        val meanStep = w.zipWithNext { a, b -> abs(b.value - a.value) }.average()
        val score = (100.0 * (1.0 - meanStep / (span * 0.5))).coerceIn(0.0, 100.0).roundToInt()
        return ParamStability(def.id, score, maxSwingPerDay(w, fromMs, toMs), w.size)
    }

    /** The 7-day window (stepping one day back over [lookbackDays]) with the best average stability. */
    fun mostStableWeek(defs: List<ParameterDef>, histories: Map<String, List<Reading>>, toMs: Long, lookbackDays: Int = 90): StableWeek? {
        var best: StableWeek? = null
        for (k in 0..(lookbackDays - 7)) {
            val end = toMs - k * DAY
            val start = end - 7 * DAY
            val scores = defs.mapNotNull { d -> histories[d.id]?.let { stability(d, it, start, end)?.score } }
            if (scores.size < 2) continue
            val avg = scores.average().roundToInt()
            if (best == null || avg > best.score) best = StableWeek(start, end, avg)
        }
        return best
    }

    /** NO3:PO4 mass ratios for tests where both were measured within an hour of each other. */
    fun ratioSeries(no3: List<Reading>, po4: List<Reading>): List<Pair<Long, Double>> =
        no3.sortedBy { it.epochMs }.mapNotNull { n ->
            val p = po4.minByOrNull { abs(it.epochMs - n.epochMs) } ?: return@mapNotNull null
            if (abs(p.epochMs - n.epochMs) > 3_600_000L) return@mapNotNull null
            InsightEngine.nutrientRatio(n.value, p.value)?.let { n.epochMs to it }
        }

    /**
     * 0–100: parameters in range (50), stability (20), testing recency (15), maintenance on time (15).
     * [statuses] are the current statuses of logged parameters (UNKNOWN ones are ignored).
     */
    fun score(statuses: List<Status>, stabilities: List<ParamStability>, daysSinceTest: Double?, overdueTasks: Int): HealthScore {
        val known = statuses.filter { it != Status.UNKNOWN }
        val paramPts = if (known.isEmpty()) 0 else (50.0 * known.map {
            when (it) { Status.GOOD -> 1.0; Status.CAUTION -> 0.5; else -> 0.0 }
        }.average()).roundToInt()
        val good = known.count { it == Status.GOOD }
        val paramDetail = if (known.isEmpty()) "No tests yet" else "$good of ${known.size} in the ideal range"

        val stabPts = if (stabilities.isEmpty()) 10 else (20.0 * stabilities.map { it.score }.average() / 100.0).roundToInt()
        val stabDetail = if (stabilities.isEmpty()) "Needs 3+ tests per parameter" else "Average ${stabilities.map { it.score }.average().roundToInt()} / 100 over 30 days"

        val testPts = when {
            daysSinceTest == null -> 0
            daysSinceTest <= 3.0 -> 15
            daysSinceTest <= 7.0 -> 10
            daysSinceTest <= 14.0 -> 5
            else -> 0
        }
        val testDetail = when {
            daysSinceTest == null -> "Log your first test"
            daysSinceTest < 1.0 -> "Tested today"
            else -> "Last test ${daysSinceTest.toInt()} d ago"
        }

        val careP = (15 - 5 * overdueTasks).coerceAtLeast(0)
        val careDetail = if (overdueTasks == 0) "All tasks on time" else "$overdueTasks overdue"

        val parts = listOf(
            HealthPart("Parameters", paramPts, 50, paramDetail),
            HealthPart("Stability", stabPts, 20, stabDetail),
            HealthPart("Testing", testPts, 15, testDetail),
            HealthPart("Maintenance", careP, 15, careDetail),
        )
        val total = parts.sumOf { it.points }.coerceIn(0, 100)
        return HealthScore(total, grade(total), parts)
    }

    fun grade(score: Int): String = when {
        score >= 85 -> "Thriving"
        score >= 70 -> "Healthy"
        score >= 50 -> "Needs attention"
        else -> "At risk"
    }
}
