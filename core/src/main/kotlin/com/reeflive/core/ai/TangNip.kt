package com.reeflive.core.ai

import com.reeflive.core.sim.Coral
import com.reeflive.core.sim.CoralKind

/**
 * The blue tang occasionally nips zoanthids and soft corals (research 03 §D, coverage C-3), a small nuisance
 * rather than a feast: a short session of 2-5 bites every half hour to hour and a half by day. Each bite makes
 * the polyp retract briefly through [Coral.disturb]; nothing is eaten. Hair-algae grazing is [Graze].
 */
class NipCorals(val b: TangBrain) : Behavior("nipcoral", "Nipping at the coral") {
    private var nextAt = Double.NaN
    private var target: Coral? = null
    private var bitesLeft = 0
    private var nextBite = 0.0

    override val targetVerb: String get() = "nipping"

    private fun pick(c: Ctx): Coral? {
        val cand = c.sim.corals.filter { weight(it) > 0 && it.tissue > 0.3 && it.extension > 0.15 }
        if (cand.isEmpty()) return null
        val total = cand.sumOf { weight(it) }
        var r = c.rng.nextDouble() * total
        for (k in cand) { r -= weight(k); if (r <= 0) return k }
        return cand.last()
    }

    override fun score(c: Ctx): Double {
        if (nextAt.isNaN()) nextAt = c.t + c.rng.range(300.0, 1200.0)
        if (c.t < nextAt || c.p.night || c.activity < 0.5) return 0.0
        return if (c.sim.corals.any { weight(it) > 0 && it.tissue > 0.3 }) 0.6 else 0.0
    }

    override fun enter(c: Ctx) {
        target = pick(c)
        if (target == null) { finished = true; nextAt = c.t + c.rng.range(1800.0, 5400.0); return }
        bitesLeft = 2 + c.rng.nextInt(4)
        nextBite = 0.0
    }

    override fun update(c: Ctx, out: SteerIntent) {
        val k = target
        if (k == null || bitesLeft <= 0 || elapsed > 90) { finished = true; return }
        val stand = c.safe(k.pos + k.normal * (0.35 * c.o.collisionLength + 1.8), 0.8)
        out.seek(stand, c.o.cruiseSpeed, arrive = 1.5)
        out.faceDir = (k.pos - stand).normalized(c.o.heading)
        out.hover = true
        if (c.o.pos.distanceTo(stand) < 3.0 && c.t > nextBite) {
            nextBite = c.t + c.rng.range(1.5, 3.5)
            bitesLeft--
            c.o.mouth = 1.0
            k.disturb = maxOf(k.disturb, DISTURB)
            b.coralNips++
        }
    }

    override fun exit(c: Ctx) { nextAt = c.t + c.rng.range(1800.0, 5400.0) }

    companion object {
        /** Retraction strength of one nip (a glass tap on a coral is 0.6, SIMULATION coral disturb). */
        const val DISTURB = 0.3
        fun weight(k: Coral): Double = when (k.kind) {
            CoralKind.ZOA -> 1.0
            CoralKind.CLOVE -> 0.6
            CoralKind.KENYA -> 0.5
            CoralKind.GSP -> 0.4
            else -> 0.0
        }
    }
}
