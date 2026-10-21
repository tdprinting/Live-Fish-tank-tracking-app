package com.reeflive.core.sim

import com.reeflive.core.geom.smoothstep

/**
 * Pulsing xenia (docs/research/stony_corals.md §8): the polyps open and close their feathery tentacles about once a
 * second, every polyp its own pacemaker (Nadir et al. 2025, PNAS). Pulsing pumps water off the colony and raises its
 * photosynthesis (Kremien et al. 2013, PNAS), is slower without food (Hill et al. 2023) and, by hobby reports, stops in
 * a hard jet, with alkalinity under ~7 dKH or with the iodine used up.
 *
 * [Coral.tentacles] holds the pulse strength (0 still .. 1 full pulse) for xenia; the renderer (polyp.vert mode 4)
 * turns it into the beat.
 */
object Xenia {
    fun isXenia(c: Coral) = c.kind == CoralKind.XENIA

    @Ev(Evidence.DESIGN_ESTIMATE, "stony_corals.md §8: alk > 8 dKH 'encourages pulsing', < 7 'reduced'; iodine and food anecdotes")
    fun pulseTarget(c: Coral, w: WaterState, light: Double, flowStrength: Double): Double {
        if (c.melting) return 0.0
        val alk = smoothstep(6.5, 8.0, w.alk)
        val iodine = 0.3 + 0.7 * smoothstep(0.01, 0.035, w.i)
        val food = 0.75 + 0.25 * c.fed.coerceIn(0.0, 1.0)
        val calm = 1 - 0.8 * smoothstep(1.2, 1.8, flowStrength)
        val lit = 0.35 + 0.65 * smoothstep(0.05, 0.5, light)
        return (c.extension * alk * iodine * food * calm * lit * (1 - 0.7 * c.stress)).coerceIn(0.0, 1.0)
    }

    /** Eases the pulse toward its target over about an hour (it fades and comes back slowly). */
    fun stepPulse(c: Coral, w: WaterState, light: Double, flowStrength: Double, dtMin: Double) {
        if (!isXenia(c)) return
        val t = pulseTarget(c, w, light, flowStrength)
        c.tentacles += (t - c.tentacles) * (dtMin / 60.0).coerceAtMost(1.0)
    }
}
