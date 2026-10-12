package com.reeflive.core.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Edits for the light schedule editor (DESIGN §5.12). Points stay sorted by minute and at least
 * [MIN_GAP] minutes apart; levels are clamped to 0..1. Pure so the editor's behaviour is unit-tested.
 */
object LightEdit {
    const val MIN_GAP = 5
    const val MIN_POINTS = 2

    fun sorted(points: List<LightPoint>): List<LightPoint> = points.sortedBy { it.minuteOfDay }

    /** Sets one channel of point [index] (in sorted order). */
    fun setLevel(points: List<LightPoint>, index: Int, channel: Int, level: Double): List<LightPoint> {
        val s = sorted(points)
        if (index !in s.indices || channel !in 0 until LightChannel.COUNT) return s
        val p = s[index]
        val ch = p.channels.toMutableList().also { it[channel] = level.coerceIn(0.0, 1.0) }
        return s.toMutableList().also { it[index] = p.copy(channels = ch) }
    }

    /** Moves point [index] to [minute], kept strictly between its neighbours (no reordering). */
    fun move(points: List<LightPoint>, index: Int, minute: Int): List<LightPoint> {
        val s = sorted(points)
        if (index !in s.indices) return s
        val lo = if (index == 0) 0 else s[index - 1].minuteOfDay + MIN_GAP
        val hi = if (index == s.lastIndex) LightCurve.MINUTES_PER_DAY - 1 else s[index + 1].minuteOfDay - MIN_GAP
        if (lo > hi) return s
        return s.toMutableList().also { it[index] = s[index].copy(minuteOfDay = minute.coerceIn(lo, hi)) }
    }

    /**
     * Inserts a point at [minute] carrying the current interpolated levels (so the curve does not change).
     * Returns the new list and the inserted index, or null when too close to an existing point.
     */
    fun insert(points: List<LightPoint>, minute: Int): Pair<List<LightPoint>, Int>? {
        val m = minute.coerceIn(0, LightCurve.MINUTES_PER_DAY - 1)
        val s = sorted(points)
        if (s.any { abs(it.minuteOfDay - m) < MIN_GAP }) return null
        val levels = LightCurve.channelsAt(s, m.toDouble()).map { (it * 1000).roundToLong() / 1000.0 }
        val out = sorted(s + LightPoint(m, levels))
        return out to out.indexOfFirst { it.minuteOfDay == m }
    }

    /** Removes point [index]; a schedule keeps at least [MIN_POINTS] points. */
    fun remove(points: List<LightPoint>, index: Int): List<LightPoint> {
        val s = sorted(points)
        if (s.size <= MIN_POINTS || index !in s.indices) return s
        return s.toMutableList().also { it.removeAt(index) }
    }

    /** Photoperiod: minutes per day with diode-weighted intensity above [threshold]. */
    fun photoperiodMinutes(points: List<LightPoint>, threshold: Double = 0.05): Int =
        (0 until LightCurve.MINUTES_PER_DAY step 5).count { LightCurve.intensity(LightCurve.channelsAt(points, it.toDouble())) > threshold } * 5
}
