package com.reeflive.app.ui.charts

import com.reeflive.core.model.EventType
import com.reeflive.core.params.Range
import com.reeflive.core.status.Status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One plotted reading, already in display units. */
data class ChartPoint(val t: Long, val v: Double, val status: Status = Status.UNKNOWN, val kit: String? = null)

/** An event marker on the x-axis baseline. */
data class ChartEvent(val t: Long, val type: EventType, val title: String)

/** Target bands in display units. */
data class ChartBands(val ideal: Range, val acceptable: Range, val dangerLow: Double?, val dangerHigh: Double?)

/** A second series drawn against a right-hand axis. */
data class OverlaySeries(val label: String, val points: List<ChartPoint>, val precision: Int, val unit: String)

/** Linear time/value → pixel mapping for one plot rectangle. */
class ChartScale(
    val t0: Long,
    val t1: Long,
    val v0: Double,
    val v1: Double,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun x(t: Long): Float = left + ((t - t0).toDouble() / max(1L, t1 - t0) * width).toFloat()
    fun y(v: Double): Float = top + ((1.0 - (v - v0) / (v1 - v0).let { if (it == 0.0) 1.0 else it }) * height).toFloat()
    fun tAt(x: Float): Long = t0 + ((x - left) / width * (t1 - t0)).toLong()
}

object ChartMath {
    const val DAY_MS = 86_400_000L
    const val MIN_SPAN_MS = 3 * DAY_MS

    /** Value domain: union of the visible data and the acceptable band, padded 10 % (UI_STYLE §8.3, §9.1). */
    fun domain(values: List<Double>, band: Range?): Range {
        var lo = values.minOrNull() ?: band?.lo ?: 0.0
        var hi = values.maxOrNull() ?: band?.hi ?: 1.0
        if (band != null) {
            lo = min(lo, band.lo)
            hi = max(hi, band.hi)
        }
        var span = hi - lo
        if (span <= 1e-12) span = if (abs(hi) > 1e-9) abs(hi) * 0.2 else 1.0
        return Range(lo - span * 0.1, hi + span * 0.1)
    }

    /** About [count] "nice" tick values (1-2-5 steps) inside [r]. */
    fun niceTicks(r: Range, count: Int = 4): List<Double> {
        val span = r.hi - r.lo
        if (span <= 0 || count < 1) return listOf(r.lo)
        val raw = span / count
        val mag = 10.0.pow(floor(log10(raw)))
        val norm = raw / mag
        val step = mag * when {
            norm < 1.5 -> 1.0
            norm < 3.0 -> 2.0
            norm < 7.0 -> 5.0
            else -> 10.0
        }
        val first = ceil(r.lo / step) * step
        val out = mutableListOf<Double>()
        var v = first
        while (v <= r.hi + step * 1e-6 && out.size < 20) {
            out += if (abs(v) < step * 1e-9) 0.0 else v
            v += step
        }
        return out
    }

    /** Decimal places that distinguish consecutive ticks (0–4). */
    fun tickDecimals(ticks: List<Double>, precision: Int): Int {
        if (ticks.size < 2) return precision.coerceIn(0, 4)
        val step = abs(ticks[1] - ticks[0])
        val d = if (step >= 1) 0 else ceil(-log10(step) - 1e-9).toInt()
        return d.coerceIn(0, 4)
    }

    /** Median interval between consecutive points; gaps longer than 3× it are dashed. */
    fun gapThreshold(points: List<ChartPoint>): Long {
        if (points.size < 3) return Long.MAX_VALUE
        val d = points.zipWithNext { a, b -> b.t - a.t }.sorted()
        return d[d.size / 2] * 3
    }

    /** Index of the point nearest in time to [t], or −1. */
    fun nearest(points: List<ChartPoint>, t: Long): Int {
        if (points.isEmpty()) return -1
        var best = 0
        var bestD = Long.MAX_VALUE
        points.forEachIndexed { i, p ->
            val d = abs(p.t - t)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** Applies a pinch [zoom] around the time under the fingers and clamps the span to [min, max]. */
    fun zoom(t0: Long, t1: Long, zoom: Float, anchor: Long, minSpan: Long = MIN_SPAN_MS, maxSpan: Long): Pair<Long, Long> {
        if (zoom <= 0f) return t0 to t1
        val span = (t1 - t0).toDouble()
        val newSpan = (span / zoom).coerceIn(minSpan.toDouble(), max(minSpan, maxSpan).toDouble())
        val f = if (span <= 0) 0.5 else (anchor - t0) / span
        val n0 = (anchor - f * newSpan).toLong()
        return n0 to (n0 + newSpan.toLong())
    }

    /** Keeps a [t0, t1] window inside [lo, hi] without changing its span (when it fits). */
    fun clampWindow(t0: Long, t1: Long, lo: Long, hi: Long): Pair<Long, Long> {
        val span = t1 - t0
        if (span >= hi - lo) return lo to max(hi, lo + span)
        var a = t0
        if (a < lo) a = lo
        if (a + span > hi) a = hi - span
        return a to a + span
    }

    data class XTick(val t: Long, val label: String)

    private val fmtDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val fmtMonth = DateTimeFormatter.ofPattern("MMM", Locale.US)

    /** X-axis ticks by visible span: weekday initials (≤ 10 d), dates (≤ 120 d), months beyond. */
    fun xTicks(t0: Long, t1: Long, zone: ZoneId): List<XTick> {
        val span = t1 - t0
        val start = Instant.ofEpochMilli(t0).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(t1).atZone(zone).toLocalDate()
        fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val out = mutableListOf<XTick>()
        when {
            span <= 10 * DAY_MS -> {
                var d = start
                while (!d.isAfter(end)) {
                    val t = ms(d)
                    if (t in t0..t1) out += XTick(t, d.dayOfWeek.name.take(1))
                    d = d.plusDays(1)
                }
            }
            span <= 45 * DAY_MS || span <= 120 * DAY_MS -> {
                val stepDays = if (span <= 45 * DAY_MS) 7L else 14L
                var d = start
                while (!d.isAfter(end)) {
                    val t = ms(d)
                    if (t in t0..t1) out += XTick(t, fmtDay.format(d))
                    d = d.plusDays(stepDays)
                }
            }
            else -> {
                var d = start.withDayOfMonth(1).plusMonths(1)
                val stepMonths = if (span > 500 * DAY_MS) 3L else 1L
                while (!d.isAfter(end)) {
                    out += XTick(ms(d), fmtMonth.format(d))
                    d = d.plusMonths(stepMonths)
                }
            }
        }
        return out
    }
}
