package com.reeflive.core.ai

import com.reeflive.core.sim.Ev
import com.reeflive.core.sim.Evidence
import kotlin.math.max

/**
 * Brittle-star light sense (research realism_gaps §4.5 P1; Aizenberg et al. 2001, *Nature* 412:819: the arm plates of
 * *Ophiocoma wendtii* carry calcite microlenses that work as photoreceptors; the tank's star is *O. erinaceus*, so the
 * transfer is an ANALOGY). The arms, not the night flag, read the light:
 *
 * - a quick rise in light (lights on, the moon light turned up) or a shadow passing within reach makes every arm whip
 *   in and the disc hold still for [HOLD_MIN_S]–[HOLD_MAX_S] s ("freeze"), after which the existing behaviours take
 *   over: in daylight the star then rows back to its hide (video invert_brittle_star S3: they run for shelter in a lit
 *   arena), at night it carries on;
 * - by day the arm tips left out of the hide reach less the brighter it is ([dayReach]).
 *
 * Idle (score 0, no rng, no noise) until a trigger, so the rest of the star's behaviour is unchanged.
 */
class LightWithdraw(val b: BrittleStarBrain) : Behavior("lightshy", "Pulling its arms in from the light") {
    override val noisy: Boolean get() = false
    private var lightAt = -1.0
    private var lightRef = -1.0
    private var trigAt = -1e9
    private var seenShadow = -1e9
    private var hold = HOLD_MIN_S
    /** Light-triggered withdrawals so far (tests). */
    var withdrawals = 0; private set
    override val committed: Boolean get() = elapsed < hold

    override fun score(c: Ctx): Double {
        val l = c.p.light
        // a running reference that follows slow ramps (dawn) but not a step: a rise of RISE within RISE_WINDOW_S triggers
        if (lightAt < 0) { lightAt = c.t; lightRef = l }
        val k = (c.dt / RISE_WINDOW_S).coerceAtMost(1.0)
        val rose = l - lightRef > RISE
        lightRef += (l - lightRef) * k
        if (l < lightRef) lightRef = l
        val shadow = b.shadowAt > seenShadow && c.t - b.shadowAt < 1.0
        if (shadow) seenShadow = b.shadowAt
        if (rose || shadow) {
            if (c.t - trigAt > REFRACTORY_S) {
                trigAt = c.t
                hold = HOLD_MIN_S + (HOLD_MAX_S - HOLD_MIN_S) * (0.5 + 0.5 * kotlin.math.sin(c.t + c.o.appendagePhase))
                withdrawals++
            }
            if (rose) lightRef = l
        }
        return if (c.t - trigAt < hold) 0.93 else 0.0
    }

    override fun update(c: Ctx, out: SteerIntent) {
        out.rest(Rest(c.o.pos, c.o.heading, c.o.up, c.o.posture), 1.0)
        b.arms.withdraw = true
        b.arms.outArms = 0
        c.o.extension = max(0.05, c.o.extension - c.dt * 3)
        if (c.t - trigAt >= hold) finished = true
    }

    companion object {
        /** Light step that counts as "the light came on" (fraction of full) and the window it must happen in (s) [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "AIZ01 photoreception (O. wendtii, ANALOGY); trigger size [L]") const val RISE = 0.08
        const val RISE_WINDOW_S = 30.0
        /** Freeze with arms in, s, and the minimum time between reflexes [L]. */
        @Ev(Evidence.DESIGN_ESTIMATE, "invert_brittle_star S3 direction; durations [L]") const val HOLD_MIN_S = 3.0
        const val HOLD_MAX_S = 8.0
        const val REFRACTORY_S = 20.0

        /** Day arm-tip reach out of the hide: full in dim light, half under full light (AIZ01 direction, value [L]). */
        fun dayReach(light: Double): Double = 1.0 - 0.5 * light.coerceIn(0.0, 1.0)
    }
}
