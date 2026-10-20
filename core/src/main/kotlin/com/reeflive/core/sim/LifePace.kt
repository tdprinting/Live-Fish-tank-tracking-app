package com.reeflive.core.sim

/**
 * How fast slow biology runs in My tank ([Simulation.lifePace]): a game, so corals spread, fish grow and babies come
 * along in days of play instead of real months. Only the slow processes are sped up; animals still swim, eat and
 * sleep at real speed, the lights follow the keeper's clock, and water drift, filter fouling and harm from bad water
 * stay on real time so neglect is no harsher at a faster pace (daily care and sand dollars count real days).
 *
 * The keeper's choices, kid-sized: Real time, Fast (the default: one real day is about one tank week, so clown eggs
 * hatch in about a day, fry grow up in under a week and a new frag visibly spreads within days) and Faster (a real
 * day is a tank month). Watch mirrors the real tank and stays at 1.
 */
enum class LifePace(val pace: Double, val label: String, val blurb: String) {
    REAL(1.0, "Real time", "Things grow as slowly as in a real tank"),
    FAST(7.0, "Fast", "One day of play is about a week in the tank"),
    FASTER(30.0, "Faster", "One day of play is about a month in the tank");

    companion object {
        const val MIN = 1.0
        const val MAX = 30.0
        val DEFAULT = FAST

        /** The choice closest to [pace] (a save keeps the number). */
        fun of(pace: Double): LifePace = entries.minBy { kotlin.math.abs(kotlin.math.ln(it.pace / pace.coerceIn(MIN, MAX))) }
    }
}
