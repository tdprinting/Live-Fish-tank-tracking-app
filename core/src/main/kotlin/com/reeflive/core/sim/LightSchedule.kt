package com.reeflive.core.sim

/**
 * AI Prime HD style 8-channel schedule (SIMULATION §4.7, ARCHITECTURE §8.5).
 * Channel order: white, blue, royal, red, green, violet, uv, moon (0..1 each).
 * Points are linearly interpolated and wrap around midnight.
 */
class LightSchedule(points: List<LightPoint> = DEFAULT_POINTS) {
    data class LightPoint(val minuteOfDay: Int, val channels: DoubleArray) {
        init { require(channels.size == CHANNELS) { "need $CHANNELS channels" } }
        override fun equals(other: Any?) = other is LightPoint && other.minuteOfDay == minuteOfDay && other.channels.contentEquals(channels)
        override fun hashCode() = minuteOfDay * 31 + channels.contentHashCode()
    }

    val points: List<LightPoint> = points.sortedBy { it.minuteOfDay }.ifEmpty { DEFAULT_POINTS }

    /** Manual override: null = follow schedule; false = all off; true = force on. */
    var forcedOn: Boolean? = null
    /** Optional intensity multiplier override (sandbox slider). */
    var intensityOverride: Double? = null
    /**
     * Sandbox photoperiod in hours (null = the programmed schedule). The schedule is stretched or
     * squeezed about its midday so ramps keep their shape (06 §4.2).
     */
    var photoperiodHours: Double? = null
    /** Per-channel gain (sandbox spectrum sliders), 0..2 each. */
    val gains = DoubleArray(CHANNELS) { 1.0 }

    /** Set one channel's gain by name (white, blue, royal, red, green, violet, uv, moon) or index. */
    fun setGain(channel: String, value: Double) {
        val i = CHANNEL_NAMES.indexOf(channel.lowercase()).takeIf { it >= 0 } ?: channel.toIntOrNull() ?: return
        if (i in 0 until CHANNELS) gains[i] = value.coerceIn(0.0, 2.0)
    }

    private fun minuteOf(simTimeSec: Double): Double {
        var m = (simTimeSec / 60.0) % 1440.0
        if (m < 0) m += 1440
        val ph = photoperiodHours ?: return m
        // stretch about the schedule's midday: programmed time = mid + (t − mid) × default / requested
        var d = m - MIDDAY_MIN
        if (d > 720) d -= 1440
        if (d < -720) d += 1440
        val k = DEFAULT_PHOTOPERIOD_H / ph.coerceIn(4.0, 16.0)
        val prog = (MIDDAY_MIN + (d * k).coerceIn(-719.0, 719.0)) % 1440.0
        return if (prog < 0) prog + 1440 else prog
    }

    fun channels(simTimeSec: Double, out: DoubleArray = DoubleArray(CHANNELS)): DoubleArray {
        if (forcedOn == false) { out.fill(0.0); return out }
        val m = minuteOf(simTimeSec)
        val pts = points
        val ia = pts.indexOfLast { it.minuteOfDay <= m }
        val a: LightPoint; val b: LightPoint; val am: Double; val bm: Double
        if (ia < 0) {
            a = pts.last(); am = a.minuteOfDay - 1440.0; b = pts.first(); bm = b.minuteOfDay.toDouble()
        } else if (ia == pts.size - 1) {
            a = pts[ia]; am = a.minuteOfDay.toDouble(); b = pts.first(); bm = b.minuteOfDay + 1440.0
        } else {
            a = pts[ia]; am = a.minuteOfDay.toDouble(); b = pts[ia + 1]; bm = b.minuteOfDay.toDouble()
        }
        val t = if (bm - am < 1e-9) 0.0 else ((m - am) / (bm - am)).coerceIn(0.0, 1.0)
        for (c in 0 until CHANNELS) out[c] = a.channels[c] + (b.channels[c] - a.channels[c]) * t
        if (forcedOn == true && intensity(out) < 0.3) {
            // "Lights on" outside the photoperiod: use the brightest scheduled point.
            val peak = pts.maxBy { intensity(it.channels) }
            peak.channels.copyInto(out)
        }
        intensityOverride?.let { k -> for (c in 0 until CHANNELS) out[c] = (out[c] * k).coerceIn(0.0, 1.0) }
        for (c in 0 until CHANNELS) if (gains[c] != 1.0) out[c] = (out[c] * gains[c]).coerceIn(0.0, 1.0)
        return out
    }

    fun intensity(simTimeSec: Double): Double = intensity(channels(simTimeSec))

    /** Minutes since intensity last rose above [LIGHTS_ON] (scans back up to 24 h), or null if dark now. */
    fun minutesSinceLightsOn(simTimeSec: Double): Double? {
        if (intensity(simTimeSec) < LIGHTS_ON) return null
        var m = 0.0
        while (m < 1440) { if (intensity(simTimeSec - (m + 5) * 60) < LIGHTS_ON) return m; m += 5 }
        return 1440.0
    }

    /** Minutes until intensity next drops below [LIGHTS_ON] (scans ahead up to 24 h), or null if dark now. */
    fun minutesUntilLightsOff(simTimeSec: Double): Double? {
        if (intensity(simTimeSec) < LIGHTS_ON) return null
        var m = 0.0
        while (m < 1440) { if (intensity(simTimeSec + (m + 5) * 60) < LIGHTS_ON) return m; m += 5 }
        return 1440.0
    }

    companion object {
        const val CHANNELS = 8
        const val LIGHTS_ON = 0.05
        val CHANNEL_NAMES = listOf("white", "blue", "royal", "red", "green", "violet", "uv", "moon")
        /** Lights-on length of [DEFAULT_POINTS] (08:30–19:45 above [LIGHTS_ON] is ~11 h; ~10 h at full ramp). */
        const val DEFAULT_PHOTOPERIOD_H = 11.0
        /** Midday of the default schedule (14:15) in minutes. */
        const val MIDDAY_MIN = 14 * 60.0 + 15
        /** Diode-count weights (4,4,4,1,1,1,1,1)/16. */
        val WEIGHTS = doubleArrayOf(4.0, 4.0, 4.0, 1.0, 1.0, 1.0, 1.0, 1.0).map { it / 16 }.toDoubleArray()

        fun intensity(ch: DoubleArray): Double {
            var s = 0.0
            for (i in 0 until CHANNELS) s += ch[i] * WEIGHTS[i]
            return s.coerceIn(0.0, 1.0)
        }

        private fun p(h: Int, m: Int, vararg c: Double) = LightPoint(h * 60 + m, c)

        /** "ai_soft_coral_default" (03 §F5 ramp in a 10-h photoperiod; moon 2 %). */
        val DEFAULT_POINTS = listOf(
            //        white blue royal red  green violet uv   moon
            p(0, 0,   0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.32),
            p(8, 0,   0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.32),
            p(8, 45,  0.0, 0.05, 0.30, 0.0, 0.0, 0.40, 0.20, 0.0),
            p(9, 30,  0.10, 0.50, 0.70, 0.05, 0.05, 0.60, 0.40, 0.0),
            p(10, 30, 0.60, 1.0, 1.0, 0.40, 0.40, 0.80, 0.60, 0.0),
            p(18, 0,  0.60, 1.0, 1.0, 0.40, 0.40, 0.80, 0.60, 0.0),
            p(19, 0,  0.05, 0.50, 0.80, 0.05, 0.05, 0.50, 0.30, 0.0),
            p(20, 0,  0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.32),
        )
    }
}
