package com.reeflive.core.model

import kotlinx.serialization.Serializable

/**
 * AI Prime HD channels in the fixed order used everywhere (ARCHITECTURE §8.5):
 * white, blue, royal, red, green, violet, UV, moon. [argb] is the UI swatch color
 * (UI_STYLE §9.3), [rgb] the linear render color and [weight] the diode-count weight.
 */
enum class LightChannel(val label: String, val argb: Long, val rgb: FloatArray, val weight: Float) {
    WHITE("Cool white", 0xFFDDEEFF, floatArrayOf(0.85f, 0.93f, 1.0f), 4f / 16f),
    BLUE("Blue", 0xFF3AA0FF, floatArrayOf(0.23f, 0.63f, 1.0f), 4f / 16f),
    ROYAL("Royal blue", 0xFF2E5BFF, floatArrayOf(0.18f, 0.36f, 1.0f), 4f / 16f),
    RED("Photo red", 0xFFFF4D4D, floatArrayOf(1.0f, 0.3f, 0.3f), 1f / 16f),
    GREEN("Green", 0xFF4DFF8A, floatArrayOf(0.3f, 1.0f, 0.54f), 1f / 16f),
    VIOLET("Violet", 0xFF6A3DFF, floatArrayOf(0.42f, 0.24f, 1.0f), 1f / 16f),
    UV("UV", 0xFF8F6BFF, floatArrayOf(0.175f, 0.1f, 0.45f), 1f / 16f),
    MOON("Moon", 0xFF2B3A6E, floatArrayOf(0.0255f, 0.0345f, 0.0645f), 1f / 16f),
    ;

    companion object {
        const val COUNT = 8
    }
}

/** One control point: [minuteOfDay] 0..1439 and 8 channel levels 0..1 (order of [LightChannel]). */
@Serializable
data class LightPoint(val minuteOfDay: Int, val channels: List<Double>) {
    init {
        require(channels.size == LightChannel.COUNT) { "LightPoint needs ${LightChannel.COUNT} channels" }
    }
}

@Serializable
data class LightSchedule(
    val id: String,
    val tankId: String,
    val name: String,
    val points: List<LightPoint>,
    val active: Boolean = true,
) {
    /** Channel levels at [minuteOfDay] (fractional allowed), linear between points, wrapping midnight. */
    fun channelsAt(minuteOfDay: Double): DoubleArray = LightCurve.channelsAt(points, minuteOfDay)

    /** Diode-weighted overall intensity 0..1 (ARCHITECTURE §8.5). */
    fun intensityAt(minuteOfDay: Double): Double = LightCurve.intensity(channelsAt(minuteOfDay))
}

object LightCurve {
    const val MINUTES_PER_DAY = 1440

    fun channelsAt(points: List<LightPoint>, minuteOfDay: Double): DoubleArray {
        if (points.isEmpty()) return DoubleArray(LightChannel.COUNT)
        val sorted = points.sortedBy { it.minuteOfDay }
        if (sorted.size == 1) return sorted[0].channels.toDoubleArray()
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        var prev = sorted.last()
        var prevMin = (prev.minuteOfDay - MINUTES_PER_DAY).toDouble()
        for (p in sorted) {
            if (p.minuteOfDay >= m) {
                return lerp(prev, prevMin, p, p.minuteOfDay.toDouble(), m)
            }
            prev = p
            prevMin = p.minuteOfDay.toDouble()
        }
        val first = sorted.first()
        return lerp(prev, prevMin, first, (first.minuteOfDay + MINUTES_PER_DAY).toDouble(), m)
    }

    private fun lerp(a: LightPoint, am: Double, b: LightPoint, bm: Double, m: Double): DoubleArray {
        val span = bm - am
        val t = if (span <= 0.0) 0.0 else ((m - am) / span).coerceIn(0.0, 1.0)
        return DoubleArray(LightChannel.COUNT) { i -> a.channels[i] + (b.channels[i] - a.channels[i]) * t }
    }

    fun intensity(channels: DoubleArray): Double {
        var s = 0.0
        LightChannel.entries.forEachIndexed { i, c -> s += channels[i] * c.weight }
        return s.coerceIn(0.0, 1.0)
    }

    /**
     * Perceived display color (sRGB ARGB) for the swatch strip: weighted channel mix,
     * normalised to its brightest component and scaled by a soft intensity curve so night stays deep blue.
     */
    fun swatchArgb(channels: DoubleArray): Long {
        var r = 0.0; var g = 0.0; var b = 0.0
        LightChannel.entries.forEachIndexed { i, c ->
            val w = channels[i] * (if (c == LightChannel.MOON) 1.0 else c.weight.toDouble() * 4.0)
            val col = c.argb
            r += w * ((col shr 16) and 0xFF) / 255.0
            g += w * ((col shr 8) and 0xFF) / 255.0
            b += w * (col and 0xFF) / 255.0
        }
        val maxC = maxOf(r, g, b)
        val night = doubleArrayOf(0.02, 0.03, 0.07)
        if (maxC <= 1e-6) return argb(night[0], night[1], night[2])
        val level = Math.sqrt(intensity(channels).coerceAtLeast(channels[LightChannel.MOON.ordinal] * 0.08)).coerceIn(0.0, 1.0)
        val k = level / maxC
        return argb(night[0] + r * k, night[1] + g * k, night[2] + b * k)
    }

    private fun argb(r: Double, g: Double, b: Double): Long {
        fun c(v: Double) = (v.coerceIn(0.0, 1.0) * 255.0 + 0.5).toLong()
        return (0xFFL shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** Builds a point from named levels (missing channels are 0). */
    fun point(hour: Int, minute: Int = 0, white: Double = 0.0, blue: Double = 0.0, royal: Double = 0.0, red: Double = 0.0,
              green: Double = 0.0, violet: Double = 0.0, uv: Double = 0.0, moon: Double = 0.0): LightPoint =
        LightPoint(hour * 60 + minute, listOf(white, blue, royal, red, green, violet, uv, moon))

    /** "AI Soft Coral" default: 10-h photoperiod, moon 2 % at night (SIMULATION §4.7, 03 §F5). */
    val AI_SOFT_CORAL: List<LightPoint> = listOf(
        point(0, 0, moon = 0.02),
        point(8, 0, moon = 0.02),
        point(8, 45, royal = 0.20, violet = 0.20, uv = 0.10),
        point(9, 30, white = 0.15, blue = 0.30, royal = 0.40, red = 0.05, green = 0.05, violet = 0.30, uv = 0.20),
        point(10, 30, white = 0.45, blue = 0.60, royal = 0.65, red = 0.20, green = 0.20, violet = 0.45, uv = 0.35),
        point(18, 0, white = 0.45, blue = 0.60, royal = 0.65, red = 0.20, green = 0.20, violet = 0.45, uv = 0.35),
        point(19, 0, white = 0.10, blue = 0.35, royal = 0.45, red = 0.05, green = 0.05, violet = 0.35, uv = 0.20),
        point(20, 0, moon = 0.02),
    )

    /** "Blue heavy": same timing, whites/reds pulled down, blues up. */
    val BLUE_HEAVY: List<LightPoint> = AI_SOFT_CORAL.map { p ->
        val c = p.channels
        LightPoint(p.minuteOfDay, listOf(c[0] * 0.4, (c[1] * 1.3).coerceAtMost(1.0), (c[2] * 1.3).coerceAtMost(1.0), c[3] * 0.5, c[4] * 0.5, c[5], c[6], c[7]))
    }

    /** "Acclimation −30 %": every daylight channel scaled by 0.7. */
    val ACCLIMATION: List<LightPoint> = AI_SOFT_CORAL.map { p ->
        LightPoint(p.minuteOfDay, p.channels.mapIndexed { i, v -> if (i == LightChannel.MOON.ordinal) v else v * 0.7 })
    }

    val PRESETS: Map<String, List<LightPoint>> = linkedMapOf(
        "AI Soft Coral" to AI_SOFT_CORAL,
        "Blue heavy" to BLUE_HEAVY,
        "Acclimation −30%" to ACCLIMATION,
    )
}
