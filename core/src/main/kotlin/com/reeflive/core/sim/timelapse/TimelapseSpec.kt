package com.reeflive.core.sim.timelapse

import com.reeflive.core.sim.Command

/**
 * One scripted change to the tank during a time-lapse. Times are hours from the start of the run.
 * All changes are delivered to the forked [com.reeflive.core.sim.Simulation] as ordinary [Command]s.
 */
sealed class TimelapseChange {
    /** Set [paramId] to [value] once, at [atHour]. */
    data class Step(val paramId: String, val value: Double, val atHour: Double = 0.0) : TimelapseChange()

    /**
     * Move [paramId] linearly to [to] over [overHours], starting at [startHour]. [from] null = the
     * value the water has when the ramp starts. Holds [to] afterwards.
     */
    data class Ramp(
        val paramId: String,
        val to: Double,
        val overHours: Double,
        val startHour: Double = 0.0,
        val from: Double? = null,
    ) : TimelapseChange()

    /**
     * A schedule of (hour, value) points, each applied as a step at its hour. With [repeatEveryHours]
     * the pattern repeats (a daily dip is `repeatEveryHours = 24` with hours inside 0..24).
     */
    data class Schedule(
        val paramId: String,
        val points: List<Pair<Double, Double>>,
        val repeatEveryHours: Double? = null,
    ) : TimelapseChange()

    /** Any other existing [Command] (Dose, WaterChange, Heater...) at [atHour]. */
    data class Do(val command: Command, val atHour: Double = 0.0) : TimelapseChange()
}

/**
 * What to time-lapse: [changes] applied over [durationHours], sampled every [sampleEveryHours].
 * [chunkMinutes] is the control resolution: the sim advances in abstract (60 s chemistry/health)
 * steps in chunks this long, and ramps are re-applied at each chunk boundary.
 */
data class TimelapseSpec(
    val changes: List<TimelapseChange>,
    val durationHours: Double,
    val sampleEveryHours: Double = autoSampleInterval(durationHours),
    val chunkMinutes: Double = 30.0,
    val label: String = "",
) {
    init {
        require(durationHours > 0) { "duration must be positive" }
        require(sampleEveryHours > 0) { "sample interval must be positive" }
        require(chunkMinutes > 0) { "chunk must be positive" }
    }

    val durationSec: Double get() = durationHours * 3600.0
    val sampleSec: Double get() = sampleEveryHours * 3600.0
    val chunkSec: Double get() = minOf(chunkMinutes * 60.0, sampleSec)

    companion object {
        const val HOUR = 1.0
        const val DAY = 24.0
        const val WEEK = 168.0

        /** About 150-250 samples whatever the duration, on friendly intervals. */
        fun autoSampleInterval(durationHours: Double): Double {
            val ideal = durationHours / 200.0
            return listOf(0.25, 0.5, 1.0, 2.0, 3.0, 6.0, 12.0).firstOrNull { it >= ideal } ?: 12.0
        }

        /** Single step of [paramId] to [value] at t=0. */
        fun step(paramId: String, value: Double, durationHours: Double, label: String = "") =
            TimelapseSpec(listOf(TimelapseChange.Step(paramId, value)), durationHours, label = label)

        /** Ramp of [paramId] to [value] over [rampHours] from t=0. */
        fun ramp(paramId: String, value: Double, rampHours: Double, durationHours: Double, label: String = "") =
            TimelapseSpec(listOf(TimelapseChange.Ramp(paramId, value, rampHours)), durationHours, label = label)

        /** No changes: the baseline run. */
        fun none(durationHours: Double) = TimelapseSpec(emptyList(), durationHours, label = "No change")
    }
}
