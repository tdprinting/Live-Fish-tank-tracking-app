package com.reeflive.core.species

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Optimal / tolerable / lethal bounds for one parameter; a missing side means "not applicable".
 * Invariants: lethalLo ≤ tolLo ≤ optLo ≤ optHi ≤ tolHi ≤ lethalHi (for the bounds present).
 */
@Serializable
data class ToleranceBand(
    val optLo: Double? = null,
    val optHi: Double? = null,
    val tolLo: Double? = null,
    val tolHi: Double? = null,
    val lethalLo: Double? = null,
    val lethalHi: Double? = null,
) {
    /** Human-readable problems; empty when the band is consistent. */
    fun problems(): List<String> {
        val errs = mutableListOf<String>()
        fun chk(a: Double?, b: Double?, what: String) {
            if (a != null && b != null && a > b + 1e-12) errs += what
        }
        chk(optLo, optHi, "optLo > optHi")
        chk(optHi, tolHi, "optHi > tolHi")
        chk(tolHi, lethalHi, "tolHi > lethalHi")
        chk(optHi, lethalHi, "optHi > lethalHi")
        chk(tolLo, optLo, "tolLo > optLo")
        chk(lethalLo, tolLo, "lethalLo > tolLo")
        chk(lethalLo, optLo, "lethalLo > optLo")
        if (listOf(optLo, optHi, tolLo, tolHi, lethalLo, lethalHi).all { it == null }) errs += "empty band"
        return errs
    }

    val isValid: Boolean get() = problems().isEmpty()

    /**
     * Stress contribution 0..1 (SIMULATION §3.3): 0 inside optimal, quadratic to 0.4 at the tolerable
     * bound, then 0.4 + 0.6·x^1.5 up to 1 at the lethal bound (and beyond).
     */
    fun stress(v: Double): Double {
        optHi?.let { oh ->
            if (v > oh) return side(v - oh, (tolHi ?: oh) - oh, (lethalHi ?: tolHi ?: oh) - (tolHi ?: oh))
        }
        optLo?.let { ol ->
            if (v < ol) return side(ol - v, ol - (tolLo ?: ol), (tolLo ?: ol) - (lethalLo ?: tolLo ?: ol))
        }
        return 0.0
    }

    fun isLethal(v: Double): Boolean = (lethalHi != null && v >= lethalHi) || (lethalLo != null && v <= lethalLo)

    private fun side(d: Double, tolSpan: Double, lethalSpan: Double): Double {
        if (tolSpan > 0 && d <= tolSpan) return (d / tolSpan).pow(2) * 0.4
        val past = d - tolSpan.coerceAtLeast(0.0)
        if (lethalSpan <= 0) return 1.0
        if (past >= lethalSpan) return 1.0
        return 0.4 + 0.6 * (past / lethalSpan).pow(1.5)
    }
}

object Tolerance {
    /** Probabilistic OR of stressors: 1 − Π(1 − s). */
    fun combine(stresses: Collection<Double>): Double = 1.0 - stresses.fold(1.0) { acc, s -> acc * (1.0 - s.coerceIn(0.0, 1.0)) }
}
