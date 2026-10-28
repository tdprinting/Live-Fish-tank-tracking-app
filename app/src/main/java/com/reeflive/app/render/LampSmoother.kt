package com.reeflive.app.render

import kotlin.math.exp

/**
 * Display-side smoothing of the lamp's 8 channels ([com.reeflive.core.sim.SimSnapshot.lights]) so fast-forward does not
 * strobe. The sim's photoperiod is untouched: only what the renderer lights the tank with lags behind it.
 *
 * - At real time and modest speeds the light follows the schedule almost at once ([TAU_NORMAL_S]), so a lamp switched
 *   by hand still reacts within a blink.
 * - From [FAST_SPEED] up, every change is blended over at least [MIN_BLEND_S] of wall time (a critically damped pair of
 *   low-pass stages: an S-shaped fade with no overshoot), so a dawn ramp that the clock runs through in a fraction of a
 *   second never flips the tank from dark to bright between two frames.
 * - When a whole day passes in under [AVERAGE_DAY_WALL_S] wall seconds the eye cannot follow the day any more: the
 *   time constant grows to a day's wall time, so the tank shows the time-averaged level instead of pulsing.
 *
 * Pure math, no GL, no allocation per frame (the result is [out], reused).
 */
class LampSmoother {
    private val a = FloatArray(CHANNELS)
    val out = FloatArray(CHANNELS)
    private var started = false

    /** Snap to [target] on the next [step] (a new tank, or the first frame). */
    fun reset() { started = false }

    /**
     * Advances by [dtWall] seconds of wall time toward [target] (channels 0..1) at sim [speed] (× real time); returns
     * the smoothed channels ([out]).
     */
    fun step(target: FloatArray, speed: Double, dtWall: Float): FloatArray {
        val n = minOf(target.size, CHANNELS)
        if (!started) {
            for (i in 0 until CHANNELS) { val v = if (i < n) target[i] else 0f; a[i] = v; out[i] = v }
            started = true
            return out
        }
        val tau = timeConstant(speed)
        // two equal stages; each takes half the time constant so the pair settles in about the same time as one stage
        val k = if (dtWall <= 0f) 0f else 1f - exp(-dtWall / (0.5f * tau))
        for (i in 0 until CHANNELS) {
            val t = if (i < n) target[i] else 0f
            a[i] += (t - a[i]) * k
            out[i] += (a[i] - out[i]) * k
        }
        return out
    }

    companion object {
        const val CHANNELS = 8
        /** Speeds (× real time) from which the lamp is blended over [MIN_BLEND_S]. */
        const val FAST_SPEED = 30.0
        /** Time constant (s) at real time: barely a fade. */
        const val TAU_NORMAL_S = 0.12f
        /** Least wall time (s) a light change takes on screen at fast-forward (≈ 95 % of the way after ~2.5 τ). */
        const val MIN_BLEND_S = 0.7f
        /** A day shorter than this (wall seconds) shows the day's average light. */
        const val AVERAGE_DAY_WALL_S = 20.0

        /** The blend's time constant (wall seconds) at sim [speed]. */
        fun timeConstant(speed: Double): Float {
            if (speed.isNaN() || speed < FAST_SPEED) return TAU_NORMAL_S
            val dayWall = 86_400.0 / speed
            return if (dayWall < AVERAGE_DAY_WALL_S) AVERAGE_DAY_WALL_S.toFloat() else MIN_BLEND_S
        }
    }
}
