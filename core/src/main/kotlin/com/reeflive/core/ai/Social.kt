package com.reeflive.core.ai

import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.Organism
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * What one individual remembers about another (SIMULATION §4.9). All values 0..1 except
 * [dominance] (−1 = it dominates me, +1 = I dominate it). Fear and grudge fade with half-lives of
 * hours; familiarity builds over days of shared space ("dear enemy": familiar neighbours are chased less).
 */
class Relation(val otherId: String) {
    var familiarity = 0.0
    var fear = 0.0
    /** Recent aggression I received from / gave to the other. */
    var harassedBy = 0.0
    var harassed = 0.0
    var dominance = 0.0
    var lastCleanedAt = -1e9
    var cleanings = 0
    var lastSeenAt = -1e9
}

/** Perception of others and relationship memory (research 05 §17; owner request: socially aware, non-repetitive). */
object Social {
    const val PERCEIVE_INTERVAL = 1.0          // s between full scans per animal
    const val FAMILIARITY_PER_HOUR = 0.02      // within 30 cm; saturates after ~2 days together
    const val FEAR_HALF_LIFE_S = 3 * 3600.0
    const val GRUDGE_HALF_LIFE_S = 6 * 3600.0

    fun rel(o: Organism, other: Organism): Relation = o.relations.getOrPut(other.id) { Relation(other.id) }

    /**
     * Full scan of every other living animal: identity, species, size, distance, state. Updates
     * familiarity and decays memories; sets [Organism.wary] to the individual it currently fears most.
     */
    fun perceive(c: Ctx, dt: Double) {
        val o = c.o
        val fearK = exp(-dt * 0.693 / FEAR_HALF_LIFE_S)
        val grudgeK = exp(-dt * 0.693 / GRUDGE_HALF_LIFE_S)
        var wary: Organism? = null
        var waryScore = 0.0
        for (n in c.sim.organisms) {
            if (n === o || !n.alive) continue
            val d = n.pos.distanceTo(o.pos)
            val r = o.relations[n.id]
            if (d < 30.0 && n.visible) {
                val rr = r ?: rel(o, n)
                rr.familiarity = min(1.0, rr.familiarity + FAMILIARITY_PER_HOUR * dt / 3600 * (1 - rr.familiarity))
                rr.lastSeenAt = c.t
            }
            if (r != null) {
                r.fear *= fearK; r.harassedBy *= grudgeK; r.harassed *= grudgeK
            }
            // Wariness toward a specific individual: remembered fear, plus a prior for a much larger aggressive fish.
            if (n.isFish && n.visible && d < 2.5 * c.L + 15) {
                val prior = if (n.lengthCm > 1.6 * o.lengthCm) 0.15 * n.aggression * (1 - (r?.familiarity ?: 0.0)) else 0.0
                val score = ((r?.fear ?: 0.0) + prior) * (1 - d / (2.5 * c.L + 15))
                if (score > waryScore) { waryScore = score; wary = n }
            }
        }
        o.wary = if (waryScore > 0.12) wary else null
        o.waryLevel = waryScore
    }

    /** Called whenever [attacker] chases/nips [victim] (any behavior). */
    fun recordAttack(attacker: Organism, victim: Organism, mag: Double) {
        val v = rel(victim, attacker)
        v.fear = min(1.0, v.fear + 0.25 * mag + 0.1)
        v.harassedBy = min(1.0, v.harassedBy + 0.3)
        v.dominance = max(-1.0, v.dominance - 0.15)
        val a = rel(attacker, victim)
        a.harassed = min(1.0, a.harassed + 0.3)
        a.dominance = min(1.0, a.dominance + 0.15)
    }

    fun recordCleaning(cleaner: Organism, client: Organism, t: Double) {
        val r = rel(client, cleaner)
        r.lastCleanedAt = t; r.cleanings++
        r.fear = max(0.0, r.fear - 0.2)
        r.familiarity = min(1.0, r.familiarity + 0.05)
        rel(cleaner, client).cleanings++
    }

    /**
     * Matrix drive modulated by memory: familiar neighbours are tolerated more ("dear enemy"),
     * an individual that recently provoked gets chased more, and a dominant one is not challenged.
     */
    fun chaseDrive(attacker: Organism, target: Organism, base: Double): Double {
        val r = attacker.relations[target.id] ?: return base
        if (r.dominance < -0.4) return 0.0
        return base * (1 - 0.5 * r.familiarity) * (1 + 0.6 * r.harassedBy + 0.3 * max(0.0, r.dominance))
    }
}

/**
 * Keep clear of the individual this animal currently fears (after being chased, or a much larger
 * aggressive fish). Moves to the far side of nearby cover; intensity follows the remembered fear.
 */
class KeepClear(val b: SpeciesBrain) : Behavior("keepclear", "Keeping clear") {
    private var spot: Vec3? = null
    override val targetVerb: String get() = "keeping clear of"
    override fun score(c: Ctx): Double {
        c.o.wary ?: return 0.0
        if (c.p.night || c.o.attached) return 0.0
        return (0.35 + 0.5 * c.o.waryLevel).coerceAtMost(0.75)
    }
    override fun enter(c: Ctx) { spot = null }
    override fun update(c: Ctx, out: SteerIntent) {
        val w = c.o.wary ?: run { finished = true; return }
        c.o.focusId = w.id
        if (spot == null || elapsed > 6) { spot = c.coverPoint(w.pos, 25.0); elapsed = 0.0 }
        out.seek(spot!!, c.o.cruiseSpeed * 1.1, arrive = 3.0, urgency = 0.5)
        out.hover = true
        out.faceDir = (w.pos - c.o.pos).normalized(Vec3.X)   // keeps an eye on it
        if (c.o.pos.distanceTo(w.pos) > 3 * c.L + 20) finished = true
    }
}
