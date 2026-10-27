package com.reeflive.core.live

/**
 * Plausibility check for logged tracker values (base units: nitrate ppm NO3, phosphate ppm PO4).
 * A reef tank at NO3 > 50 or PO4 > 0.5 is possible but far more often a unit slip: NO3 vs NO3-N (x4.43),
 * PO4 vs PO4-P (x3.07) or ppb typed as ppm. The tracker and the sandbox start show [warning] instead of silently trusting it.
 */
object ReadingSanity {
    const val NO3_WARN = 50.0
    const val PO4_WARN = 0.5
    /** Hard limits the simulation is fed (beyond these is nonsense, not a tank). */
    const val NO3_MAX = 250.0
    const val PO4_MAX = 5.0

    /** A short "check units" note for an implausible logged [value] of [paramId], or null when it looks fine. */
    fun warning(paramId: String, value: Double): String? = when {
        value.isNaN() -> null
        paramId == "no3" && value > NO3_WARN -> "Nitrate ${fmt(value)} ppm looks very high. Check units (NO3 vs NO3-N is x4.43)."
        paramId == "po4" && value > PO4_WARN -> "Phosphate ${fmt(value)} ppm looks very high. Check units (PO4 vs PO4-P is x3.07, or ppb typed as ppm)."
        else -> null
    }

    private fun fmt(v: Double): String = if (v >= 10) v.toInt().toString() else ((v * 100).toInt() / 100.0).toString()
}
